<#
.SYNOPSIS
    Redis / RabbitMQ 连通性自检（协议级握手，而非仅 TCP 端口探测）。

.DESCRIPTION
    对 dev 环境默认的远程共用服务（43.166.0.94，与 youmall 共用）执行三层检测：
      1. TCP 三次握手        —— 主机与端口是否可达
      2. 协议级握手          —— Redis 发 AUTH/PING 看是否回 +PONG；RabbitMQ 发 AMQP 协议头看是否回帧
      3. 结论汇总            —— 区分「主机不可达 / 端口未开放 / 认证失败 / 正常」

.EXAMPLE
    # 用 dev 环境默认配置检测
    pwsh -File .\scripts\check-services.ps1

.EXAMPLE
    # 检测本地 docker-compose 起的服务
    pwsh -File .\scripts\check-services.ps1 -RedisHost 127.0.0.1 -RedisPassword '' `
        -RabbitHost 127.0.0.1
#>
[CmdletBinding()]
param(
    # dev 环境：与 youmall 共用的远程服务（见 server/src/main/resources/application-dev.yml）
    [string]$RedisHost       = '43.166.0.94',
    [int]   $RedisPort       = 6379,
    [string]$RedisPassword   = '123456',

    [string]$RabbitHost      = '43.166.0.94',
    [int]   $RabbitPort      = 5672,
    [int]   $RabbitMgmtPort  = 15672,
    [string]$RabbitUser      = 'admin',
    [string]$RabbitPassword  = 'admin123',

    [int]   $TimeoutMs       = 5000
)

$ErrorActionPreference = 'Continue'

function Test-TcpPort {
    param([string]$TargetHost, [int]$Port, [int]$Timeout)
    $client = New-Object System.Net.Sockets.TcpClient
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $iar = $client.BeginConnect($TargetHost, $Port, $null, $null)
        $signalled = $iar.AsyncWaitHandle.WaitOne($Timeout, $false)
        if ($signalled -and $client.Connected) {
            $client.EndConnect($iar)
            return [pscustomobject]@{ Open = $true; Ms = $sw.ElapsedMilliseconds; Error = $null }
        }
        return [pscustomobject]@{ Open = $false; Ms = $sw.ElapsedMilliseconds; Error = 'timeout / SYN dropped' }
    } catch {
        return [pscustomobject]@{ Open = $false; Ms = $sw.ElapsedMilliseconds; Error = $_.Exception.Message }
    } finally {
        $client.Close()
    }
}

function Test-RedisService {
    param([string]$TargetHost, [int]$Port, [string]$Password, [int]$Timeout)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect($TargetHost, $Port, $null, $null)
        if (-not ($iar.AsyncWaitHandle.WaitOne($Timeout, $false) -and $client.Connected)) {
            return [pscustomobject]@{ Ok = $false; Detail = 'TCP 未连通，跳过协议握手' }
        }
        $client.EndConnect($iar)
        $stream = $client.GetStream()
        $stream.ReadTimeout = $Timeout
        $stream.WriteTimeout = $Timeout

        if ($Password) {
            $auth = "AUTH $Password`r`n"
            $bytes = [System.Text.Encoding]::ASCII.GetBytes($auth)
            $stream.Write($bytes, 0, $bytes.Length)
            Start-Sleep -Milliseconds 150
            $buf = New-Object byte[] 256
            $n = $stream.Read($buf, 0, $buf.Length)
            $authReply = [System.Text.Encoding]::ASCII.GetString($buf, 0, $n).Trim()
            if ($authReply -like '-*') {
                if ($authReply -match 'without any password configured') {
                    return [pscustomobject]@{ Ok = $false; Detail = '服务端未设置密码，而配置提供了密码 -> 请清空配置中的 password' }
                }
                return [pscustomobject]@{ Ok = $false; Detail = "认证失败: $authReply" }
            }
        }

        $ping = [System.Text.Encoding]::ASCII.GetBytes("PING`r`n")
        $stream.Write($ping, 0, $ping.Length)
        Start-Sleep -Milliseconds 150
        $buf2 = New-Object byte[] 256
        $n2 = $stream.Read($buf2, 0, $buf2.Length)
        $reply = [System.Text.Encoding]::ASCII.GetString($buf2, 0, $n2).Trim()

        if ($reply -eq '+PONG') {
            return [pscustomobject]@{ Ok = $true; Detail = 'PING -> +PONG（认证通过、服务正常）' }
        }
        return [pscustomobject]@{ Ok = $false; Detail = "异常响应: $reply" }
    } catch {
        return [pscustomobject]@{ Ok = $false; Detail = "协议握手异常: $($_.Exception.Message)" }
    } finally {
        $client.Close()
    }
}

function Read-Exactly {
    param($Stream, [int]$Count)
    $buf = New-Object byte[] $Count
    $got = 0
    while ($got -lt $Count) {
        $n = $Stream.Read($buf, $got, $Count - $got)
        if ($n -le 0) { throw "对端关闭连接（已读 $got/$Count 字节）" }
        $got += $n
    }
    return $buf
}

function Test-RabbitMqService {
    param([string]$TargetHost, [int]$Port, [int]$Timeout)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect($TargetHost, $Port, $null, $null)
        if (-not ($iar.AsyncWaitHandle.WaitOne($Timeout, $false) -and $client.Connected)) {
            return [pscustomobject]@{ Ok = $false; Detail = 'TCP 未连通，跳过协议握手' }
        }
        $client.EndConnect($iar)
        $stream = $client.GetStream()
        $stream.ReadTimeout = $Timeout

        # 发送 AMQP 0-9-1 协议头
        $header = [System.Text.Encoding]::ASCII.GetBytes('AMQP' + [char]0 + [char]0 + [char]9 + [char]1)
        $stream.Write($header, 0, $header.Length)

        # 读帧头（7 字节，长度字段为大端序）
        $hdr = Read-Exactly $stream 7
        $frameType = $hdr[0]
        $size = ($hdr[3] * 16777216) + ($hdr[4] * 65536) + ($hdr[5] * 256) + $hdr[6]

        # 若服务端不支持该版本，会回显协议头（首 4 字节为 AMQP）
        if ($frameType -eq 0x41) {
            $ver = "$($hdr[5]).$($hdr[6]).$($hdr[7])"
            return [pscustomobject]@{ Ok = $true; Detail = "Broker 存活，但其支持的版本为 AMQP $ver（非 0.9.1）" }
        }

        if ($frameType -ne 1) {
            return [pscustomobject]@{ Ok = $false; Detail = "非预期帧类型 $frameType" }
        }

        $payload = Read-Exactly $stream ([int]$size)
        $null = Read-Exactly $stream 1   # frame-end
        $classId  = ($payload[0] * 256) + $payload[1]
        $methodId = ($payload[2] * 256) + $payload[3]

        # Connection.Start(10,10) 是 Broker 正常的问候帧，说明 Broker 存活且协议正常
        if ($classId -eq 10 -and $methodId -eq 10) {
            $verMajor = $payload[4]; $verMinor = $payload[5]
            $tableLen = ($payload[6] * 16777216) + ($payload[7] * 65536) + ($payload[8] * 256) + $payload[9]
            $off = 10 + $tableLen
            $mechLen = ($payload[$off] * 16777216) + ($payload[$off+1] * 65536) + ($payload[$off+2] * 256) + $payload[$off+3]
            $mechs = [System.Text.Encoding]::UTF8.GetString($payload, $off + 4, $mechLen)
            return [pscustomobject]@{ Ok = $true; Detail = "Broker 存活，AMQP $verMajor.$verMinor，SASL: $mechs" }
        }

        return [pscustomobject]@{ Ok = $false; Detail = "非预期方法帧 class=$classId method=$methodId" }
    } catch {
        return [pscustomobject]@{ Ok = $false; Detail = "协议握手异常: $($_.Exception.Message)" }
    } finally {
        $client.Close()
    }
}

function Write-Result {
    param([string]$Name, [string]$Target, [bool]$Ok, [string]$Detail, [long]$Ms)
    $tag = if ($Ok) { '[ OK ]' } else { '[FAIL]' }
    $color = if ($Ok) { 'Green' } else { 'Red' }
    Write-Host ("{0} {1,-22} {2,-26} {3,6} ms  {4}" -f $tag, $Name, $Target, $Ms, $Detail) -ForegroundColor $color
}

$results = @()

Write-Host ''
Write-Host '===== Redis / RabbitMQ 连通性自检 =====' -ForegroundColor Cyan

# ---- Redis ----
$redisTarget = "${RedisHost}:${RedisPort}"
$tcp = Test-TcpPort -TargetHost $RedisHost -Port $RedisPort -Timeout $TimeoutMs
if (-not $tcp.Open) {
    Write-Result 'Redis  TCP' $redisTarget $false $tcp.Error $tcp.Ms
    $results += [pscustomobject]@{ Name = 'Redis'; Ok = $false }
} else {
    Write-Result 'Redis  TCP' $redisTarget $true 'TCP 握手成功' $tcp.Ms
    $proto = Test-RedisService -TargetHost $RedisHost -Port $RedisPort -Password $RedisPassword -Timeout $TimeoutMs
    Write-Result 'Redis  协议' $redisTarget $proto.Ok $proto.Detail 0
    $results += [pscustomobject]@{ Name = 'Redis'; Ok = $proto.Ok }
}

# ---- RabbitMQ AMQP ----
$amqpTarget = "${RabbitHost}:${RabbitPort}"
$tcp = Test-TcpPort -TargetHost $RabbitHost -Port $RabbitPort -Timeout $TimeoutMs
if (-not $tcp.Open) {
    Write-Result 'RabbitMQ TCP' $amqpTarget $false $tcp.Error $tcp.Ms
    $results += [pscustomobject]@{ Name = 'RabbitMQ'; Ok = $false }
} else {
    Write-Result 'RabbitMQ TCP' $amqpTarget $true 'TCP 握手成功' $tcp.Ms
    $proto = Test-RabbitMqService -TargetHost $RabbitHost -Port $RabbitPort -Timeout $TimeoutMs
    Write-Result 'RabbitMQ 协议' $amqpTarget $proto.Ok $proto.Detail 0
    $results += [pscustomobject]@{ Name = 'RabbitMQ'; Ok = $proto.Ok }
}

# ---- RabbitMQ 管理台（辅助定位：能开管理台说明 Broker 活着，只是 5672 被挡）----
$mgmtTarget = "${RabbitHost}:${RabbitMgmtPort}"
$tcp = Test-TcpPort -TargetHost $RabbitHost -Port $RabbitMgmtPort -Timeout $TimeoutMs
Write-Result 'RabbitMQ 管理台' $mgmtTarget $tcp.Open $(if ($tcp.Open) { 'Web 管理台可达' } else { $tcp.Error }) $tcp.Ms

# ---- 汇总 ----
Write-Host ''
$allOk = ($results | Where-Object { -not $_.Ok }).Count -eq 0
if ($allOk) {
    Write-Host '结论：Redis 与 RabbitMQ 均连通正常。' -ForegroundColor Green
    exit 0
} else {
    $failed = ($results | Where-Object { -not $_.Ok } | ForEach-Object { $_.Name }) -join ', '
    Write-Host "结论：以下服务不可用 -> $failed" -ForegroundColor Red
    Write-Host ''
    Write-Host '排查顺序建议：' -ForegroundColor Yellow
    Write-Host '  1. 若管理台端口(15672)也不通，多为「整台主机不可达」：查云主机是否关机、安全组是否放行来源 IP。'
    Write-Host '  2. 若仅 6379/5672 不通而 15672 通，多为「端口未开放或仅内网监听」：查 firewalld/iptables 与 listen 配置。'
    Write-Host '  3. 本机公网出口 IP 可能变化（多出口 NAT），安全组白名单易失效，建议改用固定 IP 或内网互通。'
    exit 1
}

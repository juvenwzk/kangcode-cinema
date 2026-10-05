# 完整 AMQP 0-9-1 握手 + PLAIN 认证，用于确认 RabbitMQ 凭据是否可用
param(
    [string]$Server = '43.166.0.94',
    [int]$Port = 5672,
    [string]$User = 'admin',
    [string]$Password = 'admin123',
    [string]$VHost = '/'
)

$ErrorActionPreference = 'Continue'

function Read-Exactly {
    param($Stream, [int]$Count)
    $buf = New-Object byte[] $Count
    $got = 0
    while ($got -lt $Count) {
        $n = $Stream.Read($buf, $got, $Count - $got)
        if ($n -le 0) { throw "连接被对端关闭（已读 $got/$Count 字节）" }
        $got += $n
    }
    return $buf
}

function New-Frame {
    param([byte]$Type, [int]$Channel, [byte[]]$Payload)
    # 注意：AMQP 帧头必须是大端序，不能用 BinaryWriter（它是小端序）
    $out = New-Object System.Collections.Generic.List[byte]
    $out.Add($Type)
    $out.Add([byte](($Channel -shr 8) -band 0xFF))
    $out.Add([byte]($Channel -band 0xFF))
    $len = $Payload.Length
    $out.Add([byte](($len -shr 24) -band 0xFF))
    $out.Add([byte](($len -shr 16) -band 0xFF))
    $out.Add([byte](($len -shr 8) -band 0xFF))
    $out.Add([byte]($len -band 0xFF))
    foreach ($x in $Payload) { $out.Add($x) }
    $out.Add([byte]0xCE)
    return $out.ToArray()
}

# 大端写入辅助
function Add-Short { param($List, [int]$V) $List.Add([byte](($V -shr 8) -band 0xFF)); $List.Add([byte]($V -band 0xFF)) }
function Add-Long  { param($List, [long]$V) $List.Add([byte](($V -shr 24) -band 0xFF)); $List.Add([byte](($V -shr 16) -band 0xFF)); $List.Add([byte](($V -shr 8) -band 0xFF)); $List.Add([byte]($V -band 0xFF)) }
function Add-ShortStr { param($List, [string]$S)
    $b = [System.Text.Encoding]::UTF8.GetBytes($S)
    $List.Add([byte]$b.Length)
    foreach ($x in $b) { $List.Add($x) }
}
function Add-LongStr { param($List, [byte[]]$B)
    Add-Long $List $B.Length
    foreach ($x in $B) { $List.Add($x) }
}

Write-Host "===== AMQP 握手测试 -> ${Server}:${Port}  用户=$User  密码=$Password  vhost=$VHost ====="

$client = New-Object System.Net.Sockets.TcpClient
try {
    $client.Connect($Server, $Port)
    $stream = $client.GetStream()
    $stream.ReadTimeout = 8000
    Write-Host '  [1] TCP 已连接'

    # 发送协议头 AMQP 0-9-1
    $protoHeader = [System.Text.Encoding]::ASCII.GetBytes('AMQP' + [char]0 + [char]0 + [char]9 + [char]1)
    $stream.Write($protoHeader, 0, $protoHeader.Length)
    Write-Host '  [2] 已发送协议头 AMQP 0-9-1'

    # 读 Connection.Start
    $hdr = Read-Exactly $stream 7
    $frameType = $hdr[0]
    $payloadSize = ($hdr[3] * 16777216) + ($hdr[4] * 65536) + ($hdr[5] * 256) + $hdr[6]
    $payload = Read-Exactly $stream ([int]$payloadSize)
    $null = Read-Exactly $stream 1   # frame-end
    $classId  = ($payload[0] * 256) + $payload[1]
    $methodId = ($payload[2] * 256) + $payload[3]
    Write-Host "  [3] 收到帧 type=$frameType class=$classId method=$methodId (57=Start)"
    if ($classId -ne 10 -or $methodId -ne 10) { throw "期望 Connection.Start(10,10)，实际 ($classId,$methodId)" }

    # 解析 Start 帧：version / server-properties / mechanisms / locales
    $verMajor = $payload[4]; $verMinor = $payload[5]
    $tableLen = ($payload[6] * 16777216) + ($payload[7] * 65536) + ($payload[8] * 256) + $payload[9]
    $off = 10 + $tableLen
    $mechLen = ($payload[$off] * 16777216) + ($payload[$off+1] * 65536) + ($payload[$off+2] * 256) + $payload[$off+3]
    $mechs = [System.Text.Encoding]::UTF8.GetString($payload, $off + 4, $mechLen)
    $off2 = $off + 4 + $mechLen
    $locLen = ($payload[$off2] * 16777216) + ($payload[$off2+1] * 65536) + ($payload[$off2+2] * 256) + $payload[$off2+3]
    $locales = [System.Text.Encoding]::UTF8.GetString($payload, $off2 + 4, $locLen)
    Write-Host "      AMQP 版本: $verMajor.$verMinor"
    Write-Host "      服务端宣告机制: $mechs"
    Write-Host "      服务端语言: $locales"

    # Connection.StartOk (10,11)
    $p = New-Object System.Collections.Generic.List[byte]
    Add-Short $p 10; Add-Short $p 11
    Add-Long $p 0                                  # client-properties: 空 table
    Add-ShortStr $p 'PLAIN'
    $resp = New-Object System.Collections.Generic.List[byte]
    $resp.Add([byte]0)
    foreach ($x in [System.Text.Encoding]::UTF8.GetBytes($User)) { $resp.Add($x) }
    $resp.Add([byte]0)
    foreach ($x in [System.Text.Encoding]::UTF8.GetBytes($Password)) { $resp.Add($x) }
    Add-LongStr $p $resp.ToArray()
    Add-ShortStr $p 'en_US'
    $startOk = New-Frame 1 0 $p.ToArray()
    Write-Host "  [4a] 发送 StartOk 帧 ($($startOk.Length) 字节)"
    Write-Host ('       HEX: ' + [BitConverter]::ToString($startOk))
    $stream.Write($startOk, 0, $startOk.Length)
    Write-Host '  [4] 已发送 StartOk (PLAIN 认证)'

    # 读 Connection.Tune 或 Connection.Close
    $hdr = Read-Exactly $stream 7
    $payloadSize = ($hdr[3] * 16777216) + ($hdr[4] * 65536) + ($hdr[5] * 256) + $hdr[6]
    $payload = Read-Exactly $stream ([int]$payloadSize)
    $null = Read-Exactly $stream 1
    $classId  = ($payload[0] * 256) + $payload[1]
    $methodId = ($payload[2] * 256) + $payload[3]

    if ($classId -eq 10 -and $methodId -eq 50) {
        # Connection.Close -> 认证/接入被拒
        $code = ($payload[4] * 256) + $payload[5]
        $textLen = $payload[6]
        $text = [System.Text.Encoding]::UTF8.GetString($payload, 7, $textLen)
        Write-Host ''
        Write-Host "  [X] 被服务端拒绝：reply-code=$code  reply-text=$text" -ForegroundColor Red
        if ($code -eq 403) { Write-Host '      -> 403 ACCESS_REFUSED：用户名/密码错误，或该用户不允许从远程登录（如默认 guest）' -ForegroundColor Yellow }
        if ($code -eq 530) { Write-Host '      -> 530 NOT_ALLOWED：vhost 不存在或无权限' -ForegroundColor Yellow }
    }
    elseif ($classId -eq 10 -and $methodId -eq 30) {
        Write-Host '  [5] 收到 Connection.Tune —— 认证通过！'
        $chanMax   = ($payload[4] * 256) + $payload[5]
        $frameMax  = ($payload[6] * 16777216) + ($payload[7] * 65536) + ($payload[8] * 256) + $payload[9]
        $heartbeat = ($payload[10] * 256) + $payload[11]
        Write-Host "      channel-max=$chanMax  frame-max=$frameMax  heartbeat=$heartbeat"

        # TuneOk (10,31)
        $p = New-Object System.Collections.Generic.List[byte]
        Add-Short $p 10; Add-Short $p 31
        Add-Short $p $chanMax
        Add-Long $p $frameMax
        Add-Short $p $heartbeat
        $f = New-Frame 1 0 $p.ToArray()
        $stream.Write($f, 0, $f.Length)

        # Open (10,40)
        $p = New-Object System.Collections.Generic.List[byte]
        Add-Short $p 10; Add-Short $p 40
        Add-ShortStr $p $VHost
        Add-ShortStr $p ''
        $p.Add([byte]0)
        $f = New-Frame 1 0 $p.ToArray()
        $stream.Write($f, 0, $f.Length)
        Write-Host "  [6] 已发送 TuneOk + Open(vhost=$VHost)"

        $hdr = Read-Exactly $stream 7
        $payloadSize = ($hdr[3] * 16777216) + ($hdr[4] * 65536) + ($hdr[5] * 256) + $hdr[6]
        $payload = Read-Exactly $stream ([int]$payloadSize)
        $null = Read-Exactly $stream 1
        $classId  = ($payload[0] * 256) + $payload[1]
        $methodId = ($payload[2] * 256) + $payload[3]
        if ($classId -eq 10 -and $methodId -eq 41) {
            Write-Host ''
            Write-Host '  [OK] Connection.OpenOk —— 完整握手成功，凭据可用、vhost 存在' -ForegroundColor Green
        } else {
            $code = if ($payload.Length -gt 6) { ($payload[4] * 256) + $payload[5] } else { 0 }
            $textLen = if ($payload.Length -gt 6) { $payload[6] } else { 0 }
            $text = if ($textLen -gt 0) { [System.Text.Encoding]::UTF8.GetString($payload, 7, $textLen) } else { '' }
            Write-Host "  [X] 打开 vhost 失败：class=$classId method=$methodId code=$code text=$text" -ForegroundColor Red
        }
    }
    else {
        Write-Host "  [?] 收到未预期的帧 class=$classId method=$methodId" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  [X] 握手失败: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ''
    Write-Host '      >> 若上面已打印 "[3] 收到帧 ... class=10 method=10"，说明 Broker 存活、协议正常，' -ForegroundColor Yellow
    Write-Host '         但 StartOk(认证) 之后被服务端断开 —— 即【用户名或密码错误】。' -ForegroundColor Yellow
    Write-Host '         （RabbitMQ 拒绝认证时会发 Connection.Close(403) 后立即 RST，客户端常表现为“连接被强制关闭”）' -ForegroundColor Yellow
    $script:authRejected = $true
} finally {
    $client.Close()
}

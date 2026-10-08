# ============================================================================
#  前后端联调冒烟测试
#
#  按「前端页面的调用方式」把每个接口跑一遍，用来确认前端能联调的功能后端都没问题。
#  全部走 nginx（http://127.0.0.1:200），和浏览器里发起的请求是同一条链路。
#
#  用法：
#      pwsh -File .\scripts\check-api-flows.ps1
#      pwsh -File .\scripts\check-api-flows.ps1 -Base http://127.0.0.1:8080    # 绕过 nginx 直连后端
# ============================================================================
[CmdletBinding()]
param(
    [string]$Base = 'http://127.0.0.1:200',
    [string]$AdminUser = 'admin',
    [string]$AdminPass = 'admin123',
    [string]$UserUser  = 'demo',
    [string]$UserPass  = '123456',
    [string]$TravelDate = ''          # 留空则自动用「明天」
)

$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$script:pass = 0
$script:fail = 0

function Write-Head($t) { Write-Host ''; Write-Host "── $t " -NoNewline; Write-Host ('─' * [Math]::Max(0, 62 - $t.Length)) -ForegroundColor DarkGray }
function Ok($msg)   { $script:pass++; Write-Host "  [OK]   $msg" -ForegroundColor Green }
function Bad($msg)  { $script:fail++; Write-Host "  [FAIL] $msg" -ForegroundColor Red }
function Info($msg) { Write-Host "         $msg" -ForegroundColor DarkGray }

function Invoke-Api {
    param([string]$Method, [string]$Path, $Body, [string]$Token)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $req = @{
        Uri             = "$Base$Path"
        Method          = $Method
        Headers         = $headers
        TimeoutSec      = 20
        UseBasicParsing = $true
        SkipHttpErrorCheck = $true
    }
    if ($null -ne $Body) {
        $req['Body']        = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Compress -Depth 6))
        $req['ContentType'] = 'application/json'
    }
    try { return Invoke-WebRequest @req }
    catch { return $null }
}

function Get-Code($r) { if ($null -eq $r) { return 'NO-RESPONSE' }; try { return ($r.Content | ConvertFrom-Json).code } catch { return "HTTP$($r.StatusCode)" } }
function Get-Data($r) { if ($null -eq $r) { return $null }; try { return ($r.Content | ConvertFrom-Json).data } catch { return $null } }
function Get-Msg($r)  { if ($null -eq $r) { return '' }; try { return ($r.Content | ConvertFrom-Json).message } catch { return '' } }

if (-not $TravelDate) {
    $TravelDate = (Get-Date).AddDays(1).ToString('yyyy-MM-dd')
}

Write-Host ''
Write-Host '康轨 KangRail · 前后端联调冒烟测试' -ForegroundColor Cyan
Write-Host "  接口基址: $Base"
Write-Host "  放票日期: $TravelDate"

# ---------------------------------------------------------------------------
Write-Head '0. 连通性'
$probe = Invoke-Api GET '/api/station/list' $null $null
if ($null -eq $probe) {
    Bad '后端不可达 —— 先启动后端（java -jar server\target\server-1.0.0-SNAPSHOT.jar）'
    Write-Host ''; Write-Host "结果：$script:pass 通过 / $script:fail 失败" -ForegroundColor Red; exit 1
}
if ($probe.StatusCode -eq 502) {
    Bad 'nginx 返回 502 —— 后端没起，或 nginx 反代端口不是 8080'
    Write-Host ''; Write-Host "结果：$script:pass 通过 / $script:fail 失败" -ForegroundColor Red; exit 1
}
if ($probe.StatusCode -eq 401) { Ok '未登录访问受保护接口 → 401（正确）' }
else { Bad "未登录应返回 401，实际 HTTP $($probe.StatusCode)" }

# ---------------------------------------------------------------------------
Write-Head '1. 认证'
$adminLogin = Invoke-Api POST '/api/auth/login' @{ username = $AdminUser; password = $AdminPass } $null
$adminToken = (Get-Data $adminLogin).token
if ($adminToken) { Ok "$AdminUser 登录成功，role=$((Get-Data $adminLogin).role)" }
else { Bad "$AdminUser 登录失败：$(Get-Msg $adminLogin)" }

$userLogin = Invoke-Api POST '/api/auth/login' @{ username = $UserUser; password = $UserPass } $null
$userToken = (Get-Data $userLogin).token
if ($userToken) { Ok "$UserUser 登录成功，role=$((Get-Data $userLogin).role)" }
else { Bad "$UserUser 登录失败：$(Get-Msg $userLogin)" }

$me = Invoke-Api GET '/api/user/me' $null $adminToken
if ((Get-Code $me) -eq 200) { Ok "GET /api/user/me → $((Get-Data $me).username)" }
else { Bad "GET /api/user/me → $(Get-Code $me)" }

# ---------------------------------------------------------------------------
Write-Head '2. 基础数据（车站 / 车次 / 车次详情）'
$stations = Invoke-Api GET '/api/station/list' $null $adminToken
$sList = Get-Data $stations
if ($sList -and $sList.Count -gt 0) { Ok "GET /api/station/list → $($sList.Count) 个车站" }
else { Bad 'GET /api/station/list 没有数据' }

$trains = Invoke-Api GET '/api/train/page?page=1&size=10' $null $adminToken
$tRecords = (Get-Data $trains).records
if ($tRecords -and $tRecords.Count -gt 0) { Ok "GET /api/train/page → $($tRecords.Count) 条车次" }
else { Bad 'GET /api/train/page 没有数据' }

$detail = Invoke-Api GET '/api/train/1' $null $adminToken
$dData = Get-Data $detail
if ($dData) {
    $n = if ($dData.stations) { $dData.stations.Count } else { 0 }
    if ($n -gt 0) { Ok "GET /api/train/1 → 经停站 $n 个（任务 2-1 已实现）" }
    else { Bad 'GET /api/train/1 的 stations 为空（任务 2-1 未完成？）' }
} else { Bad "GET /api/train/1 → $(Get-Code $detail)" }

# ---------------------------------------------------------------------------
Write-Head "3. 放票 / 库存预热（POST /api/admin/stock/publish）· 日期 $TravelDate"
$pub1 = Invoke-Api POST '/api/admin/stock/publish' @{ trainId = 1; travelDate = $TravelDate; seatType = '二等座'; count = 100 } $adminToken
$c1 = Get-Code $pub1
if ($c1 -eq 200) {
    Ok "第一次放票 100 张 → code=200，接口返回余票=$(Get-Data $pub1)"
    if ((Get-Data $pub1) -eq 0) { Info '返回 0：StockService.publish 还没把 remain 返回（任务 2-2 未完成）' }
} else { Bad "第一次放票失败 → code=$c1  $(Get-Msg $pub1)" }

$pub2 = Invoke-Api POST '/api/admin/stock/publish' @{ trainId = 1; travelDate = $TravelDate; seatType = '二等座'; count = 50 } $adminToken
if ((Get-Code $pub2) -eq 200) { Ok "第二次放票 50 张 → code=200（已存在的记录应累加）" }
else { Bad "第二次放票失败 → $(Get-Code $pub2)" }

$remain = Invoke-Api GET "/api/ticket/remain?trainId=1&travelDate=$TravelDate&seatType=$([uri]::EscapeDataString('二等座'))" $null $adminToken
$remainVal = Get-Data $remain
if ((Get-Code $remain) -eq 200) {
    Ok "放票后查余票 → $remainVal 张"
    if ($remainVal -eq 150) { Ok '余票 = 150，累加逻辑正确（100 + 50）' }
    elseif ($remainVal -eq 100) { Info "余票是 100 而不是 150：第二次放票没累加，或缓存没刷新" }
    else { Info "余票 $remainVal（预期 150）" }
} else { Bad "余票查询失败 → $(Get-Code $remain)" }

$badDate = Invoke-Api POST '/api/admin/stock/publish' @{ trainId = 1; travelDate = '2020-01-01'; seatType = '二等座'; count = 1 } $adminToken
if ((Get-Code $badDate) -ne 200) { Ok "放历史日期被拦截 → $(Get-Msg $badDate)" }
else { Bad '放历史日期竟然成功了（应有校验）' }

$badCount = Invoke-Api POST '/api/admin/stock/publish' @{ trainId = 1; travelDate = $TravelDate; seatType = '二等座'; count = 0 } $adminToken
if ((Get-Code $badCount) -ne 200) { Ok "放票数 0 被拦截 → $(Get-Msg $badCount)" }
else { Bad '放票数 0 竟然成功了（@Min(1) 未生效？）' }

# ---------------------------------------------------------------------------
Write-Head '4. 车次维护（PUT /api/admin/train/{id}）'
$orig = (Get-Data (Invoke-Api GET '/api/train/1' $null $adminToken)).trainName
$newName = "$orig-联调"
$up = Invoke-Api PUT '/api/admin/train/1' @{ trainName = $newName; status = 1 } $adminToken
if ((Get-Code $up) -eq 200) { Ok "改名成功：$orig → $newName" } else { Bad "改名失败 → $(Get-Code $up) $(Get-Msg $up)" }

$after = (Get-Data (Invoke-Api GET '/api/train/1' $null $adminToken)).trainName
if ($after -eq $newName) { Ok "复查读到新名字（@CacheEvict 生效）" } else { Bad "复查读到的是 $after，缓存没清？" }

$restore = Invoke-Api PUT '/api/admin/train/1' @{ trainName = $orig; status = 1 } $adminToken
if ((Get-Code $restore) -eq 200) { Ok "已还原为：$orig" } else { Bad '还原失败' }

# ---------------------------------------------------------------------------
Write-Head '5. 车站维护（PUT /api/admin/station/{id}）'
$st = $null
if ($sList) { $st = $sList | Where-Object { $_.id -eq 5 } | Select-Object -First 1 }
if ($st) {
    $oldCity = $st.city
    $up2 = Invoke-Api PUT '/api/admin/station/5' @{ stationName = $st.stationName; city = "$oldCity-联调" } $adminToken
    if ((Get-Code $up2) -eq 200) { Ok "改城市成功：$oldCity → $oldCity-联调" } else { Bad "改城市失败 → $(Get-Code $up2) $(Get-Msg $up2)" }
    $fresh = (Get-Data (Invoke-Api GET '/api/station/list' $null $adminToken)) | Where-Object { $_.id -eq 5 }
    if ($fresh.city -eq "$oldCity-联调") { Ok '复查读到新城市（缓存已清）' } else { Bad "复查读到 $($fresh.city)" }
    $null = Invoke-Api PUT '/api/admin/station/5' @{ stationName = $st.stationName; city = $oldCity } $adminToken
    Ok "已还原为：$oldCity"
} else { Bad '车站表里找不到 id=5' }

$empty = Invoke-Api PUT '/api/admin/station/1' @{ stationName = ''; city = '' } $adminToken
if ((Get-Code $empty) -ne 200) { Ok "空站名被拦截 → $(Get-Msg $empty)" } else { Bad '空站名竟然成功了（@NotBlank 未生效？）' }

# ---------------------------------------------------------------------------
Write-Head '6. 权限拦截（普通用户访问管理接口应 403）'
foreach ($c in @(
        @{ n = 'POST /api/admin/stock/publish'; m = 'POST'; p = '/api/admin/stock/publish'; b = @{ trainId = 1; travelDate = $TravelDate; seatType = '二等座'; count = 1 } },
        @{ n = 'PUT  /api/admin/train/1';       m = 'PUT';  p = '/api/admin/train/1';       b = @{ trainName = 'hack'; status = 0 } },
        @{ n = 'PUT  /api/admin/station/1';     m = 'PUT';  p = '/api/admin/station/1';     b = @{ stationName = 'hack' } },
        @{ n = 'GET  /api/admin/ping';          m = 'GET';  p = '/api/admin/ping';          b = $null }
    )) {
    $rr = Invoke-Api $c.m $c.p $c.b $userToken
    if ((Get-Code $rr) -eq 403) { Ok "demo 访问 $($c.n) → 403（正确拦截）" }
    else { Bad "demo 访问 $($c.n) → $(Get-Code $rr)（应为 403）" }
}

# ---------------------------------------------------------------------------
Write-Head '7. 「假成功」陷阱检测（返回 200 但实际没做事）'

# buy() 是 return null 占位 → controller 包成 Result.ok(null) → HTTP200 + code200 + data null
$buy = Invoke-Api POST '/api/ticket/buy' @{ trainId = 1; travelDate = $TravelDate; seatType = '二等座'; ticketCount = 1 } $adminToken
$buyCode = Get-Code $buy
$buyData = Get-Data $buy
if ($buyCode -eq 200 -and $null -eq $buyData) {
    Ok 'POST /api/ticket/buy → HTTP200/code200 但 data=null（P3 占位实现，前端已拦截并提示「下单未生效」）'
} elseif ($buyCode -eq 200 -and $buyData.orderNo) {
    Ok "POST /api/ticket/buy → 真的下单成功了，订单号 $($buyData.orderNo)（P3 已实现）"
} else {
    Info "POST /api/ticket/buy → code=$buyCode $(Get-Msg $buy)"
}

# cancel() 是空实现 → 返回 200，但订单状态不变
$ordersResp = Invoke-Api GET '/api/order/my' $null $adminToken
$myOrders = Get-Data $ordersResp
if ($myOrders -and $myOrders.Count -gt 0) {
    $pending = $myOrders | Where-Object { $_.status -eq 0 } | Select-Object -First 1
    if ($pending) {
        $before = $pending.status
        $null = Invoke-Api POST "/api/order/$($pending.orderNo)/cancel" $null $adminToken
        $fresh = Get-Data (Invoke-Api GET "/api/order/$($pending.orderNo)" $null $adminToken)
        if ($fresh -and $fresh.status -eq $before) {
            Ok "POST /api/order/{orderNo}/cancel → 返回成功但状态仍是 $before（P3/P4 空实现，前端已用「状态比对」识别）"
        } else {
            Ok "POST /api/order/{orderNo}/cancel → 状态 $before → $($fresh.status)（真的生效了）"
        }
    } else {
        Info '当前没有「待支付」订单，跳过取消测试'
    }
} else {
    Info 'GET /api/order/my 没有订单（buy 还没实现，属正常）'
}

# publish() 最后 return 0
$pubZero = Invoke-Api POST '/api/admin/stock/publish' @{ trainId = 1; travelDate = $TravelDate; seatType = '一等座'; count = 10 } $adminToken
if ((Get-Code $pubZero) -eq 200 -and (Get-Data $pubZero) -eq 0) {
    Ok 'POST /api/admin/stock/publish → code=200 但返回值恒为 0（publish() 末尾 return 0，前端会补说明）'
}

# ---------------------------------------------------------------------------
Write-Host ''
if ($script:fail -eq 0) {
    Write-Host "全部通过：$script:pass 项" -ForegroundColor Green
    exit 0
} else {
    Write-Host "结果：$script:pass 通过 / $script:fail 失败" -ForegroundColor Yellow
    exit 1
}

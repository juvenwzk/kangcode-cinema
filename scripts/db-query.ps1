<#
.SYNOPSIS
    快速查询本地 MySQL 的 railway 库（自动定位本地 m2 里的驱动，无需手写 classpath）。

.DESCRIPTION
    内部使用 scripts/javatest/SqlQuery.java（首次运行会自动编译）。
    只做只读查询，用来快速看数据，不用装 MySQL 客户端。

.EXAMPLE
    # 看有哪些表
    pwsh -File .\scripts\db-query.ps1 "SHOW TABLES FROM railway"

.EXAMPLE
    # 看订单表
    pwsh -File .\scripts\db-query.ps1 "SELECT order_no, user_id, status, create_time FROM train_order ORDER BY id DESC LIMIT 10"

.EXAMPLE
    # 看用户和角色
    pwsh -File .\scripts\db-query.ps1 "SELECT u.id, u.username, r.role_code FROM sys_user u LEFT JOIN sys_user_role ur ON ur.user_id=u.id LEFT JOIN sys_role r ON r.id=ur.role_id"

.EXAMPLE
    # 连别的库/账号
    pwsh -File .\scripts\db-query.ps1 "SELECT 1" -Database railway -User root -Password 123456
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Sql,

    [string]$DbHost = '127.0.0.1',
    [int]   $Port = 3306,
    [string]$Database = 'railway',
    [string]$User = 'root',
    [string]$Password = '123456'
)

$ErrorActionPreference = 'Stop'

# ---- 1. 定位 MySQL 驱动（本地 m2）----
$m2 = Join-Path $env:USERPROFILE '.m2\repository'
$driver = Get-ChildItem (Join-Path $m2 'com\mysql\mysql-connector-j') -Recurse -Filter 'mysql-connector-j-*.jar' -ErrorAction SilentlyContinue |
          Where-Object { $_.Name -notlike '*sources*' -and $_.Name -notlike '*javadoc*' } |
          Sort-Object Name -Descending | Select-Object -First 1
if (-not $driver) {
    # 退回到旧坐标
    $driver = Get-ChildItem (Join-Path $m2 'mysql\mysql-connector-java') -Recurse -Filter 'mysql-connector-java-*.jar' -ErrorAction SilentlyContinue |
              Where-Object { $_.Name -notlike '*sources*' -and $_.Name -notlike '*javadoc*' } |
              Sort-Object Name -Descending | Select-Object -First 1
}
if (-not $driver) {
    Write-Host '找不到 MySQL 驱动 jar，请确认本地 m2 仓库存在（或先执行一次 mvn package）。' -ForegroundColor Red
    exit 1
}

# ---- 2. 定位 javac / java ----
$javac = Get-Command javac -ErrorAction SilentlyContinue
$java  = Get-Command java  -ErrorAction SilentlyContinue
if (-not $javac) { Write-Host '找不到 javac（需要 JDK，不是 JRE）。' -ForegroundColor Red; exit 1 }
if (-not $java)  { Write-Host '找不到 java。' -ForegroundColor Red; exit 1 }

# ---- 3. 必要时编译 SqlQuery ----
$work = Join-Path $PSScriptRoot 'javatest'
$src  = Join-Path $work 'SqlQuery.java'
$cls  = Join-Path $work 'SqlQuery.class'
if (-not (Test-Path $src)) { Write-Host "找不到 $src" -ForegroundColor Red; exit 1 }
if (-not (Test-Path $cls) -or (Get-Item $src).LastWriteTime -gt (Get-Item $cls).LastWriteTime) {
    Write-Host '首次运行，正在编译 SqlQuery...' -ForegroundColor DarkGray
    & $javac.Source --release 8 -nowarn -cp $driver.FullName -d $work $src 2>&1 |
        Where-Object { $_ -notmatch '过时|deprecat|警告|warning|^\d+ 个警告' } | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Host '编译失败' -ForegroundColor Red; exit 1 }
}

# ---- 4. 执行查询 ----
$url = "jdbc:mysql://${DbHost}:${Port}/${Database}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
Write-Host "SQL: $Sql" -ForegroundColor Cyan
Write-Host ''

# JVM 与控制台都用 UTF-8，避免中文表名/数据乱码
$prevEnc = [Console]::OutputEncoding
try {
    [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
    & $java.Source '-Dfile.encoding=UTF-8' -cp "$work;$($driver.FullName)" SqlQuery $url $User $Password $Sql
    $code = $LASTEXITCODE
} finally {
    [Console]::OutputEncoding = $prevEnc
}
exit $code

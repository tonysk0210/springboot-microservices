# =============================================================================
#  手動模擬 GitHub webhook，測試設定刷新：/monitor → Config Server → Bus → 受影響服務。
#
#  設定檔需先 commit、push 到 GitHub；Config Server 與 RabbitMQ 必須啟動。
#  本機測試不需 GitHub 或 tunnel，腳本會直接呼叫 Config Server 的 /monitor。
#  仍會經過相同的 payload 解析，只通知有變更的服務。
#
#  用法：
#  .\refresh-configyml.ps1                  自動判斷最後一次 commit
#  .\refresh-configyml.ps1 -Service account 只通知 Account
#  .\refresh-configyml.ps1 -All             通知全部服務
# =============================================================================

param(
    [ValidateSet("account", "loan", "card", "eurekaserver", "gatewayserver")]
    [string[]] $Service,

    # 不分析檔案，直接通知全部服務。
    [switch]   $All,

    [string]   $ConfigServer = "http://localhost:8071"
)

$ErrorActionPreference = "Stop"

Write-Host "=== 設定刷新（模擬 GitHub webhook → /monitor → Bus）===" -ForegroundColor Cyan
Write-Host ""

# ① 先確認 Config Server 可以連線。
$uri  = [Uri]$ConfigServer
$tcp  = [System.Net.Sockets.TcpClient]::new()
$open = $false
try {
    $open = $tcp.ConnectAsync($uri.Host, $uri.Port).Wait(1500)
} catch { }
finally { $tcp.Dispose() }

if (-not $open) {
    Write-Host "✗ 連不上 configserver（$ConfigServer）" -ForegroundColor Red
    Write-Host ""
    Write-Host "  先把它啟動起來：" -ForegroundColor DarkGray
    Write-Host "    IntelliJ 跑 ConfigserverApplication，或" -ForegroundColor DarkGray
    Write-Host "    docker compose up -d configserver" -ForegroundColor DarkGray
    Write-Host ""
    Write-Host "  ⚠ 還要 rabbitmq 在跑，否則 /monitor 收得到但廣播不出去。" -ForegroundColor DarkGray
    exit 1
}

# ② 決定要通知哪些服務。
if ($All) {
    # application.yml 代表全部服務共用的設定。
    $paths = @("configyml/application.yml")
    Write-Host "模式 : 全部服務（不分析檔案）" -ForegroundColor Yellow
}
elseif ($Service) {
    $paths = $Service | ForEach-Object { "configyml/$_.yml" }
    Write-Host "模式 : 指定服務"
}
else {
    # 未指定服務時，讀取最後一次 commit 修改的設定檔。
    Push-Location $PSScriptRoot
    try {
        $changed = git diff-tree --no-commit-id --name-only -r HEAD -- configyml/ 2>$null
    } finally {
        Pop-Location
    }

    if (-not $changed) {
        Write-Host "✗ 最後一次 commit 沒有動到 configyml/ 底下的檔案" -ForegroundColor Red
        Write-Host ""
        Write-Host "  改用明確指定：" -ForegroundColor DarkGray
        Write-Host "    .\refresh-configyml.ps1 -Service account" -ForegroundColor DarkGray
        Write-Host "    .\refresh-configyml.ps1 -All" -ForegroundColor DarkGray
        exit 1
    }

    $paths = @($changed)
    Write-Host "模式 : 從最後一次 commit 自動判斷" -ForegroundColor Green
}

Write-Host "檔案 : $($paths -join ', ')"
Write-Host ""

# ③ 傳送模擬 GitHub push webhook 的通知。
#    payload 和標頭需符合 Config Server /monitor 的格式。
$body = @{ commits = @(@{ modified = $paths }) } | ConvertTo-Json -Depth 5 -Compress

try {
    $notified = Invoke-RestMethod "$ConfigServer/monitor" -Method Post `
        -Headers @{ "X-Github-Event" = "push" } `
        -ContentType "application/json" `
        -Body $body
} catch {
    Write-Host "✗ /monitor 回錯：$($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    Write-Host "  404 → configserver 少了 spring-cloud-config-monitor 依賴" -ForegroundColor DarkGray
    Write-Host "  500 → 多半是 RabbitMQ 沒開，Bus 廣播失敗" -ForegroundColor DarkGray
    exit 1
}

# ④ 顯示通知結果。
if ($notified) {
    Write-Host "✓ 已廣播 refresh 給：" -ForegroundColor Green -NoNewline
    Write-Host " $($notified -join ', ')" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "去對應服務的 console 找這一行確認："  -ForegroundColor DarkGray
    Write-Host "    Keys refreshed [...]"              -ForegroundColor DarkGray
    Write-Host ""
    Write-Host "⚠ console 沒動靜的兩個原因：" -ForegroundColor DarkGray
    Write-Host "    1. 那個服務根本沒啟動" -ForegroundColor DarkGray
    Write-Host "    2. 改的值沒 push 到 GitHub —— 重抓到的還是舊的，Keys refreshed 會是空的 []" -ForegroundColor DarkGray
} else {
    # 空陣列表示檔案路徑沒有對應到服務名稱。
    Write-Host "⚠ /monitor 收到了，但沒對應到任何服務" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  它是把檔名當服務名（configyml/account.yml → account），" -ForegroundColor DarkGray
    Write-Host "  檔名跟 spring.application.name 對不上就會是這個結果。" -ForegroundColor DarkGray
}
Write-Host ""

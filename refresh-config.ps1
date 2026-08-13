# =============================================================================
#  手動觸發設定刷新 —— 模擬 GitHub webhook
#
#  正常的鏈路是這樣：
#      push configyml/*.yml
#          ↓ GitHub 發 webhook
#      POST localhost:8071/monitor        ← configserver（spring-cloud-config-monitor）
#          ↓ 解析 payload：哪些檔案變了 → 哪些服務要 refresh
#      RabbitMQ（Spring Cloud Bus 廣播）
#          ↓
#      account / loan / card 各自重抓設定
#
#  ⚠ GitHub 打不到你家的 localhost，中間那一跳需要 tunnel（hookdeck / ngrok）。
#    但這台機器的「智慧型應用程式控制」會擋掉未簽章的 tunnel 執行檔
#    （事件記錄 CodeIntegrity 3033），所以改由這支腳本直接戳 /monitor。
#
#  🔑 走的是「完全同一條路徑」—— 一樣經過 payload 解析、一樣只通知有變的服務。
#     差別只在「誰按下觸發鍵」。
#
#  用法（在專案根目錄）：
#      .\refresh-config.ps1                     從最後一次 commit 自動判斷
#      .\refresh-config.ps1 -Service account
#      .\refresh-config.ps1 -Service account,loan
#      .\refresh-config.ps1 -All                不分析檔案，通知全部服務
#
#  ⚠ configserver 讀的是「GitHub 上的內容」，不是你磁碟上的檔案 ——
#    改完 configyml/*.yml 一定要先 commit + push，否則刷新後拿到的還是舊值。
# =============================================================================

param(
    [ValidateSet("account", "loan", "card", "eurekaserver", "gatewayserver")]
    [string[]] $Service,

    # 不做檔案分析，直接通知全部（等同 POST /actuator/busrefresh）
    [switch]   $All,

    [string]   $ConfigServer = "http://localhost:8071"
)

$ErrorActionPreference = "Stop"

Write-Host "=== 設定刷新（模擬 GitHub webhook → /monitor → Bus）===" -ForegroundColor Cyan
Write-Host ""

# ── ① 先確認 configserver 在不在 ─────────────────────────────────────────────
#    不先擋的話只會拿到「目標電腦拒絕連線」，看不出是哪個服務沒開。
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

# ── ② 決定要通知誰 ───────────────────────────────────────────────────────────
if ($All) {
    # application.yml 是 Config Server 的萬用檔名 —— 對到「所有服務」。
    $paths = @("configyml/application.yml")
    Write-Host "模式 : 全部服務（不分析檔案）" -ForegroundColor Yellow
}
elseif ($Service) {
    $paths = $Service | ForEach-Object { "configyml/$_.yml" }
    Write-Host "模式 : 指定服務"
}
else {
    # 沒指定就從最後一次 commit 撈 —— 這正是 GitHub webhook payload 的內容。
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
        Write-Host "    .\refresh-config.ps1 -Service account" -ForegroundColor DarkGray
        Write-Host "    .\refresh-config.ps1 -All" -ForegroundColor DarkGray
        exit 1
    }

    $paths = @($changed)
    Write-Host "模式 : 從最後一次 commit 自動判斷" -ForegroundColor Green
}

Write-Host "檔案 : $($paths -join ', ')"
Write-Host ""

# ── ③ 送出 ──────────────────────────────────────────────────────────────────
#    payload 的形狀要跟 GitHub 一致 —— configserver 是用
#    GithubPropertyPathNotificationExtractor 去讀 commits[].modified，
#    而它靠 X-Github-Event 這個標頭認出「這是 GitHub 來的」。少了標頭會被忽略。
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

# ── ④ 結果 ──────────────────────────────────────────────────────────────────
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
    # /monitor 回空陣列 = payload 解析出來的路徑對不到任何服務名。
    Write-Host "⚠ /monitor 收到了，但沒對應到任何服務" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  它是把檔名當服務名（configyml/account.yml → account），" -ForegroundColor DarkGray
    Write-Host "  檔名跟 spring.application.name 對不上就會是這個結果。" -ForegroundColor DarkGray
}
Write-Host ""

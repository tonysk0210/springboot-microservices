# =============================================================================
#  限流測試 —— 連續打同一支 API，看 Gateway 的 RequestRateLimiter 從第幾次開始擋。
#
#  用法（在專案根目錄）：
#      .\test-rate-limit.ps1                          預設 alice 打 10 次
#      .\test-rate-limit.ps1 -User bob -Count 20      換人、換次數
#      .\test-rate-limit.ps1 -User ""                 不帶 user 標頭（走 anonymous 那個桶）
#      .\test-rate-limit.ps1 -Url "http://localhost:8072/bank/account/api/fetch-account?mobileNumber=1234567890"
#
#  預期輸出：
#      200 429 429 429 ...        ← replenishRate: 1 的話只有第一次會過
#
#  ⚠ 前置條件：redis 要跑著（docker compose up -d redis），
#    否則第一個請求就會是 500 / Connection refused，不是 429。
# =============================================================================

param(
    [string] $User  = "alice",
    [int]    $Count = 10,
    # ⚠ 預設打 card —— 限流只掛在 card 那條路由上（RouteConfig）。
    #   打 loan 或 account 會全部 200，因為那兩條沒有 requestRateLimiter。
    [string] $Url   = "http://localhost:8072/bank/card/api/contact-info"
)

# ⚠ 一定要用 curl.exe，不能只寫 curl ——
#   PowerShell 的 curl 是 Invoke-WebRequest 的別名，參數完全不同會直接報錯。
$curl = "curl.exe"

Write-Host "目標   : $Url"
Write-Host "使用者 : $(if ($User) { $User } else { '(不帶 user 標頭)' })"
Write-Host "次數   : $Count"
Write-Host ""

$codes = @()

1..$Count | ForEach-Object {
    # -s 安靜模式、-o NUL 丟掉 body、-w 只印狀態碼
    $args = @("-s", "-o", "NUL", "-w", "%{http_code}", "--max-time", "10")
    if ($User) { $args += @("-H", "user: $User") }
    $args += $Url

    $code = & $curl @args
    $codes += $code

    # 200 綠色、429 黃色（被限流）、其他紅色
    $color = switch -Regex ($code) {
        '^2'   { "Green" }
        '^429' { "Yellow" }
        default { "Red" }
    }
    Write-Host -NoNewline "$code " -ForegroundColor $color
}

Write-Host "`n"

# ── 統計 ──────────────────────────────────────────────────────────────────
$codes | Group-Object | Sort-Object Name | ForEach-Object {
    $label = switch -Regex ($_.Name) {
        '^2'   { "成功" }
        '^429' { "被限流擋下" }
        '^503' { "服務找不到（Eureka 名冊上沒有）" }
        '^504' { "逾時" }
        '^000' { "連不上" }
        default { "其他" }
    }
    "{0,-6} {1,3} 次   {2}" -f $_.Name, $_.Count, $label
}

Write-Host ""
Write-Host "Redis 裡的額度桶（key 就是 KeyResolver 回傳的字串）：" -ForegroundColor DarkGray
docker compose exec -T redis redis-cli --scan --pattern "request_rate_limiter*" 2>$null

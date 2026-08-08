# =============================================================================
#  限流測試（二）：微服務這一層
#
#  測的是 AccountController 的 @RateLimiter(name = "testRateLimiter")：
#      固定時間窗（yaml 設每 5 秒放行 1 次）
#      不分人 —— 整個方法一份額度，帶什麼標頭都一樣
#      計數在「記憶體」，多台 account 的話每台各算各的
#
#  🔑 跟 Gateway 那支的差別：那支分人、這支不分人。
#     這支不管誰打，5 秒內第二個請求就會被擋。
#
#  用法（在專案根目錄）：
#      .\test-ratelimit-service.ps1              打 5 次
#      .\test-ratelimit-service.ps1 -Count 10
#
#  預期輸出：
#      500 429 429 429 429
#       ↑    ↑
#       │    └─ 被限流擋在門外，方法根本沒執行
#       └─ 額度還有，方法真的跑了（它故意 throw RuntimeException → GlobalExceptionHandler → 500）
#
#  ⚠ 直接打 8080，不要走 gateway —— account 那條路由掛的是斷路器，
#    走過去會多一層干擾，分不清是誰擋的。
# =============================================================================

param(
    [int]    $Count = 5,
    [string] $Url   = "http://localhost:8080/api/test-rate-limiter"
)

# ⚠ 一定要用 curl.exe（PowerShell 的 curl 是 Invoke-WebRequest 的別名）
$curl = "curl.exe"

Write-Host "=== 微服務限流（固定時間窗 + 不分人 + 記憶體）===" -ForegroundColor Cyan
Write-Host "目標 : $Url"
Write-Host "次數 : $Count"
Write-Host ""

$codes = @()

1..$Count | ForEach-Object {
    $code = & $curl "-s" "-o" "NUL" "-w" "%{http_code}" "--max-time" "10" $Url
    $codes += $code

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
        '^429' { "被限流擋下（方法沒執行，走 fallback）" }
        '^500' { "額度還有 → 方法執行了 → 它自己 throw 例外" }
        '^000' { "連不上（account 沒開？）" }
        default { "其他" }
    }
    "{0,-6} {1,3} 次   {2}" -f $_.Name, $_.Count, $label
}

Write-Host ""
Write-Host "🔑 500 和 429 的差別就是「有沒有進到方法裡」——" -ForegroundColor DarkGray
Write-Host "   這也是 fallback 該回 429 而不是 200 的理由：一眼分得出是被限流還是方法爛掉。" -ForegroundColor DarkGray
Write-Host ""
Write-Host "⏳ 等 5 秒後額度重置，再跑一次第一個又會是 500。" -ForegroundColor DarkGray

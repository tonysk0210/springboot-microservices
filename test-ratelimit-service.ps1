# =============================================================================
#  測試 Account 內部的 @RateLimiter。
#  用途：快速連續呼叫 API，觀察請求通過時回 200、超過額度時回 429。
#  限流設定每 5 秒放行 1 次，不分使用者，額度存在每個 instance 的記憶體。
#
#  用法：
#      .\test-ratelimit-service.ps1
#      .\test-ratelimit-service.ps1 -Count 10
#
#  直接呼叫 Account 的 8080，避免 Gateway 限流或斷路器干擾測試結果。
# =============================================================================

param(
    [int]    $Count = 5,
    [string] $Url   = "http://localhost:8080/api/test-rate-limiter"
)

# 使用 curl.exe，避免 PowerShell 的 curl 別名造成參數差異。
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

# 顯示各 HTTP 狀態碼的次數與意義。
$codes | Group-Object | Sort-Object Name | ForEach-Object {
    $label = switch -Regex ($_.Name) {
        '^2'   { "方法執行並成功回應" }
        '^429' { "被限流擋下（方法沒執行，走 fallback）" }
        '^000' { "連不上（account 沒開？）" }
        default { "其他" }
    }
    "{0,-6} {1,3} 次   {2}" -f $_.Name, $_.Count, $label
}

Write-Host ""
Write-Host "🔑 200 表示方法有執行；429 表示額度用完，直接走 fallback。" -ForegroundColor DarkGray
Write-Host ""
Write-Host "⏳ 等 5 秒後額度重置，再跑一次第一個又會是 200。" -ForegroundColor DarkGray

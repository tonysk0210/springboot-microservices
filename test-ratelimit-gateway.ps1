# 此腳本會自動連續呼叫 Gateway 的 Card API，驗證 Redis RateLimiter 是否正常限流。
# 觀察 200／429 回應，確認每個 user 使用獨立令牌桶；通常第一次回 200，短時間後續請求回 429。
# 用法：.\test-ratelimit-gateway.ps1 [-User bob] [-Count 5]
# 不帶 user 時使用共用的 anonymous 桶。
# 前置條件：Gateway（8072）與 Redis 必須正在執行。

param(
    [string] $User  = "alice",
    [int]    $Count = 10,
    # 限流只套用在 Card 路由；Loan 與 Account 路由使用其他容錯機制。
    [string] $Url   = "http://localhost:8072/bank/card/api/contact-info"
)

# 使用 curl.exe，避免 PowerShell 的 curl 別名造成參數不相容。
$curl = "curl.exe"

Write-Host "=== Gateway 限流（令牌桶 + 分 user + Redis）===" -ForegroundColor Cyan
Write-Host "目標   : $Url"
Write-Host "使用者 : $(if ($User) { $User } else { '(不帶 user 標頭 → anonymous)' })"
Write-Host "次數   : $Count"
Write-Host ""

$codes = @()

1..$Count | ForEach-Object {
    # 只輸出 HTTP 狀態碼，不顯示回應內容。
    $curlArgs = @("-s", "-o", "NUL", "-w", "%{http_code}", "--max-time", "10")
    if ($User) { $curlArgs += @("-H", "user: $User") }
    $curlArgs += $Url

    $code = & $curl @curlArgs
    $codes += $code

    $color = switch -Regex ($code) {
        '^2'   { "Green" }
        '^429' { "Yellow" }
        default { "Red" }
    }
    Write-Host -NoNewline "$code " -ForegroundColor $color
}

Write-Host "`n"

# 統計各 HTTP 狀態碼。
$codes | Group-Object | Sort-Object Name | ForEach-Object {
    $label = switch -Regex ($_.Name) {
        '^2'   { "成功" }
        '^429' { "被限流擋下" }
        '^503' { "服務找不到（Eureka 名冊上沒有）" }
        '^504' { "逾時" }
        '^000' { "連不上（gateway 沒開？）" }
        default { "其他" }
    }
    "{0,-6} {1,3} 次   {2}" -f $_.Name, $_.Count, $label
}

# 顯示 Redis 中的限流桶，確認計數確實寫入 Redis。
Write-Host ""
Write-Host "Redis 裡的額度桶（前綴是路由 id，後面是 KeyResolver 回傳的字串）：" -ForegroundColor DarkGray
docker compose exec -T redis redis-cli --scan --pattern "request_rate_limiter*" 2>$null

Write-Host ""
Write-Host "🔑 換個 -User 再跑一次，會看到新的桶、而且第一次還是 200 —— 那就是「分人」。" -ForegroundColor DarkGray

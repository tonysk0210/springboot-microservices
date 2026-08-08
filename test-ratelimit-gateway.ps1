# =============================================================================
#  限流測試（一）：Gateway 這一層
#
#  測的是 RouteConfig 裡 card 路由上的 .requestRateLimiter(...)：
#      令牌桶（RedisRateLimiter 1/1/1）+ KeyResolver 依 user 標頭分人
#      計數存在 Redis，多台 gateway 共用同一份
#
#  🔑 重點是「分人」—— alice 打爆了不影響 bob。
#
#  用法（在專案根目錄）：
#      .\test-ratelimit-gateway.ps1                     alice 打 10 次
#      .\test-ratelimit-gateway.ps1 -User bob -Count 5  換人、換次數
#      .\test-ratelimit-gateway.ps1 -User ""            不帶標頭 → 走 anonymous 那個共用桶
#
#  預期輸出：
#      200 429 429 429 ...     ← replenishRate: 1，每秒只補 1 個令牌
#
#  ⚠ 前置條件：
#      docker compose up -d redis    沒有 Redis 的話第一個請求就是 500，不是 429
#      gatewayserver 要在跑（8072）
# =============================================================================

param(
    [string] $User  = "alice",
    [int]    $Count = 10,
    # ⚠ 限流只掛在 card 那條路由上。打 loan（那條是 retry）或 account（斷路器）
    #   會全部 200，看不到限流。
    [string] $Url   = "http://localhost:8072/bank/card/api/contact-info"
)

# ⚠ 一定要用 curl.exe，不能只寫 curl ——
#   PowerShell 的 curl 是 Invoke-WebRequest 的別名，參數完全不同會直接報錯。
$curl = "curl.exe"

Write-Host "=== Gateway 限流（令牌桶 + 分 user + Redis）===" -ForegroundColor Cyan
Write-Host "目標   : $Url"
Write-Host "使用者 : $(if ($User) { $User } else { '(不帶 user 標頭 → anonymous)' })"
Write-Host "次數   : $Count"
Write-Host ""

$codes = @()

1..$Count | ForEach-Object {
    # -s 安靜模式、-o NUL 丟掉 body、-w 只印狀態碼
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

# ── 統計 ──────────────────────────────────────────────────────────────────
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

# ── Redis 裡的證據 ────────────────────────────────────────────────────────
Write-Host ""
Write-Host "Redis 裡的額度桶（前綴是路由 id，後面是 KeyResolver 回傳的字串）：" -ForegroundColor DarkGray
docker compose exec -T redis redis-cli --scan --pattern "request_rate_limiter*" 2>$null

Write-Host ""
Write-Host "🔑 換個 -User 再跑一次，會看到新的桶、而且第一次還是 200 —— 那就是「分人」。" -ForegroundColor DarkGray

# =============================================================================
#  啟動 webhook tunnel —— 讓 GitHub 的 push 事件進得來本機的 configserver
#
#      你 push configyml/*.yml
#          ↓
#      GitHub → https://hkdk.events/z9b5ieg8k4airh
#          ↓
#      Hookdeck（事件排隊，CLI 沒開就先存著）
#          ↓  ← 這支腳本負責接通這一段
#      http://localhost:8071/monitor
#          ↓  configserver 解析 payload → 只通知有變的服務
#      RabbitMQ 廣播 → account / loan / card 重抓設定
#
#  ⚠ 為什麼跑容器而不是本機的 hookdeck.exe：
#    那顆執行檔未簽章，被 Smart App Control 擋掉（事件記錄 CodeIntegrity 3033/3077），
#    而且 npm 那層包裝把錯誤吃掉，症狀是「什麼都不印就跳回提示字元」。
#    容器裡是 Linux binary，不受管轄。
#
#  🔑 刻意「不」寫進 compose.yml：
#    收進去的價值幾乎全來自「跟其他容器一起 docker compose up -d」，
#    但 hookdeck 正好是那個永遠不會這樣跑的 —— 它的價值在互動式儀表板，
#    而 -d（背景）沒有終端機，畫不出畫面。既然一定要單獨前景啟動，
#    寫在這裡反而只需要維護一份設定，也不用讓 compose.yml 出現
#    ${USERPROFILE} 這種 Windows 專用的路徑。
#
#  用法（在專案根目錄）：
#      .\hookdeck-listen.ps1          常駐執行，Ctrl-C 結束
#
#  ⚠ 這是常駐指令，會佔住整個視窗。要另開一個視窗做其他事。
#  ⚠ 一次只能開一個 —— 同時跑兩個會共用同一個 source，事件被搶走一半。
# =============================================================================

param(
    # configserver 的埠。⚠ 不是 gateway 的 8072
    [int]    $Port = 8071,

    # 🔑 Hookdeck 的 source 名稱，決定拿到哪一個 hkdk.events 網址。
    #    必須維持 configserver —— 換名字會建一個新的 source、拿到新網址，
    #    而 GitHub webhook 還指著舊的，事件就永遠進不來。
    [string] $Source = "configserver",

    # 轉發到本機時要補上的路徑。漏了會打到 configserver 根目錄，回 404。
    [string] $Path = "/monitor"
)

$ErrorActionPreference = "Stop"

# ── 先檢查 configserver 在不在 ────────────────────────────────────────────────
#    不擋的話 tunnel 會連上、GitHub 也送得到，但最後一哩是 connection refused，
#    而錯誤只出現在容器輸出裡，很容易誤以為是 tunnel 壞了。
#
#    🔑 這裡戳的是「主機的 8071」，所以 configserver 不管跑 IntelliJ 還是跑容器
#       都算數 —— 這正是 compose 的 depends_on 做不到的事（它只看得到容器）。
$tcp = [System.Net.Sockets.TcpClient]::new()
$up  = $false
try   { $up = $tcp.ConnectAsync("localhost", $Port).Wait(1500) }
catch { }
finally { $tcp.Dispose() }

if (-not $up) {
    Write-Host "⚠ configserver（localhost:$Port）沒有回應" -ForegroundColor Yellow
    Write-Host "  tunnel 還是會連上，但事件送到最後會失敗。" -ForegroundColor DarkGray
    Write-Host "  建議先啟動 configserver 再回來。" -ForegroundColor DarkGray
    Write-Host ""
}

Write-Host "=== Hookdeck tunnel（容器版）===" -ForegroundColor Cyan
Write-Host "source : $Source"
Write-Host "轉發到 : http://localhost:$Port$Path"
Write-Host "⚠ 常駐執行，Ctrl-C 結束（容器會自動刪除）" -ForegroundColor DarkGray
Write-Host ""

# ⚠ 四個地方都不能少：
#   -it                     -t 讓它畫得出儀表板、-i 讓方向鍵有反應。
#                           少了就只剩一行一行的純文字。
#   --rm                    Ctrl-C 後自動刪容器。不加的話每跑一次留一顆殘骸。
#   -v + --hookdeck-config  映像檔沒設 HOME，靠預設路徑找不到 config.toml，
#                           會退化成「臨時訪客帳號」→ 拿到全新的 source 網址，
#                           而 GitHub 還指著舊的 → 事件永遠進不來，且畫面上一切正常。
#   host.docker.internal    容器裡的 localhost 是容器自己，連不到主機的 8071。
docker run --rm -it `
    -v "$env:USERPROFILE\.config\hookdeck:/config" `
    hookdeck/hookdeck-cli `
    --hookdeck-config /config/config.toml `
    listen "http://host.docker.internal:$Port" $Source --path $Path

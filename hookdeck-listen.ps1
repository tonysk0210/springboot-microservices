# =============================================================================
#  啟動 webhook tunnel —— 讓 GitHub 的 push 事件進得來本機的 configserver
#
#      你 push
#          ↓
#      GitHub → https://hkdk.events/z9b5ieg8k4airh
#          ↓
#      Hookdeck（事件排隊，CLI 沒開就先存著）
#          ↓  ← 這支腳本負責接通這一段
#      http://localhost:8071/monitor
#          ↓  configserver 解析 payload → 只通知有變的服務
#      RabbitMQ 廣播 → account / loan / card 重抓設定
#
#  ⚠ 為什麼用容器而不是直接跑 hookdeck.exe：
#    Windows 版的執行檔未簽章，被 Smart App Control 擋掉（事件 CodeIntegrity 3033/3077），
#    而且什麼訊息都不印就跳回提示字元。容器裡是 Linux binary，SAC 管不到。
#
#  用法（在專案根目錄）：
#      .\hookdeck-listen.ps1          常駐執行，Ctrl-C 結束
#
#  ⚠ 這是常駐指令，會佔住整個視窗。要另開一個視窗做其他事。
# =============================================================================

param(
    # configserver 的埠。⚠ 不是 gateway 的 8072
    [int]    $Port = 8071,

    # 🔑 Hookdeck 的 source 名稱，決定拿到哪一個 hkdk.events 網址。
    #    必須維持 configserver —— 換名字會建一個新的 source、拿到新網址，
    #    而 GitHub webhook 還指著舊的，事件就永遠進不來。
    [string] $Source = "configserver",

    # 轉發到本機時要補上的路徑
    [string] $Path = "/monitor"
)

$ErrorActionPreference = "Stop"

# ── 先檢查 configserver 在不在 ────────────────────────────────────────────────
#    不擋的話 tunnel 會連上、GitHub 也送得到，但最後一哩路是 connection refused，
#    而錯誤只出現在容器的輸出裡，很容易誤以為是 tunnel 壞了。
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
Write-Host "⚠ 常駐執行，Ctrl-C 結束" -ForegroundColor DarkGray
Write-Host ""

# ⚠ 兩個參數不能少：
#   -v + --hookdeck-config  映像檔沒設 HOME，靠預設路徑找不到 config.toml，
#                           會退化成「臨時訪客帳號」→ 拿到的是全新的 source 網址
#   host.docker.internal    容器裡的 localhost 是容器自己，連不到主機的 8071
docker run --rm -it `
    -v "$env:USERPROFILE\.config\hookdeck:/config" `
    hookdeck/hookdeck-cli `
    --hookdeck-config /config/config.toml `
    listen "http://host.docker.internal:$Port" $Source --path $Path

# GitHub push → Hookdeck Cloud 公開 URL → tunnel → Config Server /monitor
# → RabbitMQ Bus → Account/Loan/Card 重新取得設定。
# hookdeck-ms 會主動連到 Hookdeck Cloud 完成 tunnel 握手；Cloud 收到 Webhook 後，沿這條 tunnel 轉送到主機的 localhost:8071/monitor。
# 本腳本以前景容器維持 tunnel；保持視窗開啟，Ctrl-C 可停止。

param(
    # Config Server port（不是 Gateway 的 8072）。
    [int]    $Port = 8071,

    # Hookdeck CLI 的 source 名稱，可自訂；不等於 GitHub Webhook URL。
    # 若 Hookdeck 已建立指定 source，請填該 source 名稱。
    [string] $Source = "configserver",

    # 轉發目的地路徑；Config Monitor 使用 /monitor。
    [string] $Path = "/monitor",

    # Hookdeck 容器名稱。
    [string] $ContainerName = "hookdeck-ms",

    # Docker 記憶體上限，可用 -MemoryLimit 調整。
    [string] $MemoryLimit = "512m"
)

$ErrorActionPreference = "Stop"

# 先確認 Config Server 的 port 可連線，避免 tunnel 建立後才發現 /monitor 無法到達。
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

# -it 顯示互動式 tunnel；--rm 結束後刪除容器；volume 保存 Hookdeck source 設定。
# CLI 先向 Hookdeck Cloud 建立 tunnel；收到 Webhook 後轉送到 host.docker.internal:8071（也就是主機的 localhost:8071）。
docker run --rm -it `
    --name $ContainerName `
    --memory $MemoryLimit `
    -v "$env:USERPROFILE\.config\hookdeck:/config" `
    hookdeck/hookdeck-cli `
    --hookdeck-config /config/config.toml `
    listen "http://host.docker.internal:$Port" $Source --path $Path

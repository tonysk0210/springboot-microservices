# =============================================================================
#  push-images-to-ghcr.ps1 —— 把本機四個 image 推到 GitHub Packages (ghcr.io)
# =============================================================================
#
#  這個腳本「只負責推」，不負責建。執行前四個 image 必須已經存在本機：
#      cd configserver; ./mvnw compile jib:dockerBuild                      (Jib)
#      cd account;      docker build -t anthonysk/account:0.0.1-SNAPSHOT .  (Dockerfile)
#      cd loan;         ./mvnw spring-boot:build-image                      (Buildpacks)
#      cd card;         ./mvnw compile jib:dockerBuild                      (Jib)
#  （rabbitmq 用官方 image，不需要推）
#
#  做的事很單純，每個服務兩步：
#      docker tag  anthonysk/xxx:tag  ghcr.io/tonysk0210/xxx:tag   (加個名牌，瞬間完成)
#      docker push ghcr.io/tonysk0210/xxx:tag                      (真正上傳)
#
#  為什麼走這條路，而不是各工具的原生推送（buildx --push / jib:build /
#  build-image publish）？
#    1. 三種建法統一成同一套流程，不必記三種工具的推送語法
#    2. Buildpacks 的 publish 不讀 docker login 的憑證，得把 token 寫進 pom，
#       走這條就沿用 docker login，什麼都不用寫
#    3. buildx --push 不會把 image 留在本機，本機要跑還得再 build 一次；
#       先建到本機再推就沒這問題
#
#  ---------------------------------------------------------------------------
#  用法
#      .\push-images-to-ghcr.ps1                      # 推 0.0.1-SNAPSHOT
#      .\push-images-to-ghcr.ps1 -Tag 0.0.2-SNAPSHOT  # 推指定版本
#      .\push-images-to-ghcr.ps1 -Services account    # 只推其中一個（可多個，逗號分隔）
#      .\push-images-to-ghcr.ps1 -WhatIf              # 只印指令不真的執行，先看看會做什麼
#
#  ⚠ 執行前要先登入一次（同一台機器只需做一次，憑證會存起來）：
#      docker login ghcr.io -u tonysk0210
#    密碼欄位貼的是 GitHub token，不是 GitHub 密碼。
#    token 產生方式：https://github.com/settings/tokens → classic token
#                    → 勾選 write:packages
# =============================================================================

[CmdletBinding(SupportsShouldProcess = $true)]
param(
    # image 版本標籤，要跟三個 pom.xml 的 <version> 一致
    [string]   $Tag        = '0.0.1-SNAPSHOT',

    # 本機 image 的命名空間（Docker Hub 風格的名字，其實只是本機標籤）
    [string]   $LocalOwner = 'anthonysk',

    # ghcr.io 的命名空間 = 你的 GitHub 帳號（必須全小寫）
    [string]   $GhcrOwner  = 'tonysk0210',

    # 要推哪幾個服務
    [string[]] $Services   = @('configserver', 'account', 'loan', 'card')
)

$ErrorActionPreference = 'Stop'

# --- 前置檢查：Docker 在跑嗎 -------------------------------------------------
# 沒有這段的話，Docker 沒開時會在第一次 docker tag 才失敗，訊息不好懂
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ Docker 沒有在執行，請先開啟 Docker Desktop" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "推送目標：ghcr.io/$GhcrOwner   版本：$Tag" -ForegroundColor Cyan
Write-Host ""

$pushed  = @()
$skipped = @()

foreach ($svc in $Services) {
    $local = "${LocalOwner}/${svc}:${Tag}"
    $ghcr  = "ghcr.io/${GhcrOwner}/${svc}:${Tag}"

    Write-Host "─── $svc ──────────────────────────────────────────" -ForegroundColor DarkGray

    # --- 檢查本機有沒有這個 image ---
    # 直接 docker tag 也會失敗，但錯誤訊息（No such image）不會告訴你該去建哪個，
    # 所以這裡先擋下來並附上建置指令
    docker image inspect $local *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  ✗ 本機找不到 $local" -ForegroundColor Red
        switch ($svc) {
            'configserver' { Write-Host "    先建：cd configserver; ./mvnw compile jib:dockerBuild" -ForegroundColor Yellow }
            'account'      { Write-Host "    先建：cd account;      docker build -t $local ." -ForegroundColor Yellow }
            'loan'         { Write-Host "    先建：cd loan;         ./mvnw spring-boot:build-image" -ForegroundColor Yellow }
            'card'         { Write-Host "    先建：cd card;         ./mvnw compile jib:dockerBuild" -ForegroundColor Yellow }
        }
        $skipped += $svc
        continue
    }

    # --- 貼上 ghcr 的名牌 ---
    # docker tag 不會複製 image，只是讓同一份 image 多一個名字，所以是瞬間完成
    if ($PSCmdlet.ShouldProcess($ghcr, "docker tag $local")) {
        docker tag $local $ghcr
        if ($LASTEXITCODE -ne 0) { $skipped += $svc; continue }
    }

    # --- 真正上傳 ---
    if ($PSCmdlet.ShouldProcess($ghcr, "docker push")) {
        Write-Host "  推送中..." -ForegroundColor DarkGray
        docker push $ghcr
        if ($LASTEXITCODE -ne 0) {
            Write-Host "  ✗ 推送失敗" -ForegroundColor Red
            Write-Host "    denied / unauthorized → 先跑 docker login ghcr.io -u $GhcrOwner" -ForegroundColor Yellow
            Write-Host "    （token 要有 write:packages 權限）" -ForegroundColor Yellow
            $skipped += $svc
            continue
        }
        Write-Host "  ✓ $ghcr" -ForegroundColor Green
        $pushed += $svc
    }
}

# --- 收尾 --------------------------------------------------------------------
Write-Host ""
if ($pushed.Count -gt 0) {
    Write-Host "完成：$($pushed -join ', ')" -ForegroundColor Green
    Write-Host ""
    Write-Host "後續：" -ForegroundColor Cyan
    # ⚠ 這裡必須寫 ${GhcrOwner}，不能寫 $GhcrOwner?tab —— PowerShell 會把
    #   問號一起吃進變數名稱，變成一個不存在的變數，網址就少了帳號。
    Write-Host "  1. 看結果  https://github.com/${GhcrOwner}?tab=packages"
    Write-Host "  2. ghcr.io 預設是 Private。要讓人不登入就能 pull："
    Write-Host "     Package settings → Danger Zone → Change visibility → Public"
    Write-Host "  3. 檢查 manifest 格式（決定 package 頁面讀不讀得到描述）："
    Write-Host "     docker buildx imagetools inspect ghcr.io/$GhcrOwner/account:$Tag --raw"
    Write-Host "       vnd.docker.distribution.manifest.v2+json → 會讀 config label ✓"
    Write-Host "       vnd.oci.image.manifest.v1+json           → 只讀 manifest annotation"
    Write-Host ""
    Write-Host "  ⚠ package 的描述與 repo 連結在「首次建立時」就定型，事後補推同一個 tag"
    Write-Host "    不會更新。要改的話得先到 GitHub 刪掉該 package 再重推。" -ForegroundColor Yellow

    if ($pushed -contains 'account') {
        Write-Host ""
        Write-Host "  ℹ account 的 manifest 是 OCI 格式（本機 containerd image store 的結果），" -ForegroundColor DarkGray
        Write-Host "    這種格式 GitHub 只讀 manifest annotation、不會退回讀 config LABEL。" -ForegroundColor DarkGray
        Write-Host "    但這個 package 當初是用 buildx --annotation --push 建立的，描述已經" -ForegroundColor DarkGray
        Write-Host "    定型保留，所以用 tag+push 更新版本不影響顯示 —— 不用處理。" -ForegroundColor DarkGray
        Write-Host "    （若哪天刪掉 package 重建，就必須改回 buildx --annotation 那條路，" -ForegroundColor DarkGray
        Write-Host "      指令記在 account/Dockerfile 最後一段。）" -ForegroundColor DarkGray
    }
}
if ($skipped.Count -gt 0) {
    Write-Host "略過：$($skipped -join ', ')" -ForegroundColor Yellow
}
Write-Host ""

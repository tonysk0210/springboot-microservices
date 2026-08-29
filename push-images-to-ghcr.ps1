<#
.SYNOPSIS
    將本機 image 推送到 GitHub Container Registry（ghcr.io）。

.DESCRIPTION
    本腳本只負責 tag 與 push，image 必須先在本機建立。
    image 格式：[Registry]/[Namespace]/[Repository]:[Tag]
    未指定 Registry 時，Docker 預設使用 Docker Hub（docker.io）。
    推送前請先執行：docker login ghcr.io -u <GitHub 帳號>
    密碼請使用具有 write:packages 權限的 GitHub Token。

.EXAMPLE
    .\push-images-to-ghcr.ps1
    .\push-images-to-ghcr.ps1 -Services account
    .\push-images-to-ghcr.ps1 -Tag 0.0.2-SNAPSHOT
    .\push-images-to-ghcr.ps1 -WhatIf
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    # 本機 image 的版本，必須與各服務的 image tag 相同。
    [string]   $Tag        = '0.0.1-SNAPSHOT',

    # 本機 image 的 namespace；只在本機使用時可自訂。
    [string]   $LocalOwner = 'anthonysk',

    # GHCR namespace，通常是 GitHub username 或 organization 名稱。
    [string]   $GhcrOwner  = 'tonysk0210',

    # 預設推送七個微服務。
    [string[]] $Services   = @('configserver', 'eurekaserver', 'account', 'loan',
                               'card', 'messageservice', 'gatewayserver')
)

$ErrorActionPreference = 'Stop'
$pushed  = @()
$skipped = @()

# 確認 Docker daemon 正在執行。
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ Docker 沒有在執行，請先開啟 Docker Desktop" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "推送目標：ghcr.io/$GhcrOwner   版本：$Tag" -ForegroundColor Cyan
Write-Host ""

foreach ($svc in $Services) {
    $local = $LocalOwner + '/' + $svc + ':' + $Tag
    $ghcr  = 'ghcr.io/' + $GhcrOwner + '/' + $svc + ':' + $Tag

    Write-Host "--- $svc ---" -ForegroundColor DarkGray

    # 先確認本機 image 存在。
    docker image inspect $local *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  ✗ 本機找不到 $local，請先建立 image" -ForegroundColor Red
        $skipped += $svc
        continue
    }

    # tag 只是替同一份 image 加上 GHCR 名稱，不會複製 image。
    if ($PSCmdlet.ShouldProcess($ghcr, "docker tag $local")) {
        docker tag $local $ghcr
        if ($LASTEXITCODE -ne 0) { $skipped += $svc; continue }
    }

    # 推送到 GHCR。
    if ($PSCmdlet.ShouldProcess($ghcr, "docker push")) {
        Write-Host "  推送中..." -ForegroundColor DarkGray
        docker push $ghcr
        if ($LASTEXITCODE -ne 0) {
            Write-Host "  ✗ 推送失敗：請確認 ghcr.io 登入帳號與 Token 權限" -ForegroundColor Red
            $skipped += $svc
            continue
        }
        Write-Host "  ✓ $ghcr" -ForegroundColor Green
        $pushed += $svc
    }
}

Write-Host ""
Write-Host "=== 摘要 ===" -ForegroundColor Cyan
if ($pushed.Count -gt 0) {
    Write-Host "完成：$($pushed -join ', ')" -ForegroundColor Green
    Write-Host ""
    Write-Host "後續：" -ForegroundColor Cyan
    Write-Host "  1. 查看套件：https://github.com/${GhcrOwner}?tab=packages"
    Write-Host "  2. ghcr.io 預設為 Private；可在 Package settings → Danger Zone 改為 Public"
    Write-Host "  3. 檢查 manifest：docker buildx imagetools inspect ghcr.io/$GhcrOwner/account:$Tag --raw"
}
if ($skipped.Count -gt 0) {
    Write-Host "略過：$($skipped -join ', ')" -ForegroundColor Yellow
}
if ($pushed.Count -eq 0 -and $skipped.Count -eq 0) {
    Write-Host '沒有服務需要推送。'
}

<#
.SYNOPSIS
    一次建立所有微服務的本機 Docker image。

    image 名稱格式：[Registry]/[Namespace]/[Repository]:[Tag]
    未指定 Registry 時，預設使用 Docker Hub（docker.io）。
    本機 image 目前使用 anthonysk 作為 Namespace；推送 Docker Hub 時，Namespace 必須是你的 Docker Hub username 或你有權限的 namespace。

.DESCRIPTION
    依各服務目前的建置方式執行：
    - configserver、eurekaserver、card、messageservice、gatewayserver：Jib
    - account：Dockerfile
    - loan：Spring Boot Buildpacks

    這支腳本只負責建立本機 image，不會推送到 GHCR。
    推送請另外執行 push-images-to-ghcr.ps1。

.EXAMPLE
    .\build-images.ps1
    建立全部七個服務。

.EXAMPLE
    .\build-images.ps1 -Services account,loan
    只建立 Account 和 Loan。

.EXAMPLE
    .\build-images.ps1 -WhatIf
    只顯示將執行的指令，不實際建置。
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [ValidateSet('configserver', 'eurekaserver', 'account', 'loan', 'card', 'messageservice', 'gatewayserver')]
    [string[]] $Services = @('configserver', 'eurekaserver', 'account', 'loan', 'card', 'messageservice', 'gatewayserver')
)

$ErrorActionPreference = 'Stop'
$failed = @()

# Jib 的 pom 使用 docker://，因此建置前確保 Java base image 已在本機；沒有才下載。
$jibSelected = $Services | Where-Object { $_ -in @('configserver', 'eurekaserver', 'card', 'messageservice', 'gatewayserver') }
if ($jibSelected.Count -gt 0) {
    $baseImage = 'eclipse-temurin:25-jre-alpine'
    & docker image inspect $baseImage *> $null
    if ($LASTEXITCODE -ne 0) {
        $pullDescription = "docker pull $baseImage"
        if ($PSCmdlet.ShouldProcess($baseImage, $pullDescription)) {
            & docker pull $baseImage
            if ($LASTEXITCODE -ne 0) { throw "無法下載 base image：$baseImage" }
        }
    }
}

foreach ($service in $Services) {
    $serviceDir = Join-Path $PSScriptRoot $service
    $pomPath = Join-Path $serviceDir 'pom.xml'

    if (-not (Test-Path $pomPath)) {
        Write-Host "[失敗] 找不到 $pomPath" -ForegroundColor Red
        $failed += $service
        continue
    }

    # 從 pom.xml 讀取版本，確保 Dockerfile image tag 與其他建法一致。
    [xml]$pom = Get-Content -Raw $pomPath
    $version = [string]$pom.project.version
    $image = "anthonysk/${service}:$version"

    Write-Host "`n=== 建立 $image ===" -ForegroundColor Cyan

    Push-Location $serviceDir
    try {
        if ($service -eq 'account') {
            $description = "docker build -t $image ."
            if ($PSCmdlet.ShouldProcess($service, $description)) {
                & docker build -t $image .
                if ($LASTEXITCODE -ne 0) { throw "Dockerfile 建置失敗" }
            }
        }
        elseif ($service -eq 'loan') {
            $description = '.\mvnw.cmd spring-boot:build-image "-Dmaven.test.skip=true"'
            if ($PSCmdlet.ShouldProcess($service, $description)) {
                & .\mvnw.cmd spring-boot:build-image '-Dmaven.test.skip=true'
                if ($LASTEXITCODE -ne 0) { throw 'Buildpacks 建置失敗' }
            }
        }
        else {
            $description = '.\mvnw.cmd compile jib:dockerBuild'
            if ($PSCmdlet.ShouldProcess($service, $description)) {
                & .\mvnw.cmd compile jib:dockerBuild
                if ($LASTEXITCODE -ne 0) { throw 'Jib 建置失敗' }
            }
        }

        if (-not $WhatIfPreference) {
            Write-Host "[完成] $image" -ForegroundColor Green
        }
    }
    catch {
        Write-Host "[失敗] $service：$_" -ForegroundColor Red
        $failed += $service
    }
    finally {
        Pop-Location
    }
}

# 清理沒有 repository／tag 的 dangling images；-WhatIf 不執行清理。
if (-not $WhatIfPreference) {
    Write-Host "`n=== 清理 dangling images ===" -ForegroundColor Cyan
    & docker image prune -f
}

Write-Host "`n=== 建置摘要 ===" -ForegroundColor Cyan
if ($failed.Count -eq 0) {
    Write-Host '全部 image 建立完成。' -ForegroundColor Green
    Write-Host '若要推送 GHCR，請執行：.\push-images-to-ghcr.ps1' -ForegroundColor DarkGray
}
else {
    Write-Host "失敗：$($failed -join ', ')" -ForegroundColor Red
    exit 1
}

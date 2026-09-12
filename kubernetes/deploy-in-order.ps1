<#
.SYNOPSIS
    依服務依賴順序部署本機 Kubernetes 環境。

.DESCRIPTION
    先套用 Secret/ConfigMap，再等待 Config Server 與 Eureka 就緒，
    接著部署業務服務，最後才部署 Gateway。
    image 必須已在 Kubernetes 節點或可從 Registry 下載。
#>
[CmdletBinding()]
param(
    [int] $TimeoutSeconds = 300
)

$ErrorActionPreference = 'Stop'

function Invoke-Kubectl {
    param([Parameter(Mandatory)][string[]] $KubectlArgs)

    & kubectl @KubectlArgs
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl 指令失敗：kubectl $($KubectlArgs -join ' ')"
    }
}

function Apply-Manifest {
    param([Parameter(Mandatory)][string] $Path)

    $manifestPath = Join-Path $PSScriptRoot $Path
    Write-Host "`n套用 $manifestPath" -ForegroundColor Cyan
    Invoke-Kubectl @('apply', '-f', $manifestPath)
}

function Wait-Deployment {
    param([Parameter(Mandatory)][string] $Name)

    Write-Host "等待 $Name 就緒..." -ForegroundColor DarkGray
    Invoke-Kubectl @(
        'wait', '--for=condition=available', "deployment/$Name",
        "--timeout=$($TimeoutSeconds)s"
    )
}

# 設定資源必須先存在，Pod 才能讀取環境變數。
Apply-Manifest 'config\secrets.yml'
Apply-Manifest 'config\configmap.yml'

# Config Server 是其他服務取得集中設定的來源。
Apply-Manifest 'configserver.yml'
Wait-Deployment 'configserver-deployment'

# Eureka 提供服務註冊與查找。
Apply-Manifest 'eurekaserver.yml'
Wait-Deployment 'eurekaserver-deployment'

# 部署業務服務；MessageService 不需等待 Gateway。
Apply-Manifest 'account.yml'
Apply-Manifest 'loan.yml'
Apply-Manifest 'card.yml'
Apply-Manifest 'messageservice.yml'

# Gateway 依賴三個業務服務，等它們 Ready 後才建立。
Wait-Deployment 'account-deployment'
Wait-Deployment 'loan-deployment'
Wait-Deployment 'card-deployment'

Apply-Manifest 'gatewayserver.yml'

Write-Host "`n=== 部署完成 ===" -ForegroundColor Green
Invoke-Kubectl @('get', 'deployments')
Invoke-Kubectl @('get', 'pods')

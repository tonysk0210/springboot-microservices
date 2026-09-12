<#
.SYNOPSIS
    重啟 Kubernetes Deployment，讓 Pod 重新讀取最新的環境設定。

.DESCRIPTION
    只執行 rollout restart，不會套用 YAML，也不會修改 replicas。
    例如 ConfigMap 或 Secret 更新後，可用此腳本讓服務重新載入設定。

.EXAMPLE
    .\restart-deployments.ps1

.EXAMPLE
    .\restart-deployments.ps1 -Services gatewayserver
#>
[CmdletBinding()]
param(
    [ValidateSet('configserver', 'eurekaserver', 'account', 'loan', 'card', 'messageservice', 'gatewayserver')]
    [string[]] $Services = @('configserver', 'eurekaserver', 'account', 'loan', 'card', 'messageservice', 'gatewayserver'),

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

Write-Host '=== 前置檢查 ===' -ForegroundColor Cyan

$context = kubectl config current-context
if ($LASTEXITCODE -ne 0) {
    throw 'kubectl 沒有可用的 context'
}
if ($context -ne 'docker-desktop') {
    throw "目前 context 是 '$context'，不是 docker-desktop；為安全起見中止。"
}
Write-Host "  [ok] kubectl context = $context" -ForegroundColor Green

Write-Host '=== 重啟 Alloy ===' -ForegroundColor Cyan
& kubectl get daemonset/alloy-k8s *> $null
if ($LASTEXITCODE -ne 0) {
    throw '找不到 DaemonSet：alloy-k8s；請先執行 deploy-in-order.ps1 或 kubectl apply -f .\observability\alloy-k8s.yml'
}
Invoke-Kubectl @('rollout', 'restart', 'daemonset/alloy-k8s')
Invoke-Kubectl @(
    'rollout', 'status', 'daemonset/alloy-k8s',
    "--timeout=$($TimeoutSeconds)s"
)

Write-Host '=== 重啟 Deployment ===' -ForegroundColor Cyan

foreach ($service in $Services) {
    $deployment = "$service-deployment"

    # 服務不存在時直接報錯，避免誤以為已完成。
    & kubectl get "deployment/$deployment" *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "找不到 Deployment：$deployment"
    }

    Invoke-Kubectl @('rollout', 'restart', "deployment/$deployment")
    Invoke-Kubectl @(
        'rollout', 'status', "deployment/$deployment",
        "--timeout=$($TimeoutSeconds)s"
    )
}

Write-Host '=== Pod 現況 ===' -ForegroundColor Cyan
Invoke-Kubectl @('get', 'deployments')
Invoke-Kubectl @('get', 'pods')

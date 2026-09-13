<#
.SYNOPSIS
    使用 Helm 部署 Alloy 與七個微服務。

.DESCRIPTION
    1. 檢查 kubectl、Helm 與 docker-desktop context。
    2. 在 default Namespace 由 Helm 管理 Alloy DaemonSet。
    3. 將共用 Secret 複製到指定 Namespace（預設 helm-test）。
    4. 依序部署七個微服務 Chart，沿用固定 Service port 與 NodePort。
    5. 顯示 Release、Deployment、Pod 與 Service 狀態。

    切換 Namespace 前，請先停止另一組服務，避免固定 NodePort 衝突。

.EXAMPLE
    # 從專案根目錄執行
    .\helm\deploy-helm-isolated.ps1

.EXAMPLE
    # 從 helm 目錄執行
    cd .\helm
    .\deploy-helm-isolated.ps1

.EXAMPLE
    # 指定測試 Namespace 與等待時間（單位：秒）
    .\helm\deploy-helm-isolated.ps1 -Namespace helm-test-2 -TimeoutSeconds 600

.EXAMPLE
    # 完整執行流程（從專案根目錄）
    cd D:\Fork\github\springboot-microservices
    docker compose -f compose.k8s-infra.yml --profile observability up -d
    .\build-images.ps1
    .\kubernetes\import-local-images-to-k8s.ps1
    .\helm\deploy-helm-isolated.ps1
#>
[CmdletBinding()]
param(
    [string] $Namespace = 'helm-test',
    [int] $TimeoutSeconds = 300
)

$ErrorActionPreference = 'Stop'

function Invoke-Checked {
    param(
        [Parameter(Mandatory)][string] $Command,
        [Parameter(Mandatory)][string[]] $Arguments
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "指令失敗：$Command $($Arguments -join ' ')"
    }
}

Write-Host '=== 前置檢查 ===' -ForegroundColor Cyan

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw '找不到 kubectl。'
}
if (-not (Get-Command helm -ErrorAction SilentlyContinue)) {
    throw '找不到 helm。'
}

$context = kubectl config current-context
if ($LASTEXITCODE -ne 0 -or $context -ne 'docker-desktop') {
    throw "目前 kubectl context 是 '$context'，預期為 'docker-desktop'。"
}
Write-Host "  [ok] kubectl context = $context" -ForegroundColor Green

$charts = @(
    'configserver',
    'eurekaserver',
    'account',
    'loan',
    'card',
    'messageservice',
    'gatewayserver'
)

# Alloy 只保留一份：若尚未由 Helm 管理，先清理舊 kubectl 資源。
$alloyChart = Join-Path $PSScriptRoot 'observability\alloy'
$legacyAlloy = Join-Path $PSScriptRoot '..\kubernetes\observability\alloy-k8s.yml'
Write-Host ''
Write-Host '=== 以 Helm 部署 Alloy（default Namespace） ===' -ForegroundColor Cyan

& helm status alloy-k8s --namespace default *> $null
$alloyReleaseExists = ($LASTEXITCODE -eq 0)
if (-not $alloyReleaseExists) {
    # 不只檢查 DaemonSet；即使只殘留 ServiceAccount、RBAC 或 ConfigMap，
    # 也必須先清除，否則 Helm 會因缺少 ownership metadata 而拒絕接管。
    Write-Host '移除舊的 kubectl Alloy 資源...' -ForegroundColor DarkGray
    Invoke-Checked 'kubectl' @('delete', '-f', $legacyAlloy, '--ignore-not-found')
}

Invoke-Checked 'helm' @(
    'upgrade', '--install', 'alloy-k8s', $alloyChart,
    '--namespace', 'default',
    '--create-namespace',
    '--wait',
    "--timeout=$($TimeoutSeconds)s"
)

# Discovery Demo 固定部署在 default，使用 ClusterRole 查詢所有 namespace。
$discoveryChart = Join-Path $PSScriptRoot 'services\discoveryserver'
$discoveryManifest = Join-Path $PSScriptRoot '..\kubernetes\discoveryserver.yml'
Write-Host ''
Write-Host '以 Helm 部署 Kubernetes Discovery Server（default Namespace）' -ForegroundColor Cyan

# 若先前由 kubectl 建立，先清理 ownership metadata，再交給 Helm 管理。
& helm status discoveryserver --namespace default *> $null
if ($LASTEXITCODE -ne 0) {
    Invoke-Checked 'kubectl' @('delete', '-f', $discoveryManifest, '--ignore-not-found')
}

Invoke-Checked 'helm' @(
    'upgrade', '--install', 'discoveryserver', $discoveryChart,
    '--reset-values',
    '--namespace', 'default',
    '--create-namespace',
    '--wait',
    "--timeout=$($TimeoutSeconds)s"
)

Write-Host ''
Write-Host "=== 建立 Namespace：$Namespace ===" -ForegroundColor Cyan
kubectl create namespace $Namespace --dry-run=client -o yaml | kubectl apply -f -
if ($LASTEXITCODE -ne 0) {
    throw "無法建立 Namespace：$Namespace"
}

# 各服務 Chart 不建立 Secret；將共用 Secret 複製到目標 Namespace。
$secretPath = Join-Path $PSScriptRoot '..\kubernetes\config\secrets.yml'
Write-Host ''
Write-Host "套用共用 Secret：$secretPath" -ForegroundColor Cyan
Invoke-Checked 'kubectl' @('apply', '-n', $Namespace, '-f', $secretPath)

Write-Host ''
Write-Host '=== 依序部署 Helm Charts ===' -ForegroundColor Cyan
foreach ($chart in $charts) {
    $chartPath = Join-Path $PSScriptRoot "services\$chart"
    Write-Host ''
    Write-Host "部署 $chart（沿用 Kubernetes Service port 與固定 NodePort）" -ForegroundColor Cyan

    Invoke-Checked 'helm' @(
        'upgrade', '--install', $chart, $chartPath,
        '--namespace', $Namespace,
        '--create-namespace',
        # 捨棄舊 Release 覆寫值，重新採用 Chart 目前設定（含固定 NodePort）。
        '--reset-values',
        '--wait',
        "--timeout=$($TimeoutSeconds)s"
    )
}

Write-Host ''
Write-Host "=== Helm 部署完成：$Namespace ===" -ForegroundColor Green
Invoke-Checked 'helm' @('list', '--namespace', $Namespace)
Invoke-Checked 'kubectl' @('get', 'deployments', '-n', $Namespace)
Invoke-Checked 'kubectl' @('get', 'pods', '-n', $Namespace)
Invoke-Checked 'kubectl' @('get', 'services', '-n', $Namespace)

<#
.SYNOPSIS
    將本機 image 匯入 Docker Desktop Kubernetes 節點。

.DESCRIPTION
    Docker 與 Kubernetes 節點使用不同的 image 儲存區；本腳本以
    save → docker cp → containerd import 搬移 image，避免 K8s 改拉舊版 registry image。
    tar 必須先存成檔案，不能直接用 PowerShell pipeline 傳送二進位資料。

.PARAMETER Services
    要處理的服務；預設為全部七個。

.PARAMETER Force
    忽略快取，強制重新匯入 image。

.EXAMPLE
    .\import-local-images-to-k8s.ps1 -Services loan
.EXAMPLE
    .\import-local-images-to-k8s.ps1
#>
[CmdletBinding()]
param(
    [ValidateSet('configserver', 'eurekaserver', 'account', 'loan', 'card', 'messageservice', 'gatewayserver')]
    [string[]] $Services = @('configserver', 'eurekaserver', 'account', 'loan', 'card', 'messageservice', 'gatewayserver'),

    # Docker Desktop Kubernetes 節點容器名稱。
    [string] $Node = 'desktop-control-plane',

    [string] $Prefix = 'anthonysk',
    [string] $Tag = '0.0.1-SNAPSHOT',

    [switch] $Force
)

# docker/kubectl 的錯誤需透過 $LASTEXITCODE 檢查。
$ErrorActionPreference = 'Continue'

# 記錄上次匯入的 image ID，未變更時跳過搬運。
$stateFile = Join-Path $PSScriptRoot '.image-load-state.json'
$tempDir = Join-Path $env:TEMP 'k8s-image-load'

function Write-Step { param([string]$m) Write-Host "  $m" -ForegroundColor DarkGray }
function Write-Ok { param([string]$m) Write-Host "  [ok]   $m" -ForegroundColor Green }
function Write-Skip { param([string]$m) Write-Host "  [skip] $m" -ForegroundColor DarkYellow }
function Write-Fail { param([string]$m) Write-Host "  [fail] $m" -ForegroundColor Red }

# ── 前置檢查 ──────────────────────────────────────────────────────────────
Write-Host "`n=== 前置檢查 ===" -ForegroundColor Cyan

docker version --format '{{.Server.Version}}' 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Fail "Docker 沒有在跑"
    exit 1
}

# Docker Desktop 可能隱藏 Kubernetes 系統容器，使用 inspect 而非 docker ps 檢查。
$nodeState = docker inspect -f '{{.State.Status}}' $Node 2>&1
if ($LASTEXITCODE -ne 0 -or $nodeState -ne 'running') {
    Write-Fail "節點容器 '$Node' 不在或沒在跑 —— Docker Desktop 的 Kubernetes 沒開？"
    Write-Step "開啟方式：Docker Desktop → Settings → Kubernetes → Enable Kubernetes"
    exit 1
}
Write-Ok "節點容器 $Node 在跑"

$ctx = kubectl config current-context 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Fail "kubectl 沒有可用的 context"
    exit 1
}
# 腳本會重啟 Deployment，因此只允許 docker-desktop context。
if ($ctx -ne 'docker-desktop') {
    Write-Fail "當前 kubectl context 是 '$ctx'，不是 docker-desktop —— 為安全起見中止"
    Write-Step "要切回來：kubectl config use-context docker-desktop"
    exit 1
}
Write-Ok "kubectl context = $ctx"

# ── 讀快取 ────────────────────────────────────────────────────────────────
$state = @{}
if ((Test-Path $stateFile) -and -not $Force) {
    try {
        (Get-Content $stateFile -Raw | ConvertFrom-Json).PSObject.Properties |
            ForEach-Object { $state[$_.Name] = $_.Value }
    }
    catch {
        Write-Step "快取檔讀不起來，當成全新開始"
    }
}

New-Item -ItemType Directory -Force $tempDir | Out-Null

# ── 主流程 ────────────────────────────────────────────────────────────────
$loaded = @()   # 真的搬進去的
$skipped = @()  # 沒變、跳過的
$failed = @()   # 失敗的

foreach ($svc in $Services) {
    $image = "$Prefix/${svc}:$Tag"
    Write-Host "`n=== $svc ===" -ForegroundColor Cyan

    # ① 確認本機 image 存在。
    $localId = docker image inspect $image --format '{{.Id}}' 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "本機沒有 $image —— 還沒 build？"
        $failed += $svc
        continue
    }
    $shortId = $localId.Substring(7, 12)

    # ② image 未變更則跳過。
    if (-not $Force -and $state[$svc] -eq $localId) {
        Write-Skip "$image ($shortId) 跟上次灌的相同"
        $skipped += $svc
        continue
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $tar = Join-Path $tempDir "$svc.tar"

    try {
        # ③ 匯出 image。
        Write-Step "docker save …"
        docker save $image -o $tar 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "docker save 失敗" }
        $mb = [math]::Round((Get-Item $tar).Length / 1MB)

        # ④ 複製到 Kubernetes 節點容器；暫存檔放在根目錄，避免 /tmp 掛載問題。
        $remoteTar = "/$svc.tar"
        Write-Step "docker cp ($mb MB) …"
        docker cp $tar "${Node}:$remoteTar" 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "docker cp 失敗" }

        # ⑤ 匯入 Kubernetes 使用的 containerd namespace。
        Write-Step "ctr images import …"
        $out = docker exec $Node ctr -n k8s.io images import $remoteTar 2>&1
        if ($LASTEXITCODE -ne 0) { throw "ctr import 失敗: $out" }

        # ⑥ 補上 kubelet 使用的完整 image 名稱（docker.io/...）。
        $canonical = "docker.io/$Prefix/${svc}:$Tag"
        docker exec $Node ctr -n k8s.io images tag $image $canonical 2>&1 | Out-Null

        # ⑦ 清除節點暫存檔。
        docker exec $Node rm -f $remoteTar 2>&1 | Out-Null

        # ⑧ 確認 containerd 已被 Kubernetes 看見。
        $seen = docker exec $Node crictl images 2>&1 | Select-String -Pattern "/$Prefix/$svc\s"
        if (-not $seen) { throw "匯入後 crictl 仍看不到 $svc —— 名稱可能沒對上" }

        $sw.Stop()
        Write-Ok "$image ($shortId, $mb MB) 已匯入 —— $([math]::Round($sw.Elapsed.TotalSeconds,1))s"
        $state[$svc] = $localId
        $loaded += $svc
    }
    catch {
        Write-Fail "$svc : $_"
        $failed += $svc
    }
    finally {
        Remove-Item $tar -Force -ErrorAction SilentlyContinue
    }
}

# ── 寫回快取 ──────────────────────────────────────────────────────────────
if ($loaded.Count -gt 0) {
    $state | ConvertTo-Json | Set-Content $stateFile -Encoding UTF8
}

# ── 摘要 ──────────────────────────────────────────────────────────────────
Write-Host "`n=== 摘要 ===" -ForegroundColor Cyan
Write-Host ("  已匯入 : " + $(if ($loaded) { $loaded -join ', ' } else { '（無）' }))
Write-Host ("  跳過   : " + $(if ($skipped) { $skipped -join ', ' } else { '（無）' }))
if ($failed) { Write-Host ("  失敗   : " + ($failed -join ', ')) -ForegroundColor Red }

Write-Host "`n=== Pod 現況 ===" -ForegroundColor Cyan
kubectl get pods

if ($failed) { exit 1 }

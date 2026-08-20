<#
.SYNOPSIS
    把本機 docker build 出來的 image 灌進 Docker Desktop Kubernetes 節點的 containerd。

.DESCRIPTION
    ── 為什麼需要這支腳本 ────────────────────────────────────────────────────
    有「兩個 image 倉庫」，而且互不相通：

        Windows 主機
        ├── Docker 的倉庫              ← docker build / jib / buildpacks 產出的在這
        └── desktop-control-plane      ← 這是一個容器，就是 K8s 的節點
              └── containerd 的倉庫    ← Kubernetes 只看這裡

    新版 Docker Desktop 的 Kubernetes 是 kind 架構，節點有自己的 containerd，
    「看不到」Docker 的 image。所以 imagePullPolicy: IfNotPresent 找不到東西時
    會跑去 Docker Hub 拉 —— 於是你在本機改的程式碼根本沒進到 cluster，
    跑的是「上次 push 到 registry 的版本」。

    這支腳本做的就是把 image 手動搬過去：save → cp → import。

    ⚠ 不要用 PowerShell 的 pipe 傳 tar（docker save … | docker exec -i …）——
      PowerShell 的 pipeline 會把二進位當文字處理，tar 檔會壞掉。
      一定要先落地成檔案再 docker cp，這也是本腳本的做法。

.PARAMETER Services
    要灌哪些服務。不指定就是全部七個。

.PARAMETER Force
    忽略「跟上次灌的是同一個 image」的快取判斷，強制重灌。

.PARAMETER NoRestart
    只灌 image，不要 kubectl rollout restart。
    ⚠ 不重啟的話既有的 Pod 還是跑舊 image —— 只有新建的 Pod 才會用到新灌進去的。

.EXAMPLE
    .\load-images.ps1 -Services loan
    只處理 loan（例如它卡在 ImagePullBackOff）。

.EXAMPLE
    .\load-images.ps1
    七個服務全部檢查一遍，只有 image 變過的才實際搬運。

.EXAMPLE
    .\load-images.ps1 -Services account,loan -Force
#>
[CmdletBinding()]
param(
    [ValidateSet('configserver', 'eurekaserver', 'account', 'loan', 'card', 'messageservice', 'gatewayserver')]
    [string[]] $Services = @('configserver', 'eurekaserver', 'account', 'loan', 'card', 'messageservice', 'gatewayserver'),

    # K8s 節點的容器名稱。docker ps 看得到它 —— 節點本身就是一個容器。
    [string] $Node = 'desktop-control-plane',

    [string] $Prefix = 'anthonysk',
    [string] $Tag = '0.0.1-SNAPSHOT',

    [switch] $Force,
    [switch] $NoRestart
)

# ⚠ native exe（docker / kubectl）的失敗不會觸發 PowerShell 的例外，
#   所以下面一律自己檢查 $LASTEXITCODE，不靠 $ErrorActionPreference。
$ErrorActionPreference = 'Continue'

# 記住「上次灌進去的是哪個 image ID」，下次沒變就跳過。
# 搬運成本很高（loan 有 1GB，save + cp 要十幾秒），這個快取讓重跑幾乎免費。
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

# ⚠⚠ 這裡「不能」用 docker ps 檢查 ⚠⚠
#   Docker Desktop 把 K8s 節點當「系統容器」隱藏起來，docker ps 完全看不到它
#   （除非在 Settings 裡打開 Show system containers）。
#   但 docker inspect / docker exec 用名字照樣找得到 —— 所以用 inspect 判斷。
#   踩過一次：用 docker ps --filter 判斷的話，明明 cluster 好好在跑，
#   腳本卻回報「Kubernetes 沒開」。
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
# ⚠ 這一行是防呆：腳本會 rollout restart，指到正式環境的 context 就慘了。
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

    # ① 本機有沒有這個 image
    $localId = docker image inspect $image --format '{{.Id}}' 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "本機沒有 $image —— 還沒 build？"
        $failed += $svc
        continue
    }
    $shortId = $localId.Substring(7, 12)

    # ② 跟上次灌的一樣就跳過
    if (-not $Force -and $state[$svc] -eq $localId) {
        Write-Skip "$image ($shortId) 跟上次灌的相同"
        $skipped += $svc
        continue
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $tar = Join-Path $tempDir "$svc.tar"

    try {
        # ③ 打包
        Write-Step "docker save …"
        docker save $image -o $tar 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "docker save 失敗" }
        $mb = [math]::Round((Get-Item $tar).Length / 1MB)

        # ④ 搬進節點容器（節點就是個容器，所以 docker cp 就行）
        #
        # ⚠⚠ 目標路徑「不能」用 /tmp ⚠⚠
        #   kind 的節點跑 systemd，開機後會把 /tmp 掛成 tmpfs。docker cp 寫的是
        #   容器 rootfs 上的 /tmp，位置在那個 tmpfs「底下」——容器裡的行程看不到。
        #   症狀：docker cp 回報成功，下一行 ctr 卻說
        #        ctr: open /tmp/loan.tar: no such file or directory
        #   所以放在根目錄（不是任何掛載點）最保險。
        $remoteTar = "/$svc.tar"
        Write-Step "docker cp ($mb MB) …"
        docker cp $tar "${Node}:$remoteTar" 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "docker cp 失敗" }

        # ⑤ 匯入 containerd
        # ⚠ -n k8s.io 不能省 —— containerd 有多個 namespace，Kubernetes 只認這個。
        #   匯到別的 namespace 會「成功但 K8s 看不到」，最難查的那種錯。
        Write-Step "ctr images import …"
        $out = docker exec $Node ctr -n k8s.io images import $remoteTar 2>&1
        if ($LASTEXITCODE -ne 0) { throw "ctr import 失敗: $out" }

        # ⑥ 確保「正規化後的名字」存在。
        # ⚠ docker save 的 tar 裡記的是 anthonysk/xxx:tag（沒有 registry 前綴），
        #   但 kubelet 找的是正規化後的 docker.io/anthonysk/xxx:tag。
        #   containerd 若照原樣登記，kubelet 就配不到 → 又跑去 Docker Hub 拉。
        #   多補一個 tag 是無害的保險（已存在時 ctr 會報錯，直接忽略）。
        $canonical = "docker.io/$Prefix/${svc}:$Tag"
        docker exec $Node ctr -n k8s.io images tag $image $canonical 2>&1 | Out-Null

        # ⑦ 清掉節點裡的暫存 tar（不刪會一直佔著節點的磁碟）
        docker exec $Node rm -f $remoteTar 2>&1 | Out-Null

        # ⑧ 驗證 K8s 真的看得到了（crictl 看的就是 kubelet 用的那份清單）
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

# ── 重啟有更新的 Deployment ───────────────────────────────────────────────
# 🔑 只重啟「真的換了 image」的，沒變的不動 —— 免得每次跑腳本都把整套服務洗一遍。
if ($loaded.Count -gt 0 -and -not $NoRestart) {
    Write-Host "`n=== 重啟 Deployment ===" -ForegroundColor Cyan
    foreach ($svc in $loaded) {
        $dep = "$svc-deployment"
        $r = kubectl rollout restart "deployment/$dep" 2>&1
        if ($LASTEXITCODE -eq 0) { Write-Ok $r } else { Write-Fail "$dep : $r" }
    }
}

# ── 摘要 ──────────────────────────────────────────────────────────────────
Write-Host "`n=== 摘要 ===" -ForegroundColor Cyan
Write-Host ("  已匯入 : " + $(if ($loaded) { $loaded -join ', ' } else { '（無）' }))
Write-Host ("  跳過   : " + $(if ($skipped) { $skipped -join ', ' } else { '（無）' }))
if ($failed) { Write-Host ("  失敗   : " + ($failed -join ', ')) -ForegroundColor Red }

Write-Host "`n=== Pod 現況 ===" -ForegroundColor Cyan
kubectl get pods

if ($failed) { exit 1 }

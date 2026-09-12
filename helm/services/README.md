# 微服務 Helm Charts

每個服務的 Chart 都管理自己的 ConfigMap、Deployment 與 Service。
目前微服務部署在 `helm-test` namespace；Alloy 是 Cluster 範圍的
DaemonSet，由獨立 Chart 部署在 `default` namespace。

## 建議部署方式

從專案根目錄執行：

```powershell
docker compose -f compose.k8s-infra.yml --profile observability up -d
.\build-images.ps1
.\kubernetes\import-local-images-to-k8s.ps1
.\helm\deploy-helm-isolated.ps1
```

腳本會依序部署：

```text
configserver → eurekaserver → account → loan → card → messageservice → gatewayserver
```

並以 `--wait` 等待每個 Helm Release 就緒。

## 手動部署

先建立 namespace 並套用共用 Secret：

```powershell
kubectl create namespace helm-test --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n helm-test -f .\kubernetes\config\secrets.yml
```

Alloy：

```powershell
helm upgrade --install alloy-k8s .\helm\observability\alloy --namespace default --wait --timeout 3m
```

微服務（使用各 Chart 內設定的 Service port 與固定 NodePort）：

```powershell
helm upgrade --install configserver .\helm\services\configserver --reset-values --namespace helm-test --wait --timeout 3m
helm upgrade --install eurekaserver .\helm\services\eurekaserver --reset-values --namespace helm-test --wait --timeout 3m
helm upgrade --install account .\helm\services\account --reset-values --namespace helm-test --wait --timeout 3m
helm upgrade --install loan .\helm\services\loan --reset-values --namespace helm-test --wait --timeout 3m
helm upgrade --install card .\helm\services\card --reset-values --namespace helm-test --wait --timeout 3m
helm upgrade --install messageservice .\helm\services\messageservice --reset-values --namespace helm-test --wait --timeout 3m
helm upgrade --install gatewayserver .\helm\services\gatewayserver --reset-values --namespace helm-test --wait --timeout 3m
```

`--reset-values` 會重新使用 Chart 目前的設定，包含固定 NodePort。
兩個 namespace 不能同時占用相同的 NodePort。

## 設定與除錯

- 共用機密來自 `kubernetes/config/secrets.yml`，以 `microservices-secrets` 注入 Pod。
- ConfigMap 由各 Chart 的 `values.yaml` 與 `templates/configmap.yaml` 建立，
  不會讀取 `kubernetes/config/configmap.yml`。
- 檢查 Release：`helm ls -A`。
- 檢查資源：`kubectl get pods -n helm-test`、`kubectl get services -n helm-test`。
- 查看日誌：`kubectl logs -n helm-test deployment/account-deployment`。
- Helm 已管理的服務不要再用 `kubectl apply -f kubernetes\\<service>.yml` 覆寫。

切換回 kubectl 部署前，先移除 `helm-test` 的 Helm Releases，釋放固定
NodePort；切換到 Helm 前，也要清理 `default` 中相同的 Service。

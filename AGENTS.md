# Repository Guidelines

## 專案結構與模組組織

本儲存庫由多個獨立的 Spring Boot 服務組成：`account`、`card`、`loan`、
`configserver`、`eurekaserver`、`gatewayserver` 與 `messageservice`。
各服務的 Java 程式碼位於 `src/main/java`，設定位於 `src/main/resources`，
測試位於 `src/test/java`。`microservices-bom` 集中管理 Spring Boot、Spring
Cloud、Java 25 與 Maven plugin 版本。執行環境相關設定位於 `compose*.yml`，
Kubernetes manifest 位於 `kubernetes/`，Helm charts 位於 `helm/`。

## 建置、測試與開發指令

請在對應的服務目錄中使用 PowerShell 執行指令：

```powershell
cd microservices-bom; .\mvnw.cmd install
cd ..\account; .\mvnw.cmd test
cd ..\gatewayserver; .\mvnw.cmd spring-boot:run
```

建置服務前先安裝 BOM。`test` 會執行編譯與自動化測試，
`spring-boot:run` 用於本機開發。若要從專案根目錄啟動容器化環境，請先確認
`.env` 定義了 `ENCRYPT_KEY` 等必要機密，再執行 `docker compose up -d`。
加上 `--profile observability` 可一併啟動監控服務。

## 程式碼風格與命名慣例

遵循現有 Java、XML、YAML 的四格縮排與套件結構（`com.example.<service>`）。
類別使用 PascalCase，方法與欄位使用 camelCase；模組、Docker service 與服務
發現識別名稱使用小寫。設定維持 YAML 格式，避免提交本機機密。目前沒有全域
formatter 或 linter，請使修改與鄰近程式碼保持一致。

## 測試指引

測試使用 Spring Boot test starter 與 JUnit 慣例。測試應放在 `src/test/java` 下
對應的套件，並命名為 `<Subject>Tests.java`。在修改過的服務中執行
`.\mvnw.cmd test`；若修改 controller、service、持久層或設定，請補充針對性
測試。目前沒有設定明確的覆蓋率門檻。

## Commit 與 Pull Request 指引

Commit subject 請使用簡短的祈使句，描述單一變更，例如 `update gateway
fallback` 或 `update helm`。Pull Request 應說明受影響的服務、設定或部署影響、
驗證指令及必要的環境變數。若修改 API 或觀測性行為，請附上 request/response
範例或截圖，並列出 reviewer 需要重現的 Kubernetes 或 Compose 手動步驟。

## 安全與設定注意事項

請將 `.env` 與 Kubernetes secret manifest 視為敏感檔案，絕不可把真實憑證
提交到 Git。Compose 服務透過 service DNS 互相通訊；正式 Compose 環境則透過
Gateway 對外提供業務 API。修改 Helm 時請遵循文件中的部署順序，避免使用 raw
manifest 覆寫由 Helm 管理的資源。

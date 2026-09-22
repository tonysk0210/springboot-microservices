# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

本專案的程式註解、腳本說明與設定檔說明皆為繁體中文，新增內容請沿用相同語言與註解密度。

## 專案概要

練習用的銀行微服務系統：Java 25 + Spring Boot 4.1.0 + Spring Cloud 2025.1.2。
七個可部署服務 + 一個純 BOM 模組，並提供三套執行環境（Docker Compose、kubectl manifests、Helm）。

| 模組 | Port | 說明 |
|---|---|---|
| `configserver` | 8071 | Config Server（composite：Git 優先、native 備援），`/monitor` 觸發 Bus refresh |
| `eurekaserver` | 8070 | 服務註冊中心 |
| `gatewayserver` | 8072 | Spring Cloud Gateway（WebFlux），對外單一入口 |
| `account` | 8080 | 帳戶／客戶，聚合查詢 loan 與 card（Feign） |
| `loan` | 8090 | 貸款 |
| `card` | 9000 | 信用卡 |
| `messageservice` | 9010 | Spring Cloud Function，處理 email/sms 通知 |
| `microservices-bom` | — | 共用 parent：Java 版本、Spring Cloud BOM、springdoc、Jib pluginManagement |

## 建置與測試

**沒有 root aggregator POM。** 每個服務是獨立 Maven 專案，各自帶 `mvnw.cmd`，必須進到服務目錄執行：

```powershell
cd account; .\mvnw.cmd clean package          # 建置單一服務
cd account; .\mvnw.cmd test                   # 全部測試
cd account; .\mvnw.cmd test "-Dtest=AccountApplicationTests"          # 單一測試類別
cd account; .\mvnw.cmd test "-Dtest=AccountApplicationTests#contextLoads"  # 單一測試方法
cd account; .\mvnw.cmd spring-boot:run
```

各服務以 `<relativePath>../microservices-bom/pom.xml</relativePath>` 繼承 BOM，**改 BOM 不需要 install**，但會同時影響全部服務。

目前只有 Spring Initializr 產生的 7 個 `*ApplicationTests` context smoke test；這些測試會嘗試連 Config Server／MySQL／RabbitMQ，離線時通常失敗，image 建置流程一律 skip test。

本機啟動（IntelliJ）前需要：`$env:ENCRYPT_KEY="<.env 裡的值>"`（Maven/IntelliJ 不會讀 `.env`，只有 Docker Compose 會）。

## Image 建置與推送

三個服務用三種不同建法，`build-images.ps1` 已封裝差異：

```powershell
.\build-images.ps1                      # 全部七個
.\build-images.ps1 -Services account     # 指定服務
.\build-images.ps1 -WhatIf               # 只印指令
.\push-images-to-ghcr.ps1                # tag + push 到 ghcr.io（需先 docker login ghcr.io）
```

- Jib（`configserver`、`eurekaserver`、`card`、`messageservice`、`gatewayserver`）：`.\mvnw.cmd compile jib:dockerBuild`，`compile` 不能省。base image 寫成 `docker://eclipse-temurin:25-jre-alpine`，需本機先有該 image。
- Dockerfile（`account`）：兩階段建置，**build context 必須是專案根目錄**（要複製 `microservices-bom/`）：`docker build -f account/Dockerfile -t anthonysk/account:0.0.1-SNAPSHOT .`
- Buildpacks（`loan`）：`.\mvnw.cmd spring-boot:build-image "-Dmaven.test.skip=true"`。產出的 image 沒有 wget/curl，healthcheck 改用 bash `/dev/tcp`。

本機 image namespace 固定 `anthonysk/<service>:<pom version>`；tag 直接取自 pom 的 `<version>`。

## 執行環境

### Docker Compose（主要開發環境）

```powershell
docker compose up -d                                        # 核心服務
docker compose --profile observability up -d                # 加上 Loki/Alloy/Prometheus/Tempo/Grafana
docker compose -f compose.yml -f compose.prod.yml up -d      # 正式環境覆寫
docker compose up -d --force-recreate account                # 重建單一容器
```

- 需要專案根目錄的 `.env`（`ENCRYPT_KEY`、MySQL/RabbitMQ/Keycloak 帳密）；缺值的變數會直接讓 compose 失敗。
- `common.yml` 是 `extends` 的設定範本階層：`resource-limits` → `spring-bus-base` → `microservice-base` → `database-microservice-base`，容器專屬設定（healthcheck、port、DB URL）留在 `compose.yml`。
- 容器環境的 Gateway 固定 `SPRING_PROFILES_ACTIVE=auth`，呼叫 API 必須帶 Keycloak token；本機不帶 profile 時走 `NoAuthSecurityConfig`。
- 容器把 `CONFIGSERVER_OPTIONAL_PREFIX` 設成空字串，Config Server 變成硬性依賴（本機預設 `optional:`）。
- 每個服務都有固定 `container_name`，因此不能用 `--scale`。

### Kubernetes（kubectl manifests）

```powershell
docker compose -f compose.k8s-infra.yml --profile observability up -d   # 外部依賴仍跑在 Compose
.\build-images.ps1
.\kubernetes\import-local-images-to-k8s.ps1     # Docker → containerd（兩者 image store 不同）
.\kubernetes\deploy-in-order.ps1
.\kubernetes\restart-deployments.ps1
```

`kubernetes/config/` 放 ConfigMap 與 Secret；`kubernetes/.image-load-state.json` 是匯入快取（`-Force` 可忽略）。

### Helm

```powershell
.\helm\deploy-helm-in-order.ps1                          # 預設 namespace: helm-test
.\helm\deploy-helm-in-order.ps1 -Namespace helm-test-2
```

微服務 Chart 在 `helm/services/`（部署到 `helm-test`），Alloy 與 Spring Cloud Kubernetes Discovery Server 屬基礎設施，部署在 `default`。NodePort 是固定值，切 namespace 前要先停掉另一組。

## 架構重點

### 設定管理（最容易踩坑）

Config Server 使用 **composite backend，Git 優先**，指向本 repo 的 `configyml/` 目錄：

- 改了 `configyml/*.yml` **必須 commit + push**，否則 `http://localhost:8071/account/default` 拿到的還是舊版。
- `configserver/src/main/resources/config/` 是 native 備援（目前不生效），別改錯檔案。驗證方式：看回應的 `propertySources[].name` 是 GitHub URL 還是 `classpath:/config/`。
- 敏感值以 `'{cipher}...'`（單引號不可省）寫入，由 `ENCRYPT_KEY` 解密；換金鑰會讓所有既有密文失效。
- 刷新鏈路：Git push → `/monitor` → Spring Cloud Bus（RabbitMQ）→ 各服務 refresh。本機模擬：`.\refresh-configyml.ps1`（`-Service account` / `-All`）；真正的 GitHub webhook 走 `.\hookdeck-listen.ps1` 建立 tunnel。
- Config Server 自己不處理 refresh 事件（`spring.cloud.bus.refresh.enabled=false`），也不開放 `busrefresh`。

### 兩條服務發現路徑（刻意並存）

Account 對 loan/card 同時保留兩組 Feign Client，用來對照 Eureka 與 Kubernetes Service DNS：

- `LoanFeignClient` / `CardFeignClient`：`@FeignClient(name="loan")` → Eureka + LoadBalancer，對應 `/api/fetch-customerAccLoanCardDetail-eureka`。
- `KubernetesLoanFeignClient` / `KubernetesCardFeignClient`：固定 `url = ${downstream.loan.base-url}` → Service DNS，對應 `/api/fetch-customerAccLoanCardDetail-k8s`。兩者必須用不同的 `contextId`。

Gateway 端同樣成對：`/bank/{account,loan,card}/**` 走 `lb://`（Eureka），`/k8s/account/**` 走 `downstream.account.base-url`（Service DNS），以回應 header `X-Gateway-Discovery-Mode` 區分。

### 韌性設定的層次

逾時必須由內到外遞增，改任一層都要一起檢查：**Feign 約 3s（connect 1s + read 2s）→ Gateway `response-timeout` 7s → Resilience4j `timelimiter` 15s**。

- Account 停用了 CircuitBreaker 的 Feign 執行緒池（`spring.cloud.circuitbreaker.resilience4j.disable-thread-pool=true`），目的是保住 MDC 裡的 correlation-id；代價是 TimeLimiter 無法中斷同步 Feign，等待時間改由 Feign timeout 控制。
- 每個路由的容錯機制不同：account 路由用 Circuit Breaker + `forward:/contactSupport`，loan 路由用 GET retry，card 路由用 Redis `RequestRateLimiter`（依 `user` header 分桶）。
- Account 服務內另有 Resilience4j `@RateLimiter`（每 instance 記憶體計數，5 秒 1 次）。測試腳本：`.\test-ratelimit-gateway.ps1`、`.\test-ratelimit-service.ps1`。

### 訊息

Account 與 MessageService 同時掛 RabbitMQ 與 Kafka 兩個 binder，因此 `spring.cloud.stream.defaultBinder: rabbit1` 不能拿掉——Spring Cloud Bus 固定走 RabbitMQ，業務事件另有 Kafka 流程（`kafka-send-communication` ↔ `kafka-communication-sent`）。Kafka 位址本機用 `localhost:29092`，容器內用 `kafka:9092`。

### 觀測性

log pattern 內含 `X-Gateway-Correlation-Id`、`traceId`、`spanId`（從 MDC 取）。Gateway 是 WebFlux，必須保留 `spring.reactor.context-propagation: auto`，否則 log 的 traceId 永遠是空的（Servlet 服務不需要）。指標由 Prometheus 抓 `/actuator/prometheus`，trace 走 OTLP 送 Tempo（`OTLP_TRACING_ENDPOINT`），log 由 Alloy 收進 Loki，全部在 Grafana 查。OTel metrics export 已關閉。

### 資料層

account/loan/card 共用同一個 MySQL 容器、不同 database（`accountdb`/`loandb`/`carddb`，由 `mysql/init/01-create-databases.sql` 建立）。各服務啟動時以 `sql/schema.sql` 建表，JPA 為 `ddl-auto: validate`——**改 Entity 一定要同步改 schema.sql**，否則啟動即失敗。

## 其他慣例

- 分層固定為 `controller → service(I*/`*ServiceImpl`) → repository`，DTO 與 Entity 以靜態 `*Mapper` 轉換，例外統一由 `GlobalExceptionHandler` 處理，各服務有自己的 `CorrelationIdFilter`。
- Java 23+ 需顯式宣告 Lombok 的 `annotationProcessorPaths`，新模組的 `maven-compiler-plugin` 要沿用既有寫法。
- Actuator 開了 `env`（`show-values: ALWAYS`）與 `shutdown`（`access: unrestricted`）方便練習，屬刻意的不安全設定，僅限本機。

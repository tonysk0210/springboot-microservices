# =============================================================================
#  這是什麼？
#
#    Dockerfile = 一份「步驟說明書」（像食譜）
#    docker build → 產生 image（做好的便當，可複製無限份）
#    docker run   → 產生 container（真的拿出來吃的那一份）
#
#  Docker 從上到下一行一行照做，每做完一行存一次檔（這叫「一層」）。
# =============================================================================
#
#  檔案結構怎麼演變（數字為實測值）
#
#  ┌─ 第一段：廚房  FROM ...25-jdk-alpine AS builder ───────────────────────────┐
#  │
#  │ WORKDIR /build            /build/                    ← 空的
#  │
#  │ COPY .mvn/ mvnw pom.xml     ├── .mvn/  mvnw  pom.xml
#  │
#  │ RUN dependency:go-offline （164 個依賴下載到 /root/.m2，193 MB
#  │                             —— 不在 /build 底下）
#  │ COPY src/                   ├── src/                 ← 你寫的 .java
#  │
#  │ RUN clean package           ├── target/
#  │                             │    └── account-0.0.1-SNAPSHOT.jar  99 MB
#  │                             │       （程式碼 + 164 個函式庫，fat jar）
#  │ RUN jarmode extract         └── extracted/
#  │                                  ├── dependencies/lib/    99 MB / 164 個
#  │                                  ├── spring-boot-loader/
#  │                                  ├── snapshot-dependencies/
#  │                                  └── application/app.jar  ~45 KB
#  │
#  │ 每一步都是「疊上去」，前面的檔案不會消失，會一直累積。
#  │ 這一段最後有 1.17 GB。
#  │
#  └────────────────────────────────────────────────────────────────────────────┘
#                    │
#                    │  只有 extracted/ 被帶走，其餘（JDK、Maven、.m2、
#                    ↓  src、target）全部丟掉
#  ┌─ 第二段：便當盒  FROM ...25-jre-alpine ────────────────────────────────────┐
#  │
#  │ WORKDIR /app              /app/
#  │ COPY --from=builder x4      ├── lib/         164 個 jar
#  │                             └── app.jar      你的程式
#  │
#  │ 成品 501 MB（基底 jre-alpine 就佔 305 MB）
#  │
#  └───────────────────────────────────────────────────────────────────────────┘
#
#  建置：cd account && docker build -t anthonysk/account:0.0.1-SNAPSHOT .
#  執行：docker compose up -d account（設定見專案根目錄的 compose.yml）
# =============================================================================


# =============================================================================
#  第一段：廚房 —— 把原始碼變成 jar
# =============================================================================
# AS builder 是幫這間廚房取名，第二段才能說「去 builder 那裡拿東西」
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build

# 先只搬「下載依賴需要的三樣東西」，故意不搬程式碼。
# 🔑 這樣拆是為了快取：pom.xml 沒變 → 下一行整個跳過（build log 顯示 CACHED）。
#    跟程式碼一起搬的話，改一行 Controller 就要重載 193 MB 的依賴。
#
# ⚠ 目的地要寫 .mvn/ 不能寫 ./
#   來源是資料夾時，COPY 複製的是「裡面的內容」而不是資料夾本身。
#   寫 ./ 的話 wrapper/ 會直接倒進 /build/，.mvn 那層消失，mvnw 就找不到設定。
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# chmod 是因為 Windows 的檔案搬到 Linux 沒有執行權限
# -B 關掉進度條那種動態字元，log 才乾淨
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# -DskipTests：測試該由 CI 負責，不然每次做便當都要試吃一輪
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# 把 fat jar 拆成「依賴」和「自己的程式碼」兩堆。
# 🔑 Docker 是一層一層存的。99 MB 全擠在一層的話，改一行程式碼整包都要重做、
#    重新上傳；拆開之後只有 45 KB 那層會變。
#    → 分層真正省的是「傳輸量」，不是建置時間（編譯還是得跑）。
#
# 最後的 mv 把 account-0.0.1-SNAPSHOT.jar 改名成 app.jar，
# 這樣 ENTRYPOINT 可以寫死檔名，升版號時不用改 Dockerfile。
RUN java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted \
    && mv extracted/application/*.jar extracted/application/app.jar


# =============================================================================
#  第二段：便當盒 —— 只裝跑得起來所需的最少東西
# =============================================================================
# ⚠ 這一行是整份檔案「唯一真正清空」的地方 —— 廚房整個消失，從這裡開始是
#   全新的空環境，只有下面 COPY --from=builder 帶過來的檔案才存在。
#   jre 能執行但不能編譯，體積比 jdk 小。
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# 建立一個普通員工帳號，不要用管理員（root）。
# 容器萬一被入侵，對方拿到的是受限帳號。
RUN addgroup -S app && adduser -S app -G app
USER app

# 從那間即將被拆掉的廚房裡，把成品端出來。
# ⚠ 這四行的順序是刻意的：從「最不會變」排到「最常變」。
#   第 1 行是 164 個依賴（幾乎不變，快取幾乎永遠有效），第 4 行是你的程式碼
#   （每次都變）。順序反過來的話，前面一變動，後面全部的快取都失效。
# ⚠ --chown 不能省，否則檔案屬於 root，上面那個 app 使用者可能讀不到。
COPY --from=builder --chown=app:app /build/extracted/dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/application/ ./

# 在盒子上貼張紙條：這個服務用 8080 號門。
# ⚠ 只是紙條，不會真的開門。要從外面連進來得靠 -p 8080:8080 或 compose 的 ports:
EXPOSE 8080

# 預設值，可被 compose 的 environment: 或 docker run -e 覆蓋。
# 用記憶體資料庫是刻意的 —— 容器裡的檔案關掉就沒了，用 mem 語意才清楚，
# 不會讓人誤以為資料有存起來、重建容器後才發現全消失。
ENV SPRING_DATASOURCE_URL=jdbc:h2:mem:accountdb

# 純 metadata。docker inspect、資安掃描工具（Trivy/Grype）、SBOM 工具會讀。
# ⚠ 刻意放最後：LABEL 會產生一個 0 B 的層，放前面會讓後面所有層的快取失效。
# ⚠ 這裡的 LABEL「不足以」讓 GitHub Packages 顯示描述 —— 見檔案最後那段。
LABEL org.opencontainers.image.title="account-service" \
      org.opencontainers.image.description="帳戶與客戶資料的 REST API" \
      org.opencontainers.image.authors="Tony Shangkuan" \
      org.opencontainers.image.source="https://github.com/tonysk0210/springboot-microservices"

# 便當打開時要執行的動作 —— 上面全是「準備」，只有這一行是 docker run 時才跑的。
# app.jar 裡記載著 Class-Path: lib/...，Java 會自動去 /app/lib/ 找那些依賴。
ENTRYPOINT ["java", "-jar", "app.jar"]


# =============================================================================
#  推到 ghcr.io —— ⚠ 這是「當初建立 package 時」用的做法，現在日常推送用
#  專案根目錄的 push-images-to-ghcr.ps1（docker tag + docker push）。
#  只有「刪掉 package 重建」時才需要回來用這一串。
# =============================================================================
#
#  docker login ghcr.io -u tonysk0210     # 密碼欄貼 classic token（勾 write:packages）
#
#  docker buildx build --provenance=false --sbom=false `
#    --annotation "org.opencontainers.image.description=帳戶與客戶資料的 REST API" `
#    --annotation "org.opencontainers.image.source=https://github.com/tonysk0210/springboot-microservices" `
#    --annotation "org.opencontainers.image.title=account-service" `
#    --annotation "org.opencontainers.image.authors=Tony Shangkuan" `
#    --push -t ghcr.io/tonysk0210/account:0.0.1-SNAPSHOT .
#
#  ---------------------------------------------------------------------------
#  🔑 為什麼 LABEL 不夠，還要 --annotation？
#     兩者寫在 image 的不同位置：LABEL 進 image config，annotation 進 manifest。
#     GitHub 對 OCI manifest「只讀 annotation，不會退回去讀 label」。
#     （loan 用 Buildpacks、card 用 Jib，產出的是舊的 Docker v2 manifest，
#       該格式不支援 annotation，GitHub 才會退回讀 label —— 所以只設 label 就夠。）
#
#  ⚠ --provenance=false --sbom=false 不能省：buildx 預設會多附一份建置履歷，
#    讓 tag 指向一個 index，GitHub 看到 index 就讀不到 metadata。
#
#  ⚠ 一定要 --push，不能先 build 再 push —— annotation 在 --load 到本機 Docker
#    的過程中會遺失。副作用是 image 不會留在本機，要另外 build 一次或 pull 回來。
#
#  ⚠ package 的描述與 repo 連結在「首次建立時」就定型，補推同一個 tag 不會更新。
#    要改就得先到 GitHub 刪掉整個 package 再重推。
#
#  ⚠ ghcr.io 推上去預設是 Private。要公開：
#    Package settings → Danger Zone → Change visibility → Public
# =============================================================================

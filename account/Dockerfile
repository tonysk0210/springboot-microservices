# Dockerfile 是建立 image 的步驟說明。
# 使用兩階段建置：第一階段編譯，第二階段只保留執行所需檔案。
# 建置：docker build -t anthonysk/account:0.0.1-SNAPSHOT .
# 若要推送 Docker Hub，anthonysk 必須是你的 Docker Hub username，或是你有權限的 namespace。
# 執行：docker compose up -d account

# 第一階段：使用 JDK 和 Maven 編譯應用程式。
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build

# 先複製 Maven 設定，讓依賴下載層可以重複使用快取。
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Windows 檔案在 Linux 中可能沒有執行權限，因此先 chmod。
# -B 讓 Maven log 保持非互動格式。
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# 建置 image 時略過測試；測試可交由 CI 執行。
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# 將 jar 拆成依賴與應用程式層，減少程式修改時的 image 傳輸量。
# 統一命名為 app.jar，讓版本更新時不必修改 ENTRYPOINT。
RUN java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted \
    && mv extracted/application/*.jar extracted/application/app.jar

# 第二階段：只使用 JRE 執行應用程式，不帶入 JDK、Maven 和原始碼。
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# 使用非 root 帳號執行，降低容器遭入侵時的權限。
RUN addgroup -S app && adduser -S app -G app
USER app

# 依固定到常變的順序複製檔案，最大化 Docker 快取效果。
# --chown 確保 app 使用者可以讀取這些檔案。
COPY --from=builder --chown=app:app /build/extracted/dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/application/ ./

# 宣告應用程式在容器內監聽 8080；實際對外連線仍需 ports 或 -p 設定。
EXPOSE 8080

# 預設連到同一 Docker network 中 DNS 名為 mysql 的資料庫；可由 compose 或 -e 覆蓋。
ENV SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/accountdb

# image 的基本描述資訊，供 docker inspect 和掃描工具使用。
LABEL org.opencontainers.image.title="account-service" \
      org.opencontainers.image.description="帳戶與客戶資料的 REST API" \
      org.opencontainers.image.authors="Tony Shangkuan" \
      org.opencontainers.image.source="https://github.com/tonysk0210/springboot-microservices"

# 容器啟動時執行 Spring Boot 應用程式。
ENTRYPOINT ["java", "-jar", "app.jar"]

# 推送 GHCR 時，日常請使用專案根目錄的 push-images-to-ghcr.ps1。

# 以下指令僅供刪除 package 後重新建立時使用。
# docker login ghcr.io -u tonysk0210
# docker buildx build --provenance=false --sbom=false `
#   --annotation "org.opencontainers.image.description=帳戶與客戶資料的 REST API" `
#   --annotation "org.opencontainers.image.source=https://github.com/tonysk0210/springboot-microservices" `
#   --annotation "org.opencontainers.image.title=account-service" `
#   --annotation "org.opencontainers.image.authors=Tony Shangkuan" `
#   --push -t ghcr.io/tonysk0210/account:0.0.1-SNAPSHOT .
#
# LABEL 是 image 內的 metadata；--annotation 是 manifest 的 metadata。
# GHCR 的 package 描述可能需要在首次建立時透過 annotation 設定。

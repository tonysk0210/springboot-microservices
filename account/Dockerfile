# =========================================================================
# 多階段建置（multi-stage build）
#
#   第一階段 builder：像一間「廚房」—— 有完整工具，負責把原始碼做成 jar
#   第二階段        ：像一個「便當盒」—— 只裝成品，廚具不帶走
#
# 第一階段的內容最後會全部丟棄，只有明確 COPY --from=builder 挑出來的檔案
# 才會進入最終 image。這就是 image 從 703 MB 降到 422 MB 的原因。
# =========================================================================


# =========================================================================
# 第一階段：建置（含 JDK 與 Maven）
# =========================================================================

# 以 Java 25 的 JDK（Alpine 精簡版）為起點。
# AS builder 是替這個階段命名，第二階段才能用 --from=builder 指名取用。
FROM eclipse-temurin:25-jdk-alpine AS builder

# 建立並切換到 /build，之後的指令都在這裡執行（等同 cd /build）
WORKDIR /build

# --- 步驟 1：先只複製建置設定，「不要」複製原始碼 ---------------------------
# Docker 會把每個指令的結果存成一層快取。把依賴下載與原始碼分開，
# 只要 pom.xml 沒變，下一行那個很慢的下載步驟就能直接重用快取。
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# chmod +x：Windows 複製進 Linux 的檔案預設沒有執行權限，要補上
# -B：batch 模式，不輸出進度條那類動態字元，log 才乾淨
# dependency:go-offline：只下載 93 個依賴，不編譯
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# --- 步驟 2：複製原始碼並打包 ----------------------------------------------
# -DskipTests：跳過測試。測試應該由 CI 負責，不該每次建 image 都跑一次
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# --- 步驟 3：把 fat jar 拆成分層 -------------------------------------------
# 62 MB 的 fat jar 拆開後變成：
#     extracted/dependencies/lib/   → 93 個依賴 jar（62 MB，很少變動）
#     extracted/application/*.jar   → 你的程式碼（45 KB，每次都變動）
# 拆開的目的是讓「常變的」與「不常變的」分屬不同 Docker 層，
# 改一行程式碼時只需重建 45 KB，而不是整包 62 MB。
#
# 最後的 mv 把檔名改成 app.jar，讓下方 ENTRYPOINT 可以寫死檔名
# （版本號升級時不必改 Dockerfile；manifest 的 Class-Path 是相對路徑，改名不受影響）
RUN java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted \
    && mv extracted/application/*.jar extracted/application/app.jar


# =========================================================================
# 第二階段：執行（只留 JRE，不含 JDK 與 Maven）
# =========================================================================

# 重新開始，這次用 JRE —— 只能執行、不能編譯。
# 第一階段的 JDK、Maven、原始碼、target 目錄通通不會進來。
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# 建立一般使用者並切換身份，避免以 root 執行容器。
# 容器一旦被入侵，攻擊者拿到的是受限帳號而非最高權限。
RUN addgroup -S app && adduser -S app -G app
USER app

# 從第一階段挑檔案過來（--from=builder 表示來源是上一階段，不是你的電腦）。
# --chown=app:app：把擁有者設成 app，否則檔案屬於 root，app 使用者可能讀不到。
#
# ⚠ 順序有意義：由「最不常變」排到「最常變」。
#   改程式碼重建時，前三行的快取都能重用，只有最後 45 KB 要重新建。
#
# 複製後的結果：/app/lib/*.jar（依賴）與 /app/app.jar（程式碼）
COPY --from=builder --chown=app:app /build/extracted/dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/application/ ./

# ⚠ EXPOSE 只是文件說明，宣告「這個服務用 8080」，它「不會」真的開放 port。
#   實際對外必須靠 docker run -p 18080:8080
EXPOSE 8080

# 預設值，搭配 application.yaml 的 ${SPRING_DATASOURCE_URL:...} 使用。
# 這裡刻意用記憶體資料庫，語意明確表示「容器內的資料不持久」，
# 避免資料悄悄寫進 /home/app/h2db/ 然後隨著容器重建而消失。
#
# 部署時務必覆寫成外部資料庫：
#   docker run -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/accountdb \
#              -e SPRING_DATASOURCE_USERNAME=app \
#              -e SPRING_DATASOURCE_PASSWORD=secret ...
ENV SPRING_DATASOURCE_URL=jdbc:h2:mem:accountdb

# 容器啟動時執行的指令。
# 因為 WORKDIR 是 /app 且 app.jar 的 manifest 寫著 Class-Path: lib/...，
# Java 會自動到 /app/lib/ 找那 93 個依賴。
ENTRYPOINT ["java", "-jar", "app.jar"]

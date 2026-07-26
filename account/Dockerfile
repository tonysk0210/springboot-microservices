# =============================================================================
#  這是什麼？
#
#    Dockerfile = 一份「步驟說明書」（像食譜）
#    docker build 之後 → 產生 image（做好的便當，可複製無限份）
#    docker run   之後 → 產生 container（真的拿出來吃的那一份）
#
#  Docker 會從上到下、一行一行照做，每做完一行就存一次檔（這叫「一層」）。
#
#  ---------------------------------------------------------------------------
#  為什麼下面有「兩個 FROM」？因為分兩段做：
#
#    第一段 = 廚房    有瓦斯爐、鍋子、菜刀，負責把生食材煮成菜
#    第二段 = 便當盒  只裝煮好的菜，鍋子菜刀通通不帶走
#
#  第一段的東西最後「整個丟掉」，只有明確說要帶走的檔案才會進便當盒。
#  實測：廚房 934 MB（建完就丟）→ 便當盒 422 MB（這才是成品）
# =============================================================================


# =============================================================================
#  第一段：廚房 —— 把原始碼變成 jar
# =============================================================================

# 「先給我一間裝好 Java 25 開發工具的乾淨 Linux 電腦」
#   eclipse-temurin:25-jdk-alpine → 別人做好的現成 image
#   jdk    = 開發版（能編譯，也能執行）
#   alpine = 超精簡的 Linux（只留必要的東西，體積小）
#   AS builder = 幫這間廚房取名叫 builder，第二段才能說「去 builder 那裡拿東西」
FROM eclipse-temurin:25-jdk-alpine AS builder

# 「在這台電腦上開一個 /build 資料夾，然後走進去」
# 之後所有動作都在這個資料夾裡發生（等同 cd /build）
#
#   容器現在：/build/        ← 空的
WORKDIR /build


# --- 步驟 1：先搬「設定檔」，故意不搬程式碼 -----------------------------------
#
# 這三樣東西就是「下載函式庫」所需的全部：
#   mvnw                                → Maven 啟動腳本
#   .mvn/wrapper/maven-wrapper.properties → 記載要用哪個 Maven 版本（3.9.16）
#   pom.xml                             → 要下載哪些函式庫的清單
#
#   容器現在：/build/
#             ├── .mvn/wrapper/maven-wrapper.properties
#             ├── mvnw
#             └── pom.xml
#
# ⚠ 注意目的地要寫 .mvn/ 而不是 ./
#   因為來源是「資料夾」時，COPY 複製的是「裡面的內容」，不是資料夾本身。
#   寫成 ./ 的話 wrapper/ 會被倒進 /build/，.mvn 那層消失，mvnw 就找不到設定了。
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# 「把 93 個函式庫（Spring、Hibernate、H2…）全部下載下來」—— 這步最慢，約 62 MB
#
#   chmod +x mvnw          → 給執行權限（Windows 的檔案搬到 Linux 會沒有）
#   -B                     → 不要輸出進度條那種動態字元，log 才乾淨
#   dependency:go-offline  → 只下載，先不編譯
#
# 🔑 為什麼要跟程式碼分兩步做？
#    Docker 的規則是：某一步用到的檔案沒變，就直接拿上次的結果，不重做。
#      你改了一行 Controller → pom.xml 沒變 → 這步跳過，不重新下載 ✅
#      若程式碼跟 pom.xml 一起搬 → 每次都要重載 62 MB ❌
#    重建時看到的 CACHED 就是這個機制在生效。
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline


# --- 步驟 2：現在才搬程式碼，然後開始煮 ---------------------------------------
#
#   容器現在：/build/
#             ├── .mvn/  mvnw  pom.xml
#             └── src/                   ← 你寫的 .java 檔
COPY src/ src/

# 「把 .java 編譯成 .class，再打包成一個 jar」
#   -DskipTests → 跳過測試。測試該由 CI 負責，不然每次做便當都要試吃一輪，太慢
#
#   容器現在：/build/target/account-0.0.1-SNAPSHOT.jar
#             （62 MB，你的程式碼 + 93 個函式庫全包在裡面，稱為 fat jar）
RUN ./mvnw -B clean package -DskipTests


# --- 步驟 3：把 62 MB 的大包裹拆成兩堆 ----------------------------------------
#
#   容器現在：/build/extracted/
#             ├── dependencies/lib/     ← 93 個函式庫（62 MB，幾乎不會變）
#             └── application/app.jar   ← 你的程式碼（45 KB，每次都變）
#
# 🔑 為什麼要拆？
#    Docker 是一層一層存的。62 MB 若全擠在同一層：
#      改一行程式碼 → 整包 62 MB 都要重做、重新上傳 ❌
#      拆開之後     → 只有 45 KB 那層要重做           ✅
#
# 最後的 mv 是把檔名 account-0.0.1-SNAPSHOT.jar 改成 app.jar，
# 這樣最下面的 ENTRYPOINT 就能寫死名字，以後版本號升級也不必改 Dockerfile。
RUN java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted \
    && mv extracted/application/*.jar extracted/application/app.jar


# =============================================================================
#  第二段：便當盒 —— 只裝跑得起來所需的最少東西
# =============================================================================

# 「重新拿一個乾淨的盒子，這次只要 JRE」
#
#            能編譯 Java   能執行 Java   體積
#    jdk         ✅            ✅        大
#    jre         ❌            ✅        小      ← 程式跑起來只需要「執行」
#
# ⚠ 這一行一下，上面那間廚房就整個消失了 —— JDK、Maven、原始碼、target 都不見。
FROM eclipse-temurin:25-jre-alpine

# 便當盒裡開一個 /app 資料夾並走進去
WORKDIR /app

# 「建立一個普通員工帳號，之後都用他的身份做事，不要用管理員（root）」
# 容器萬一被入侵，對方拿到的是受限帳號，而不是最高權限。
RUN addgroup -S app && adduser -S app -G app
USER app

# 「從那間即將被拆掉的廚房裡，把成品端出來」
#
#   --from=builder    → 來源不是你的電腦，是第一段那間廚房
#   --chown=app:app   → 把檔案擁有者設成剛建的員工帳號
#                       （不然檔案屬於 root，員工可能讀不到）
#
# ⚠ 這四行的順序是刻意的：從「最不會變」排到「最常變」
#     第 1 行 93 個函式庫（幾乎不變）→ 這層的快取幾乎永遠有效
#     第 4 行 你的程式碼（每次都變）  → 只有這層要重做
#   順序反過來的話，前面一變動，後面全部的快取都會失效。
#
#   容器現在：/app/
#             ├── lib/        ← 93 個 jar
#             └── app.jar     ← 你的程式
COPY --from=builder --chown=app:app /build/extracted/dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/application/ ./

# 「在盒子上貼張紙條：這個服務用 8080 號門」
#
# ⚠ 它「只是紙條，不會真的開門」。要能從外面連進來，啟動時得自己指定：
#     docker run -p 18080:8080 ...
#                   ↑外面  ↑裡面
EXPOSE 8080

# 資料庫連線的「預設值」，搭配 application.yaml 裡的 ${SPRING_DATASOURCE_URL:...}
#   有這個環境變數 → 用它；沒有 → 才用 yaml 裡寫的
#
# 為什麼預設用記憶體資料庫？因為容器裡的檔案「關掉就沒了」。
# 用 mem 至少語意清楚（一看就知道是暫時的），
# 不會讓人誤以為資料有存起來、結果容器重建後才發現全消失。
#
# 真正部署時要覆蓋掉，指向外部資料庫：
#   docker run -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/accountdb \
#              -e SPRING_DATASOURCE_USERNAME=app \
#              -e SPRING_DATASOURCE_PASSWORD=secret ...
ENV SPRING_DATASOURCE_URL=jdbc:h2:mem:accountdb

# 「便當打開時要執行的動作」
#
# 上面所有步驟都是「準備」，只有這一行是 docker run 時才執行的。
# 因為 WORKDIR 是 /app，所以 app.jar 就是 /app/app.jar；
# 而 app.jar 裡記載著 Class-Path: lib/...，Java 會自動去旁邊的 /app/lib/ 找那 93 個函式庫。
ENTRYPOINT ["java", "-jar", "app.jar"]


# =============================================================================
#  附錄：實測「改一行程式碼後重建」哪些步驟會被重跑
#         （指令：docker build --progress=plain -t account:0.0.1-SNAPSHOT .）
#
#  ┌──────────────────────────────────────┬──────────────────────────────────┐
#  │ 第一階段（廚房）                       │                                  │
#  ├──────────────────────────────────────┼──────────────────────────────────┤
#  │ WORKDIR /build                       │ ✅ CACHED                        │
#  │ COPY .mvn/ .mvn/                     │ ✅ CACHED                        │
#  │ COPY mvnw pom.xml ./                 │ ✅ CACHED                        │
#  │ RUN ./mvnw dependency:go-offline     │ ✅ CACHED（不用重載 93 個依賴）    │
#  │ COPY src/ src/                       │ ❌ 重跑（原始碼變了）              │
#  │ RUN ./mvnw clean package             │ ❌ 重跑（重新編譯打包，最慢一步）    │
#  │ RUN java -Djarmode=tools ... extract │ ❌ 重跑                          │
#  ├──────────────────────────────────────┼──────────────────────────────────┤
#  │ 第二階段（便當盒）                      │                                  │
#  ├──────────────────────────────────────┼──────────────────────────────────┤
#  │ WORKDIR /app                         │ ✅ CACHED                        │
#  │ RUN addgroup && adduser              │ ✅ CACHED                        │
#  │ COPY dependencies/                   │ ✅ CACHED ← 62 MB 沒動            │
#  │ COPY spring-boot-loader/             │ ✅ CACHED                        │
#  │ COPY snapshot-dependencies/          │ ✅ CACHED                        │
#  │ COPY application/                    │ ❌ 重建（45 KB）                  │
#  └──────────────────────────────────────┴──────────────────────────────────┘
#
#  🔑 為什麼 COPY dependencies/ 還能命中快取？
#     上游的 extract 明明重跑了，但 COPY 的快取是比對「檔案內容」，
#     不是比對「上游有沒有重跑」。93 個依賴 jar 內容一模一樣 → 命中。
#     只有 app.jar 的內容變了 → 只有那層重建。
#
#  🔑 所以分層省下的是什麼？
#     省到　：不用重新下載 93 個依賴；最終 image 只新增 45 KB 的層，
#             push 到 registry 只上傳 45 KB（沒分層的話要傳 62 MB）
#     沒省到：編譯打包還是要跑（本機 CPU 約 20-30 秒）
#     → 分層的最大價值在「傳輸」，不在「建置時間」。
#
#  🔑 什麼情況連依賴下載都會重跑？
#     只有 pom.xml 改動時（新增依賴、升級版本）。那次建置會明顯變慢，
#     而且 62 MB 那層也要重推 —— 但不常發生。
# =============================================================================

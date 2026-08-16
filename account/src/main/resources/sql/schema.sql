CREATE TABLE IF NOT EXISTS `customer` (
  `customer_id` int AUTO_INCREMENT  PRIMARY KEY,
  `name` varchar(100) NOT NULL,
  `email` varchar(100) NOT NULL,
  `mobile_number` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(20) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` varchar(20) DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS `accounts` (
  `customer_id` int NOT NULL,
  -- ⚠ 刻意「不是」AUTO_INCREMENT —— Account entity 沒有 @GeneratedValue，
  --   帳號是 AccountServiceImpl 自己算的十億起跳隨機數。
  --   設成自增的話 MySQL 的計數器會被那些值推到十億，之後查詢很困惑。
  `account_number` int PRIMARY KEY,
  `account_type` varchar(100) NOT NULL,
  `branch_address` varchar(200) NOT NULL,
  `communication_sw` boolean DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(20) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` varchar(20) DEFAULT NULL
);

-- =============================================================================
--  ⚠ 為什麼是 datetime(6) 不是 timestamp
--
--    MySQL 的 `timestamp NOT NULL` 帶有「隱式」的
--        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
--    每次 UPDATE 都會偷偷改掉值 —— 跟 BaseEntity 的 @CreatedDate /
--    @LastModifiedDate 打架，而且不會有任何錯誤。
--    entity 用的是 LocalDateTime，(6) 是微秒精度，datetime(6) 才是正確對應。
--    ⚠ 另外 timestamp 只到 2038 年。
--
--  ⚠ 本檔案是 CREATE TABLE IF NOT EXISTS ——「表已存在就整段跳過」。
--    所以「改了這裡的欄位定義」對既有的資料庫完全沒作用，
--    而 ddl-auto: validate 會讓服務直接啟動失敗：
--        Schema validation: missing column [xxx] in table [accounts]
--
-- 🔑 本專案的做法：改了結構就「刪掉資料庫重建」，不寫 ALTER 遷移語句。
--        docker compose rm -sf mysql
--        docker volume rm springboot-microservices_mysql-data
--        docker compose up -d mysql
--    ⚠ 三個服務的資料會一起消失（共用同一個 MySQL 容器）。
--    ⚠ 只想清一個的話：
--        docker exec mysql-ms mysql -uroot -proot -e "DROP DATABASE accountdb; CREATE DATABASE accountdb;"
--
--    代價是測試資料一併消失。學習專案可以接受，正式環境絕對不行 ——
--    那時該換 Flyway 或 Liquibase，它們會記錄「哪些遷移跑過了」。
-- =============================================================================

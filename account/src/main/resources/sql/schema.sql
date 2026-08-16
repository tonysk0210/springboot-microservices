CREATE TABLE IF NOT EXISTS `customer` (
  `customer_id` int AUTO_INCREMENT  PRIMARY KEY,
  `name` varchar(100) NOT NULL,
  `email` varchar(100) NOT NULL,
  `mobile_number` varchar(20) NOT NULL,
  `created_at` timestamp NOT NULL,
  `created_by` varchar(20) NOT NULL,
  `updated_at` timestamp DEFAULT NULL,
  `updated_by` varchar(20) DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS `accounts` (
  `customer_id` int NOT NULL,
  `account_number` int AUTO_INCREMENT  PRIMARY KEY,
  `account_type` varchar(100) NOT NULL,
  `branch_address` varchar(200) NOT NULL,
  `communication_sw` boolean DEFAULT NULL,
  `created_at` timestamp NOT NULL,
  `created_by` varchar(20) NOT NULL,
  `updated_at` timestamp DEFAULT NULL,
  `updated_by` varchar(20) DEFAULT NULL
);

-- ⚠⚠ 本檔案是 CREATE TABLE IF NOT EXISTS ——「表已存在就整段跳過」。
--     所以「改了這裡的欄位定義」對既有的資料庫檔案完全沒作用，
--     而 ddl-auto: validate 會讓服務直接啟動失敗：
--         Schema validation: missing column [xxx] in table [accounts]
--
--     ⚠ 本機和容器「都會」遇到，因為兩邊都是檔案模式：
--         本機   jdbc:h2:file:~/h2db/accountdb
--         容器   jdbc:h2:file:/data/accountdb  + volume account-data
--       （Dockerfile 的 ENV 寫的是 jdbc:h2:mem，但 compose.yml 又蓋回檔案模式 ——
--         只看 Dockerfile 會誤判，要以 compose.yml 為準。）
--
-- 🔑 本專案的做法：改了結構就「刪掉舊資料庫重建」，不寫 ALTER 遷移語句。
--        本機   停掉服務 → 刪 ~/h2db/accountdb* → 重啟
--        容器   docker compose rm -sf account
--               docker volume rm springboot-microservices_account-data
--               docker compose up -d account
--    ⚠ 代價是測試資料一併消失。學習專案可以接受，正式環境絕對不行 ——
--      那時該換 Flyway 或 Liquibase，它們會記錄「哪些遷移跑過了」。
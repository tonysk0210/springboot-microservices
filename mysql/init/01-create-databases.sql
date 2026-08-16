-- =============================================================================
--  建立三個服務各自的 database
--
--  ⚠ 這個檔案掛在容器的 /docker-entrypoint-initdb.d/ 底下，MySQL image
--    「只在資料目錄是空的時候」執行它 —— 也就是第一次啟動、或 volume 被刪掉之後。
--    之後改這個檔案不會有任何效果，症狀是新加的 database 一直不出現。
--    要重跑：docker compose rm -sf mysql && docker volume rm springboot-microservices_mysql-data
--
--  🔑 為什麼不用 MYSQL_DATABASE 環境變數：那個只能建「一個」database。
--
--  ⚠ 表格不在這裡建 —— 各服務啟動時由自己的 sql/schema.sql 建（spring.sql.init）。
--    這樣改表結構只要改 Java 專案，不用碰 MySQL 容器。
-- =============================================================================
CREATE DATABASE IF NOT EXISTS `accountdb`;
CREATE DATABASE IF NOT EXISTS `loandb`;
CREATE DATABASE IF NOT EXISTS `carddb`;

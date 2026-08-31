-- MySQL 第一次使用空資料 volume 啟動時，建立各微服務的 database。
-- 之後修改本檔案不會自動重跑；需刪除 mysql-data volume 後重建容器。
-- 資料表由各微服務啟動時執行自己的 sql/schema.sql 建立。
CREATE DATABASE IF NOT EXISTS `accountdb`;
CREATE DATABASE IF NOT EXISTS `loandb`;
CREATE DATABASE IF NOT EXISTS `carddb`;

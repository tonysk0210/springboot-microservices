CREATE TABLE IF NOT EXISTS `loans` (
  `loan_id` int NOT NULL AUTO_INCREMENT,
  `mobile_number` varchar(15) NOT NULL,
  `loan_number` varchar(100) NOT NULL,
  `loan_type` varchar(100) NOT NULL,
  `total_loan` int NOT NULL,
  `amount_paid` int NOT NULL,
  `outstanding_amount` int NOT NULL,
  -- ⚠ 用 datetime(6) 不用 timestamp —— MySQL 的 timestamp NOT NULL 帶有隱式的
  --   ON UPDATE CURRENT_TIMESTAMP，每次 UPDATE 都會偷偷改值，跟 BaseEntity 的
  --   @CreatedDate / @LastModifiedDate 打架。完整說明見 account 的 schema.sql。
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(20) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`loan_id`)
);

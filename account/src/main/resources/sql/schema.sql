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
  `account_number` int PRIMARY KEY,
  `account_type` varchar(100) NOT NULL,
  `branch_address` varchar(200) NOT NULL,
  `communication_sw` boolean DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(20) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` varchar(20) DEFAULT NULL
);


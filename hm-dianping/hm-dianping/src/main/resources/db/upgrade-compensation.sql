-- ============================================================
-- Compensation event table.
-- Execute:
--   mysql -u root -p hmdp < upgrade-compensation.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS `tb_compensation_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `event_key` VARCHAR(191) NOT NULL COMMENT 'Unique idempotent key for the compensation event',
  `event_type` VARCHAR(64) NOT NULL COMMENT 'Compensation action type',
  `biz_type` VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN' COMMENT 'Business domain',
  `biz_id` VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Business id',
  `payload` JSON NULL COMMENT 'Action payload',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SUCCEEDED/FAILED/CANCELED',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT 'Retry count',
  `max_retry` INT NOT NULL DEFAULT 12 COMMENT 'Max retry count',
  `next_retry_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Next retry time',
  `locked_until` DATETIME NULL COMMENT 'Processing lock expiration time',
  `last_error` TEXT NULL COMMENT 'Last failure message',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_compensation_event_key` (`event_key`),
  KEY `idx_compensation_status_next` (`status`, `next_retry_time`),
  KEY `idx_compensation_type_biz` (`event_type`, `biz_type`, `biz_id`),
  KEY `idx_compensation_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Compensation events for eventual consistency';

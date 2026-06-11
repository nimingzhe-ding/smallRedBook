-- ============================================================
-- Private message Kafka outbox.
-- Execute:
--   mysql -u root -p hmdp < upgrade-private-message-kafka.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS `tb_private_message_outbox` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `event_key` VARCHAR(191) NOT NULL COMMENT 'Unique event key',
  `message_id` BIGINT UNSIGNED NOT NULL COMMENT 'Private message id',
  `conversation_id` BIGINT UNSIGNED NOT NULL COMMENT 'Conversation id',
  `sender_id` BIGINT UNSIGNED NOT NULL COMMENT 'Sender user id',
  `receiver_id` BIGINT UNSIGNED NOT NULL COMMENT 'Receiver user id',
  `request_id` VARCHAR(128) NULL COMMENT 'Client request id',
  `payload` JSON NOT NULL COMMENT 'Kafka event payload',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SENT/FAILED',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT 'Retry count',
  `max_retry` INT NOT NULL DEFAULT 12 COMMENT 'Max retry count',
  `next_retry_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Next publish time',
  `locked_until` DATETIME NULL COMMENT 'Publish lock expiration time',
  `last_error` TEXT NULL COMMENT 'Last publish error',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_private_message_outbox_event_key` (`event_key`),
  KEY `idx_private_message_outbox_status_next` (`status`, `next_retry_time`),
  KEY `idx_private_message_outbox_message_id` (`message_id`),
  KEY `idx_private_message_outbox_receiver_time` (`receiver_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Private message Kafka outbox';

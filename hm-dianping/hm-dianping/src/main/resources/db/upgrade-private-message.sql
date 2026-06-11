USE xiaohongshu;

CREATE TABLE IF NOT EXISTS `tb_private_conversation` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `user_low_id` bigint UNSIGNED NOT NULL COMMENT 'smaller user id',
  `user_high_id` bigint UNSIGNED NOT NULL COMMENT 'larger user id',
  `last_message_id` bigint UNSIGNED NULL COMMENT 'last message id',
  `last_message_content` varchar(500) NULL COMMENT 'last message preview',
  `last_message_time` timestamp NULL DEFAULT NULL COMMENT 'last message time',
  `low_unread_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT 'unread count for lower user id',
  `high_unread_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT 'unread count for higher user id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_private_conversation_pair` (`user_low_id`, `user_high_id`) USING BTREE,
  KEY `idx_private_conversation_low_time` (`user_low_id`, `update_time`) USING BTREE,
  KEY `idx_private_conversation_high_time` (`user_high_id`, `update_time`) USING BTREE,
  KEY `idx_private_conversation_last_message` (`last_message_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='private one-to-one conversation';

CREATE TABLE IF NOT EXISTS `tb_private_message` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `conversation_id` bigint UNSIGNED NOT NULL COMMENT 'conversation id',
  `sender_id` bigint UNSIGNED NOT NULL COMMENT 'sender user id',
  `receiver_id` bigint UNSIGNED NOT NULL COMMENT 'receiver user id',
  `content` varchar(500) NOT NULL COMMENT 'message content',
  `read_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT 'read flag',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_private_message_conversation_id` (`conversation_id`, `id`) USING BTREE,
  KEY `idx_private_message_receiver_read` (`receiver_id`, `read_flag`, `create_time`) USING BTREE,
  KEY `idx_private_message_sender_time` (`sender_id`, `create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='private message';

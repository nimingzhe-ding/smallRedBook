-- 内容社区增量脚本
-- 用途：补充收藏、点赞、评论状态、商品挂载、话题、内容标签和行为事件表。
USE hmdp;

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;
DELIMITER //
CREATE PROCEDURE add_column_if_missing(
  IN p_table_name varchar(64),
  IN p_column_name varchar(64),
  IN p_column_definition text
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = p_table_name
      AND column_name = p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN `', p_column_name, '` ', p_column_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//

CREATE PROCEDURE add_index_if_missing(
  IN p_table_name varchar(64),
  IN p_index_name varchar(64),
  IN p_index_definition text
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = p_table_name
      AND index_name = p_index_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ', p_index_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

CREATE TABLE IF NOT EXISTS `tb_blog_collect` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户id',
  `blog_id` bigint UNSIGNED NOT NULL COMMENT '笔记id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_blog_collect` (`user_id`, `blog_id`) USING BTREE,
  KEY `idx_blog_id` (`blog_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记收藏关系表';

CREATE TABLE IF NOT EXISTS `tb_blog_like` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户id',
  `blog_id` bigint UNSIGNED NOT NULL COMMENT '笔记id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_blog_like` (`user_id`, `blog_id`) USING BTREE,
  KEY `idx_blog_id` (`blog_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记点赞关系表';

CREATE TABLE IF NOT EXISTS `tb_blog_product` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `blog_id` bigint UNSIGNED NOT NULL COMMENT '笔记id',
  `product_id` bigint UNSIGNED NOT NULL COMMENT '商品id',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_blog_product` (`blog_id`, `product_id`) USING BTREE,
  KEY `idx_product_id` (`product_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记挂载商品关系表';

CREATE TABLE IF NOT EXISTS `tb_content_topic` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `keyword` varchar(64) NOT NULL COMMENT '话题词，不带#',
  `heat` bigint UNSIGNED NOT NULL DEFAULT 0 COMMENT '热度',
  `note_count` bigint UNSIGNED NOT NULL DEFAULT 0 COMMENT '笔记数',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_topic_keyword` (`keyword`) USING BTREE,
  KEY `idx_topic_heat` (`heat`, `note_count`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容话题表';

CREATE TABLE IF NOT EXISTS `tb_user_notification` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '接收用户id',
  `actor_user_id` bigint UNSIGNED NULL COMMENT '触发用户id',
  `type` varchar(32) NOT NULL COMMENT '通知类型：LIKE/COLLECT/COMMENT/FOLLOW/ORDER_*',
  `title` varchar(80) NOT NULL COMMENT '通知标题',
  `content` varchar(255) NULL COMMENT '通知内容',
  `blog_id` bigint UNSIGNED NULL COMMENT '关联笔记id',
  `order_id` bigint NULL COMMENT '关联订单id',
  `payload` varchar(1024) NULL COMMENT '扩展数据',
  `read_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已读',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_read_time` (`user_id`, `read_flag`, `create_time`) USING BTREE,
  KEY `idx_blog_id` (`blog_id`) USING BTREE,
  KEY `idx_order_id` (`order_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息通知表';

CREATE TABLE IF NOT EXISTS `tb_user_notification_setting` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户id',
  `interaction_enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '互动通知开关',
  `order_enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '订单通知开关',
  `audit_enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '审核通知开关',
  `system_enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '系统通知开关',
  `realtime_enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '实时推送开关',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_notification_setting_user` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息通知设置表';

CALL add_column_if_missing('tb_blog', 'content_type', 'varchar(32) NOT NULL DEFAULT ''IMAGE'' COMMENT ''内容类型：IMAGE/VIDEO/LIVE/PRODUCT_NOTE'' AFTER `video_url`');
CALL add_column_if_missing('tb_blog', 'tags', 'varchar(128) NULL COMMENT ''内容标签，多个标签用英文逗号分隔'' AFTER `content_type`');

UPDATE `tb_blog` SET `comments` = 0 WHERE `comments` IS NULL;
UPDATE `tb_blog_comments` SET `liked` = 0 WHERE `liked` IS NULL;
UPDATE `tb_blog_comments` SET `status` = 0 WHERE `status` IS NULL;
ALTER TABLE `tb_blog_comments`
  MODIFY COLUMN `liked` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞数',
  MODIFY COLUMN `status` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0正常，1被举报，2已删除/禁止查看';

CREATE TABLE IF NOT EXISTS `tb_note_event` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint UNSIGNED NULL COMMENT '用户id，未登录为空',
  `blog_id` bigint UNSIGNED NULL COMMENT '笔记id',
  `event_type` varchar(32) NOT NULL COMMENT '事件类型：impression/detail/like/collect/comment/search/play',
  `scene` varchar(64) NULL COMMENT '发生场景：feed/detail/search/ai',
  `keyword` varchar(128) NULL COMMENT '搜索词或推荐词',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_time` (`user_id`, `create_time`) USING BTREE,
  KEY `idx_blog_event` (`blog_id`, `event_type`) USING BTREE,
  KEY `idx_event_keyword` (`event_type`, `keyword`, `create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记行为事件表';

INSERT INTO `tb_content_topic` (`keyword`, `heat`, `note_count`)
VALUES
  ('美食', 120, 0),
  ('穿搭', 90, 0),
  ('旅行', 88, 0),
  ('数码', 76, 0),
  ('好物', 72, 0),
  ('探店', 110, 0)
ON DUPLICATE KEY UPDATE `heat` = GREATEST(`heat`, VALUES(`heat`));

CREATE TABLE IF NOT EXISTS `tb_video_play_metric` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `blog_id` bigint UNSIGNED NOT NULL COMMENT '视频笔记id',
  `user_id` bigint UNSIGNED NULL COMMENT '观看用户id',
  `duration_second` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '视频总时长秒',
  `watched_second` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '本次已看秒数',
  `max_progress` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '最大播放进度百分比',
  `completed` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否完播',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_blog_time` (`blog_id`, `create_time`) USING BTREE,
  KEY `idx_user_blog` (`user_id`, `blog_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频播放指标表';

CREATE TABLE IF NOT EXISTS `tb_live_room` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `merchant_id` bigint UNSIGNED NOT NULL COMMENT '商家id',
  `anchor_user_id` bigint UNSIGNED NOT NULL COMMENT '主播用户id',
  `blog_id` bigint UNSIGNED NULL COMMENT '关联内容id，回放会沉淀为视频内容',
  `title` varchar(80) NOT NULL COMMENT '直播标题',
  `cover_url` varchar(512) NULL COMMENT '直播封面',
  `stream_url` varchar(512) NULL COMMENT '直播流地址',
  `replay_video_url` varchar(512) NULL COMMENT '直播回放视频地址',
  `status` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0预告，1直播中，2已结束',
  `online_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '在线人数',
  `liked` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '直播点赞数',
  `start_time` timestamp NULL DEFAULT NULL COMMENT '开播时间',
  `end_time` timestamp NULL DEFAULT NULL COMMENT '关播时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_status_time` (`status`, `start_time`, `create_time`) USING BTREE,
  KEY `idx_merchant_time` (`merchant_id`, `create_time`) USING BTREE,
  KEY `idx_anchor_time` (`anchor_user_id`, `create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播间表';

CREATE TABLE IF NOT EXISTS `tb_live_room_product` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `room_id` bigint UNSIGNED NOT NULL COMMENT '直播间id',
  `product_id` bigint UNSIGNED NOT NULL COMMENT '商品id',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `explain_text` varchar(255) NULL COMMENT '商品讲解文案',
  `explaining` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否正在讲解',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_room_product` (`room_id`, `product_id`) USING BTREE,
  KEY `idx_product_id` (`product_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播间商品橱窗表';

CREATE TABLE IF NOT EXISTS `tb_live_room_message` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `room_id` bigint UNSIGNED NOT NULL COMMENT '直播间id',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '发送用户id',
  `type` varchar(32) NOT NULL DEFAULT 'danmaku' COMMENT '消息类型：danmaku/like/system',
  `content` varchar(120) NOT NULL COMMENT '消息内容',
  `liked` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '消息点赞数',
  `status` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0正常，1举报，2删除',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_room_time` (`room_id`, `create_time`) USING BTREE,
  KEY `idx_user_time` (`user_id`, `create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播互动消息表';

CALL add_column_if_missing('tb_mall_product', 'category_id', 'bigint UNSIGNED NULL COMMENT ''一级类目id'' AFTER `category`');
CALL add_column_if_missing('tb_mall_product', 'sub_category_id', 'bigint UNSIGNED NULL COMMENT ''二级类目id'' AFTER `category_id`');
CALL add_column_if_missing('tb_mall_product', 'spec_summary', 'varchar(255) NULL COMMENT ''规格摘要'' AFTER `sub_category_id`');
CALL add_column_if_missing('tb_mall_product', 'score', 'int UNSIGNED NOT NULL DEFAULT 0 COMMENT ''商品评分，乘以10保存'' AFTER `spec_summary`');
CALL add_column_if_missing('tb_mall_product', 'review_count', 'int UNSIGNED NOT NULL DEFAULT 0 COMMENT ''评价数'' AFTER `score`');
CALL add_column_if_missing('tb_mall_product', 'favorite_count', 'int UNSIGNED NOT NULL DEFAULT 0 COMMENT ''收藏数'' AFTER `review_count`');

CALL add_column_if_missing('tb_mall_order', 'sku_id', 'bigint UNSIGNED NULL COMMENT ''SKU id'' AFTER `product_id`');
CALL add_column_if_missing('tb_mall_order', 'address_id', 'bigint UNSIGNED NULL COMMENT ''收货地址id'' AFTER `sku_id`');
CALL add_column_if_missing('tb_mall_order', 'sku_name', 'varchar(120) NULL COMMENT ''SKU名称快照'' AFTER `product_image`');
CALL add_column_if_missing('tb_mall_order', 'sku_specs', 'varchar(512) NULL COMMENT ''SKU规格快照'' AFTER `sku_name`');
CALL add_column_if_missing('tb_mall_order', 'receiver_name', 'varchar(64) NULL COMMENT ''收货人快照'' AFTER `sku_specs`');
CALL add_column_if_missing('tb_mall_order', 'receiver_phone', 'varchar(32) NULL COMMENT ''收货手机号快照'' AFTER `receiver_name`');
CALL add_column_if_missing('tb_mall_order', 'receiver_address', 'varchar(255) NULL COMMENT ''收货地址快照'' AFTER `receiver_phone`');
CALL add_column_if_missing('tb_mall_order', 'logistics_company', 'varchar(80) NULL COMMENT ''物流公司'' AFTER `receiver_address`');
CALL add_column_if_missing('tb_mall_order', 'logistics_no', 'varchar(80) NULL COMMENT ''物流单号'' AFTER `logistics_company`');
CALL add_column_if_missing('tb_mall_order', 'refund_reason', 'varchar(255) NULL COMMENT ''退款原因'' AFTER `logistics_no`');
CALL add_column_if_missing('tb_mall_order', 'refund_remark', 'varchar(255) NULL COMMENT ''退款处理备注'' AFTER `refund_reason`');
CALL add_column_if_missing('tb_mall_order', 'promotion_discount_amount', 'bigint UNSIGNED NOT NULL DEFAULT 0 COMMENT ''活动优惠金额'' AFTER `discount_amount`');
CALL add_column_if_missing('tb_mall_order', 'pay_time', 'timestamp NULL DEFAULT NULL COMMENT ''支付时间'' AFTER `create_time`');
CALL add_column_if_missing('tb_mall_order', 'ship_time', 'timestamp NULL DEFAULT NULL COMMENT ''发货时间'' AFTER `pay_time`');
CALL add_column_if_missing('tb_mall_order', 'receive_time', 'timestamp NULL DEFAULT NULL COMMENT ''收货时间'' AFTER `ship_time`');
CALL add_column_if_missing('tb_mall_order', 'cancel_time', 'timestamp NULL DEFAULT NULL COMMENT ''取消时间'' AFTER `receive_time`');
CALL add_column_if_missing('tb_mall_order', 'refund_time', 'timestamp NULL DEFAULT NULL COMMENT ''退款完成时间'' AFTER `cancel_time`');

CALL add_column_if_missing('tb_voucher', 'scope_type', 'varchar(32) NULL COMMENT ''券作用范围：SHOP/PRODUCT/CATEGORY/PLATFORM'' AFTER `product_id`');
CALL add_column_if_missing('tb_voucher', 'category_id', 'bigint UNSIGNED NULL COMMENT ''类目券绑定类目id'' AFTER `scope_type`');
CALL add_column_if_missing('tb_voucher', 'platform_id', 'bigint UNSIGNED NULL COMMENT ''平台券主体id'' AFTER `category_id`');

CREATE TABLE IF NOT EXISTS `tb_mall_category` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id` bigint UNSIGNED NOT NULL DEFAULT 0 COMMENT '父类目id，0为一级类目',
  `name` varchar(64) NOT NULL COMMENT '类目名称',
  `icon` varchar(255) NULL COMMENT '类目图标',
  `level` tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '层级：1一级，2二级',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_parent_sort` (`parent_id`, `sort`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城类目表';

CREATE TABLE IF NOT EXISTS `tb_mall_sku` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id` bigint UNSIGNED NOT NULL COMMENT '商品id',
  `sku_name` varchar(120) NULL COMMENT 'SKU名称',
  `specs` varchar(512) NULL COMMENT '规格JSON',
  `image` varchar(512) NULL COMMENT 'SKU图片',
  `price` bigint UNSIGNED NOT NULL COMMENT '价格，单位分',
  `origin_price` bigint UNSIGNED NULL COMMENT '划线价，单位分',
  `stock` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '库存',
  `sold` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '销量',
  `status` tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1上架，0下架',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_product_status` (`product_id`, `status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU表';

CREATE TABLE IF NOT EXISTS `tb_user_address` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户id',
  `receiver_name` varchar(64) NOT NULL COMMENT '收货人',
  `phone` varchar(32) NOT NULL COMMENT '手机号',
  `province` varchar(64) NULL COMMENT '省',
  `city` varchar(64) NULL COMMENT '市',
  `district` varchar(64) NULL COMMENT '区县',
  `detail_address` varchar(255) NOT NULL COMMENT '详细地址',
  `default_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否默认地址',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_default` (`user_id`, `default_flag`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收货地址表';

CREATE TABLE IF NOT EXISTS `tb_mall_logistics` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint UNSIGNED NOT NULL COMMENT '订单id',
  `merchant_id` bigint UNSIGNED NOT NULL COMMENT '商家id',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户id',
  `company` varchar(80) NULL COMMENT '物流公司',
  `tracking_no` varchar(80) NULL COMMENT '物流单号',
  `status` varchar(32) NOT NULL DEFAULT 'CREATED' COMMENT '物流状态',
  `traces` text NULL COMMENT '物流轨迹JSON',
  `ship_time` timestamp NULL DEFAULT NULL COMMENT '发货时间',
  `signed_time` timestamp NULL DEFAULT NULL COMMENT '签收时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_order_id` (`order_id`) USING BTREE,
  KEY `idx_user_time` (`user_id`, `create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城物流表';

CREATE TABLE IF NOT EXISTS `tb_mall_refund` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint UNSIGNED NOT NULL COMMENT '订单id',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户id',
  `merchant_id` bigint UNSIGNED NOT NULL COMMENT '商家id',
  `amount` bigint UNSIGNED NOT NULL DEFAULT 0 COMMENT '退款金额',
  `reason` varchar(255) NULL COMMENT '退款原因',
  `images` varchar(1024) NULL COMMENT '退款凭证图片',
  `status` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0待处理，1退款成功，2拒绝',
  `merchant_remark` varchar(255) NULL COMMENT '商家处理备注',
  `apply_time` timestamp NULL DEFAULT NULL COMMENT '申请时间',
  `handle_time` timestamp NULL DEFAULT NULL COMMENT '处理时间',
  `refund_time` timestamp NULL DEFAULT NULL COMMENT '退款完成时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_order_id` (`order_id`) USING BTREE,
  KEY `idx_merchant_status` (`merchant_id`, `status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城售后退款表';

CREATE TABLE IF NOT EXISTS `tb_mall_review` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint UNSIGNED NOT NULL COMMENT '订单id',
  `product_id` bigint UNSIGNED NOT NULL COMMENT '商品id',
  `sku_id` bigint UNSIGNED NULL COMMENT 'SKU id',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户id',
  `merchant_id` bigint UNSIGNED NOT NULL COMMENT '商家id',
  `rating` tinyint UNSIGNED NOT NULL DEFAULT 5 COMMENT '评分1-5',
  `content` varchar(500) NULL COMMENT '评价内容',
  `images` varchar(1024) NULL COMMENT '评价图片',
  `status` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0正常，1隐藏',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_order_user` (`order_id`, `user_id`) USING BTREE,
  KEY `idx_product_time` (`product_id`, `create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价表';

CREATE TABLE IF NOT EXISTS `tb_mall_favorite` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户id',
  `target_type` varchar(32) NOT NULL COMMENT '收藏类型：PRODUCT/SHOP',
  `target_id` bigint UNSIGNED NOT NULL COMMENT '收藏对象id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城收藏表';

CREATE TABLE IF NOT EXISTS `tb_mall_promotion` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `merchant_id` bigint UNSIGNED NULL COMMENT '商家id',
  `product_id` bigint UNSIGNED NULL COMMENT '商品id',
  `type` varchar(32) NOT NULL COMMENT '活动类型：FULL_REDUCTION/LIMITED_DISCOUNT/SECKILL/BUNDLE',
  `title` varchar(80) NOT NULL COMMENT '活动标题',
  `threshold_amount` bigint UNSIGNED NULL COMMENT '门槛金额',
  `discount_amount` bigint UNSIGNED NULL COMMENT '优惠金额',
  `discount_rate` int UNSIGNED NULL COMMENT '折扣率，90表示9折',
  `bundle_product_ids` varchar(255) NULL COMMENT '组合商品id列表',
  `status` tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
  `begin_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `end_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '结束时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_merchant_product_time` (`merchant_id`, `product_id`, `begin_time`, `end_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城营销活动表';

INSERT INTO `tb_mall_category` (`id`, `parent_id`, `name`, `level`, `sort`, `status`)
VALUES
  (1, 0, '美食', 1, 10, 1),
  (2, 0, '穿搭', 1, 20, 1),
  (3, 0, '旅行', 1, 30, 1),
  (4, 0, '数码', 1, 40, 1),
  (5, 0, '好物', 1, 50, 1),
  (101, 1, '零食饮品', 2, 10, 1),
  (102, 1, '地方特产', 2, 20, 1),
  (201, 2, '女装', 2, 10, 1),
  (202, 2, '鞋包配饰', 2, 20, 1),
  (401, 4, '手机配件', 2, 10, 1),
  (402, 4, '智能设备', 2, 20, 1),
  (501, 5, '家居日用', 2, 10, 1),
  (502, 5, '个护清洁', 2, 20, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `status` = VALUES(`status`);

-- Content governance: 0=normal, 1=reported, 2=hidden.
CALL add_column_if_missing('tb_blog', 'status', 'tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT ''content governance status'' AFTER `comments`');
UPDATE `tb_blog` SET `status` = 0 WHERE `status` IS NULL;
CALL add_index_if_missing('tb_blog', 'idx_blog_status_time', 'ADD INDEX `idx_blog_status_time` (`status`, `create_time`)');
CALL add_column_if_missing('tb_blog', 'report_reason', 'varchar(255) NULL COMMENT ''举报原因'' AFTER `status`');
CALL add_column_if_missing('tb_blog', 'report_count', 'int UNSIGNED NOT NULL DEFAULT 0 COMMENT ''举报次数'' AFTER `report_reason`');
CALL add_column_if_missing('tb_blog', 'reporter_id', 'bigint UNSIGNED NULL COMMENT ''最近举报人id'' AFTER `report_count`');
CALL add_column_if_missing('tb_blog', 'audit_remark', 'varchar(255) NULL COMMENT ''审核备注'' AFTER `reporter_id`');
CALL add_column_if_missing('tb_blog', 'auditor_id', 'bigint UNSIGNED NULL COMMENT ''审核人id'' AFTER `audit_remark`');
CALL add_column_if_missing('tb_blog', 'audit_time', 'timestamp NULL COMMENT ''审核时间'' AFTER `auditor_id`');
CALL add_index_if_missing('tb_blog', 'idx_blog_reporter', 'ADD INDEX `idx_blog_reporter` (`reporter_id`, `update_time`)');

CALL add_column_if_missing('tb_blog_comments', 'report_reason', 'varchar(255) NULL COMMENT ''举报原因'' AFTER `status`');
CALL add_column_if_missing('tb_blog_comments', 'report_count', 'int UNSIGNED NOT NULL DEFAULT 0 COMMENT ''举报次数'' AFTER `report_reason`');
CALL add_column_if_missing('tb_blog_comments', 'reporter_id', 'bigint UNSIGNED NULL COMMENT ''最近举报人id'' AFTER `report_count`');
CALL add_column_if_missing('tb_blog_comments', 'audit_remark', 'varchar(255) NULL COMMENT ''审核备注'' AFTER `reporter_id`');
CALL add_column_if_missing('tb_blog_comments', 'auditor_id', 'bigint UNSIGNED NULL COMMENT ''审核人id'' AFTER `audit_remark`');
CALL add_column_if_missing('tb_blog_comments', 'audit_time', 'timestamp NULL COMMENT ''审核时间'' AFTER `auditor_id`');
CALL add_index_if_missing('tb_blog_comments', 'idx_comment_reporter', 'ADD INDEX `idx_comment_reporter` (`reporter_id`, `update_time`)');

UPDATE `tb_video_danmaku` SET `status` = 0 WHERE `status` IS NULL;
ALTER TABLE `tb_video_danmaku`
  MODIFY COLUMN `status` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT 'content governance status';
CALL add_index_if_missing('tb_video_danmaku', 'idx_danmaku_status_time', 'ADD INDEX `idx_danmaku_status_time` (`status`, `update_time`, `create_time`)');
CALL add_column_if_missing('tb_video_danmaku', 'report_reason', 'varchar(255) NULL COMMENT ''举报原因'' AFTER `status`');
CALL add_column_if_missing('tb_video_danmaku', 'report_count', 'int UNSIGNED NOT NULL DEFAULT 0 COMMENT ''举报次数'' AFTER `report_reason`');
CALL add_column_if_missing('tb_video_danmaku', 'reporter_id', 'bigint UNSIGNED NULL COMMENT ''最近举报人id'' AFTER `report_count`');
CALL add_column_if_missing('tb_video_danmaku', 'audit_remark', 'varchar(255) NULL COMMENT ''审核备注'' AFTER `reporter_id`');
CALL add_column_if_missing('tb_video_danmaku', 'auditor_id', 'bigint UNSIGNED NULL COMMENT ''审核人id'' AFTER `audit_remark`');
CALL add_column_if_missing('tb_video_danmaku', 'audit_time', 'timestamp NULL COMMENT ''审核时间'' AFTER `auditor_id`');
CALL add_index_if_missing('tb_video_danmaku', 'idx_danmaku_reporter', 'ADD INDEX `idx_danmaku_reporter` (`reporter_id`, `update_time`)');

CREATE TABLE IF NOT EXISTS `tb_content_audit_log` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `target_type` varchar(32) NOT NULL COMMENT '审核对象：NOTE/COMMENT/DANMAKU',
  `target_id` bigint UNSIGNED NOT NULL COMMENT '审核对象id',
  `note_id` bigint UNSIGNED NULL COMMENT '关联笔记id',
  `reporter_id` bigint UNSIGNED NULL COMMENT '举报人id',
  `target_user_id` bigint UNSIGNED NULL COMMENT '内容作者id',
  `auditor_id` bigint UNSIGNED NULL COMMENT '审核人id',
  `action` varchar(32) NOT NULL COMMENT '动作：RESTORE/HIDE',
  `reason` varchar(255) NULL COMMENT '举报原因',
  `remark` varchar(255) NULL COMMENT '审核备注',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_audit_target` (`target_type`, `target_id`, `create_time`) USING BTREE,
  KEY `idx_audit_auditor` (`auditor_id`, `create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容审核操作日志表';

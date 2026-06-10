-- 商城第一版增量脚本
-- 用途：新增商品、购物车、商城订单表，并插入示例商品。

USE hmdp;

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;

DELIMITER //
CREATE PROCEDURE add_column_if_missing(
  IN table_name_value varchar(64),
  IN column_name_value varchar(64),
  IN column_definition_value text
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = table_name_value
      AND column_name = column_name_value
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', table_name_value, '` ADD COLUMN ', column_definition_value);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//

CREATE PROCEDURE add_index_if_missing(
  IN table_name_value varchar(64),
  IN index_name_value varchar(64),
  IN index_definition_value text
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = table_name_value
      AND index_name = index_name_value
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', table_name_value, '` ', index_definition_value);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

CREATE TABLE IF NOT EXISTS `tb_merchant` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '商家所属用户id',
  `name` varchar(80) NOT NULL COMMENT '店铺名称',
  `avatar` varchar(512) NULL COMMENT '店铺头像',
  `description` varchar(255) NULL COMMENT '店铺简介',
  `phone` varchar(32) NULL COMMENT '客服电话',
  `address` varchar(255) NULL COMMENT '发货/经营地址',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '营业状态：1正常，2休息，3关闭',
  `audit_status` tinyint NOT NULL DEFAULT 2 COMMENT '审核状态：1待审，2通过，3拒绝',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城商家表';

CREATE TABLE IF NOT EXISTS `tb_mall_product` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `merchant_id` bigint UNSIGNED NULL COMMENT '商家id',
  `title` varchar(128) NOT NULL COMMENT '商品标题',
  `sub_title` varchar(255) NULL COMMENT '商品副标题',
  `images` varchar(1024) NULL COMMENT '商品图片，多个用英文逗号分隔',
  `price` bigint NOT NULL COMMENT '现价，单位分',
  `origin_price` bigint NULL COMMENT '原价，单位分',
  `stock` int NOT NULL DEFAULT 0 COMMENT '库存',
  `sold` int NOT NULL DEFAULT 0 COMMENT '销量',
  `category` varchar(32) NULL COMMENT '类目',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1上架，0下架',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_merchant_status` (`merchant_id`, `status`) USING BTREE,
  KEY `idx_category_status` (`category`, `status`) USING BTREE,
  KEY `idx_sold` (`sold`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城商品表';

CREATE TABLE IF NOT EXISTS `tb_blog_product` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `blog_id` bigint UNSIGNED NOT NULL COMMENT '笔记id',
  `product_id` bigint UNSIGNED NOT NULL COMMENT '挂载商品id',
  `sort` int NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_blog_product` (`blog_id`, `product_id`) USING BTREE,
  KEY `idx_product_blog` (`product_id`, `blog_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记挂载商品关系表';

CREATE TABLE IF NOT EXISTS `tb_mall_cart_item` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户id',
  `product_id` bigint UNSIGNED NOT NULL COMMENT '商品id',
  `quantity` int NOT NULL DEFAULT 1 COMMENT '数量',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城购物车表';

CREATE TABLE IF NOT EXISTS `tb_mall_order` (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户id',
  `merchant_id` bigint UNSIGNED NULL COMMENT '商家id',
  `product_id` bigint UNSIGNED NOT NULL COMMENT '商品id',
  `product_title` varchar(128) NOT NULL COMMENT '商品标题快照',
  `product_image` varchar(512) NULL COMMENT '商品图片快照',
  `price` bigint NOT NULL COMMENT '单价，单位分',
  `quantity` int NOT NULL COMMENT '数量',
  `total_amount` bigint NOT NULL COMMENT '总金额，单位分',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1待支付，2已支付，3已发货，4已完成，5已取消',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_merchant_time` (`merchant_id`, `create_time`) USING BTREE,
  KEY `idx_user_time` (`user_id`, `create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城订单表';

CALL add_column_if_missing('tb_mall_product', 'merchant_id', '`merchant_id` bigint UNSIGNED NULL COMMENT ''商家id'' AFTER `id`');
CALL add_index_if_missing('tb_mall_product', 'idx_merchant_status', 'ADD INDEX `idx_merchant_status` (`merchant_id`, `status`)');

CALL add_column_if_missing('tb_mall_order', 'merchant_id', '`merchant_id` bigint UNSIGNED NULL COMMENT ''商家id'' AFTER `user_id`');
CALL add_column_if_missing('tb_mall_order', 'voucher_id', '`voucher_id` bigint UNSIGNED NULL COMMENT ''优惠券id'' AFTER `product_id`');
CALL add_column_if_missing('tb_mall_order', 'discount_amount', '`discount_amount` bigint NOT NULL DEFAULT 0 COMMENT ''优惠金额，单位分'' AFTER `price`');
CALL add_column_if_missing('tb_mall_order', 'pay_time', '`pay_time` timestamp NULL DEFAULT NULL COMMENT ''支付时间'' AFTER `create_time`');
CALL add_column_if_missing('tb_mall_order', 'ship_time', '`ship_time` timestamp NULL DEFAULT NULL COMMENT ''发货时间'' AFTER `pay_time`');
CALL add_column_if_missing('tb_mall_order', 'receive_time', '`receive_time` timestamp NULL DEFAULT NULL COMMENT ''收货时间'' AFTER `ship_time`');
CALL add_column_if_missing('tb_mall_order', 'cancel_time', '`cancel_time` timestamp NULL DEFAULT NULL COMMENT ''取消时间'' AFTER `receive_time`');
CALL add_column_if_missing('tb_mall_order', 'refund_time', '`refund_time` timestamp NULL DEFAULT NULL COMMENT ''退款完成时间'' AFTER `cancel_time`');
CALL add_index_if_missing('tb_mall_order', 'idx_merchant_time', 'ADD INDEX `idx_merchant_time` (`merchant_id`, `create_time`)');

CALL add_column_if_missing('tb_voucher', 'merchant_id', '`merchant_id` bigint UNSIGNED NULL COMMENT ''商城商家id'' AFTER `shop_id`');
CALL add_column_if_missing('tb_voucher', 'product_id', '`product_id` bigint UNSIGNED NULL COMMENT ''商城商品id'' AFTER `merchant_id`');
CALL add_index_if_missing('tb_voucher', 'idx_mall_voucher', 'ADD INDEX `idx_mall_voucher` (`merchant_id`, `product_id`, `status`)');

CALL add_column_if_missing('tb_blog', 'video_url', '`video_url` varchar(1024) NULL COMMENT ''视频笔记地址'' AFTER `images`');
CALL add_column_if_missing('tb_blog', 'content_type', '`content_type` varchar(32) NOT NULL DEFAULT ''IMAGE'' COMMENT ''内容类型：IMAGE/VIDEO/PRODUCT_NOTE'' AFTER `video_url`');
CALL add_index_if_missing('tb_blog', 'idx_blog_content_type', 'ADD INDEX `idx_blog_content_type` (`content_type`, `create_time`)');

UPDATE `tb_blog`
SET `content_type` = 'VIDEO'
WHERE `video_url` IS NOT NULL
  AND TRIM(`video_url`) <> ''
  AND (`content_type` IS NULL OR `content_type` = '' OR `content_type` = 'IMAGE');

CREATE TABLE IF NOT EXISTS `tb_video_danmaku` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `blog_id` bigint UNSIGNED NOT NULL COMMENT '视频笔记id',
  `user_id` bigint UNSIGNED NULL COMMENT '发送用户id，对外匿名',
  `content` varchar(80) NOT NULL COMMENT '弹幕内容',
  `video_second` int NOT NULL DEFAULT 0 COMMENT '视频播放秒数',
  `lane` int NULL COMMENT '弹幕轨道',
  `status` bit(1) NOT NULL DEFAULT b'0' COMMENT '状态：0正常，1隐藏',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_blog_second` (`blog_id`, `video_second`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频弹幕表';

INSERT INTO `tb_merchant`
(`id`, `user_id`, `name`, `avatar`, `description`, `phone`, `address`, `status`, `audit_status`)
SELECT 1, 1, '探店优选旗舰店',
       'https://images.unsplash.com/photo-1527980965255-d3b416303d12?auto=format&fit=crop&w=200&q=80',
       '内容同款、探店套餐和创作装备集合店',
       '400-100-1204',
       '杭州内容电商产业园',
       1, 2
WHERE NOT EXISTS (SELECT 1 FROM `tb_merchant` WHERE `id` = 1);

INSERT INTO `tb_mall_product`
(`merchant_id`, `title`, `sub_title`, `images`, `price`, `origin_price`, `stock`, `sold`, `category`, `status`)
SELECT 1, '城市露营咖啡礼盒', '挂耳咖啡 + 随行杯，适合周末出逃', 'https://images.unsplash.com/photo-1509042239860-f550ce710b93?auto=format&fit=crop&w=900&q=80', 9900, 12900, 80, 126, 'coffee', 1
WHERE NOT EXISTS (SELECT 1 FROM `tb_mall_product` WHERE `title` = '城市露营咖啡礼盒');

INSERT INTO `tb_mall_product`
(`merchant_id`, `title`, `sub_title`, `images`, `price`, `origin_price`, `stock`, `sold`, `category`, `status`)
SELECT 1, '港式茶餐厅双人套餐', '经典菠萝油、奶茶、主食组合', 'https://images.unsplash.com/photo-1551218808-94e220e084d2?auto=format&fit=crop&w=900&q=80', 6880, 9800, 60, 238, 'food', 1
WHERE NOT EXISTS (SELECT 1 FROM `tb_mall_product` WHERE `title` = '港式茶餐厅双人套餐');

INSERT INTO `tb_mall_product`
(`merchant_id`, `title`, `sub_title`, `images`, `price`, `origin_price`, `stock`, `sold`, `category`, `status`)
SELECT 1, '拍照探店补光灯', '轻便折叠，适合美食和穿搭笔记', 'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=900&q=80', 5900, 7900, 120, 91, 'gear', 1
WHERE NOT EXISTS (SELECT 1 FROM `tb_mall_product` WHERE `title` = '拍照探店补光灯');

UPDATE `tb_mall_product` SET `merchant_id` = 1 WHERE `merchant_id` IS NULL;

INSERT INTO `tb_voucher`
(`shop_id`, `merchant_id`, `product_id`, `title`, `sub_title`, `rules`, `pay_value`, `actual_value`, `type`, `status`)
SELECT NULL, 1, NULL, '商城新人满50减10', '探店商城全店可用', '商城订单满50元可用，不与其他券叠加', 5000, 1000, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM `tb_voucher` WHERE `title` = '商城新人满50减10');

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;

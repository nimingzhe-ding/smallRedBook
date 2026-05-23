-- 作品级 MVP 演示数据
-- 依赖 hmdp.sql、upgrade-mall.sql、upgrade-content-community.sql 已执行。
USE hmdp;

INSERT INTO `tb_user` (`id`, `phone`, `password`, `nick_name`, `icon`)
VALUES
  (9001, '13900009001', '', '咖啡地图小满', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=240&q=80'),
  (9002, '13900009002', '', '周末探店阿泽', 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=240&q=80'),
  (9003, '13900009003', '', '好物编辑小鹿', 'https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=240&q=80')
ON DUPLICATE KEY UPDATE `nick_name` = VALUES(`nick_name`), `icon` = VALUES(`icon`);

INSERT INTO `tb_user_info` (`user_id`, `city`, `introduce`, `fans`, `followee`, `gender`, `credits`, `level`)
VALUES
  (9001, '杭州', '每天记录一杯好咖啡和一条适合散步的街。', 1280, 88, 1, 360, 4),
  (9002, '上海', '本地生活体验派，认真写每一次踩点。', 860, 102, 0, 220, 3),
  (9003, '杭州', '把内容里反复出现的好物整理成清单。', 2160, 76, 1, 520, 5)
ON DUPLICATE KEY UPDATE `city` = VALUES(`city`), `introduce` = VALUES(`introduce`);

INSERT INTO `tb_merchant` (`id`, `user_id`, `name`, `avatar`, `description`, `phone`, `address`, `status`, `audit_status`)
VALUES
  (9001, 9001, '小满城市咖啡', 'https://images.unsplash.com/photo-1554118811-1e0d58224f24?auto=format&fit=crop&w=300&q=80', '咖啡、甜点和周边小物精选。', '400-900-1001', '杭州上城区湖滨步行街 18 号', 1, 2),
  (9002, 9003, '小鹿好物研究所', 'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=300&q=80', '探店、拍摄、旅行随身好物集合店。', '400-900-1002', '杭州拱墅区运河天地 2 号', 1, 2)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `avatar` = VALUES(`avatar`), `description` = VALUES(`description`);

INSERT INTO `tb_mall_product`
(`id`, `merchant_id`, `title`, `sub_title`, `images`, `price`, `origin_price`, `stock`, `sold`, `category`, `status`, `score`, `review_count`, `favorite_count`)
VALUES
  (900101, 9001, '湖滨拿铁双杯券', '工作日可用，含两杯热拿铁', 'https://images.unsplash.com/photo-1461023058943-07fcbe16d735?auto=format&fit=crop&w=900&q=80', 3980, 5200, 88, 326, 'food', 1, 48, 64, 190),
  (900102, 9001, '手作巴斯克蛋糕 4 寸', '低糖配方，适合下午茶分享', 'https://images.unsplash.com/photo-1464305795204-6f5bbfc7fb81?auto=format&fit=crop&w=900&q=80', 6800, 8800, 36, 171, 'food', 1, 47, 39, 118),
  (900103, 9001, '城市散步挂耳咖啡礼盒', '6 种产区风味，附路线卡片', 'https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?auto=format&fit=crop&w=900&q=80', 9900, 12900, 64, 208, 'coffee', 1, 49, 82, 233),
  (900201, 9002, '探店口袋补光灯', '磁吸夹持，夜景和美食都能拍', 'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=900&q=80', 5900, 7900, 120, 96, 'gear', 1, 46, 21, 75),
  (900202, 9002, '旅行分装护肤套装', '三日短途容量，透明收纳袋', 'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?auto=format&fit=crop&w=900&q=80', 4590, 6900, 52, 141, 'beauty', 1, 45, 28, 102),
  (900203, 9002, '轻量帆布通勤包', '可放相机和 13 寸电脑', 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=900&q=80', 12800, 16800, 18, 87, 'fashion', 1, 48, 44, 156)
ON DUPLICATE KEY UPDATE
  `merchant_id` = VALUES(`merchant_id`),
  `title` = VALUES(`title`),
  `sub_title` = VALUES(`sub_title`),
  `images` = VALUES(`images`),
  `price` = VALUES(`price`),
  `origin_price` = VALUES(`origin_price`),
  `stock` = VALUES(`stock`),
  `sold` = VALUES(`sold`),
  `category` = VALUES(`category`),
  `status` = VALUES(`status`),
  `score` = VALUES(`score`),
  `review_count` = VALUES(`review_count`),
  `favorite_count` = VALUES(`favorite_count`);

INSERT INTO `tb_blog`
(`id`, `user_id`, `shop_id`, `title`, `images`, `video_url`, `content_type`, `tags`, `content`, `liked`, `comments`, `status`)
VALUES
  (900001, 9001, 1, '杭州湖滨新开的咖啡窗口，拿铁香得很稳定', 'https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=900&q=80', NULL, 'PRODUCT_NOTE', '咖啡,探店,杭州', '早上八点半人不多，燕麦拿铁很顺，搭配巴斯克刚好。适合通勤前坐 20 分钟。', 386, 18, 0),
  (900002, 9002, 4, '远洋乐堤港约会餐厅，灯光和座位都很出片', 'https://images.unsplash.com/photo-1552566626-52f8b828add9?auto=format&fit=crop&w=900&q=80', NULL, 'IMAGE', '约会餐厅,杭州,晚餐', '靠窗位建议提前订，招牌意面比甜品更值得点，人均在 120 左右。', 244, 11, 0),
  (900003, 9003, 0, '拍探店视频时我最常带的 3 件小装备', 'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=900&q=80', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 'VIDEO', '拍摄装备,好物,视频', '补光灯、领夹麦、轻量脚架，预算不高也能提升成片稳定性。', 512, 32, 0),
  (900004, 9001, 3, '运河边散步路线：咖啡、书店和夜景都安排好了', 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=900&q=80', NULL, 'IMAGE', '城市散步,杭州,周末', '下午四点从桥西出发，先喝咖啡，再去书店，傍晚沿河回走。', 198, 9, 0),
  (900005, 9002, 8, '寿司店真实体验：午市套餐比单点更划算', 'https://images.unsplash.com/photo-1579871494447-9811cf80d66c?auto=format&fit=crop&w=900&q=80', NULL, 'IMAGE', '寿司,午餐,探店', '鱼生新鲜度在线，午市套餐含汤和小菜，适合工作日快速吃。', 167, 7, 0),
  (900006, 9003, 0, '三天短途旅行，我会怎么收纳护肤品', 'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?auto=format&fit=crop&w=900&q=80', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 'VIDEO', '旅行,收纳,护肤', '分装瓶按早晚分区，透明袋过安检方便，也不容易漏。', 438, 21, 0),
  (900007, 9001, 5, '火锅店排队两小时值不值？我觉得看你点什么', 'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=900&q=80', NULL, 'IMAGE', '火锅,排队,本地生活', '锅底和服务稳定，建议避开周五晚高峰，鸭血和虾滑可以点。', 302, 16, 0),
  (900008, 9002, 0, '春天通勤包里有什么：相机、伞和一件薄外套', 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=900&q=80', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 'VIDEO', '穿搭,通勤包,好物', '容量够、肩带不勒、能放相机，是我最近出门频率最高的一只。', 356, 19, 0)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`), `content` = VALUES(`content`), `content_type` = VALUES(`content_type`), `tags` = VALUES(`tags`);

INSERT INTO `tb_blog_product` (`blog_id`, `product_id`, `sort`)
VALUES
  (900001, 900101, 1),
  (900001, 900102, 2),
  (900003, 900201, 1),
  (900006, 900202, 1),
  (900008, 900203, 1)
ON DUPLICATE KEY UPDATE `sort` = VALUES(`sort`);

INSERT INTO `tb_blog_comments` (`id`, `user_id`, `blog_id`, `parent_id`, `answer_id`, `content`, `liked`, `status`)
VALUES
  (900001, 9002, 900001, 0, 0, '这个窗口我路过好多次，下次按你这个组合点。', 18, 0),
  (900002, 9003, 900003, 0, 0, '补光灯真的救夜景，尤其餐厅黄光环境。', 24, 0),
  (900003, 9001, 900006, 0, 0, '分区收纳这个方法学到了，周末就试。', 13, 0)
ON DUPLICATE KEY UPDATE `content` = VALUES(`content`), `liked` = VALUES(`liked`);

INSERT INTO `tb_mall_order`
(`id`, `user_id`, `merchant_id`, `product_id`, `product_title`, `product_image`, `price`, `discount_amount`, `quantity`, `total_amount`, `status`, `receiver_name`, `receiver_phone`, `receiver_address`, `logistics_company`, `logistics_no`, `pay_time`, `ship_time`, `receive_time`, `cancel_time`, `refund_time`)
VALUES
  (990000001, 1, 9001, 900101, '湖滨拿铁双杯券', 'https://images.unsplash.com/photo-1461023058943-07fcbe16d735?auto=format&fit=crop&w=900&q=80', 3980, 0, 1, 3980, 1, '小鱼同学', '13686869696', '浙江杭州上城区湖滨路 18 号', NULL, NULL, NULL, NULL, NULL, NULL, NULL),
  (990000002, 1, 9001, 900103, '城市散步挂耳咖啡礼盒', 'https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?auto=format&fit=crop&w=900&q=80', 9900, 1000, 1, 8900, 3, '小鱼同学', '13686869696', '浙江杭州上城区湖滨路 18 号', NULL, NULL, NOW(), NULL, NULL, NULL, NULL),
  (990000003, 1, 9002, 900201, '探店口袋补光灯', 'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=900&q=80', 5900, 0, 1, 5900, 4, '小鱼同学', '13686869696', '浙江杭州上城区湖滨路 18 号', '顺丰速运', 'SF900201', NOW(), NOW(), NULL, NULL, NULL),
  (990000004, 1, 9002, 900203, '轻量帆布通勤包', 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=900&q=80', 12800, 0, 1, 12800, 5, '小鱼同学', '13686869696', '浙江杭州上城区湖滨路 18 号', '京东物流', 'JD900203', NOW(), NOW(), NOW(), NULL, NULL),
  (990000005, 1, 9001, 900102, '手作巴斯克蛋糕 4 寸', 'https://images.unsplash.com/photo-1464305795204-6f5bbfc7fb81?auto=format&fit=crop&w=900&q=80', 6800, 0, 1, 6800, 6, '小鱼同学', '13686869696', '浙江杭州上城区湖滨路 18 号', NULL, NULL, NULL, NULL, NULL, NOW(), NULL),
  (990000006, 1, 9002, 900202, '旅行分装护肤套装', 'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?auto=format&fit=crop&w=900&q=80', 4590, 0, 1, 4590, 7, '小鱼同学', '13686869696', '浙江杭州上城区湖滨路 18 号', NULL, NULL, NOW(), NULL, NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE `status` = VALUES(`status`), `product_title` = VALUES(`product_title`);

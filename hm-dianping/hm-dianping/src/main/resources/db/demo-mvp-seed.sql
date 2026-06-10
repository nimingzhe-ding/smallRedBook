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

-- 2026 演示扩容：让首页、视频流和商城更接近真实内容平台。
INSERT INTO `tb_user` (`id`, `phone`, `password`, `nick_name`, `icon`)
VALUES
  (9004, '13900009004', '', '通勤穿搭林一', 'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=240&q=80'),
  (9005, '13900009005', '', '家居整理阿宁', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=240&q=80'),
  (9006, '13900009006', '', '城市旅行橙子', 'https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?auto=format&fit=crop&w=240&q=80'),
  (9007, '13900009007', '', '数码效率派Leo', 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=240&q=80'),
  (9008, '13900009008', '', '晚餐计划小南', 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=240&q=80')
ON DUPLICATE KEY UPDATE `nick_name` = VALUES(`nick_name`), `icon` = VALUES(`icon`);

INSERT INTO `tb_user_info` (`user_id`, `city`, `introduce`, `fans`, `followee`, `gender`, `credits`, `level`)
VALUES
  (9004, '上海', '每天一套能直接照着穿的通勤方案。', 3420, 146, 1, 640, 5),
  (9005, '杭州', '把小户型收纳做成轻松可复制的清单。', 2190, 91, 1, 520, 5),
  (9006, '苏州', '两天一夜和城市漫游路线收藏夹。', 1680, 138, 0, 410, 4),
  (9007, '深圳', '认真测试提升效率的小设备和 App。', 2860, 82, 0, 590, 5),
  (9008, '杭州', '下班后还能好好吃饭的简化菜单。', 1320, 108, 1, 300, 3)
ON DUPLICATE KEY UPDATE `city` = VALUES(`city`), `introduce` = VALUES(`introduce`), `fans` = VALUES(`fans`);

INSERT INTO `tb_merchant` (`id`, `user_id`, `name`, `avatar`, `description`, `phone`, `address`, `status`, `audit_status`)
VALUES
  (9003, 9004, '林一通勤衣橱', 'https://images.unsplash.com/photo-1483985988355-763728e1935b?auto=format&fit=crop&w=300&q=80', '基础款、包袋和通勤配饰精选。', '400-900-1003', '上海静安区愚园路 66 号', 1, 2),
  (9004, 9005, '阿宁小家研究所', 'https://images.unsplash.com/photo-1556228453-efd6c1ff04f6?auto=format&fit=crop&w=300&q=80', '小户型收纳、香氛和家居清洁。', '400-900-1004', '杭州西湖区文三路 188 号', 1, 2),
  (9005, 9007, 'Leo 数码效率店', 'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=300&q=80', '移动办公、拍摄和桌面效率装备。', '400-900-1005', '深圳南山区科技园 9 栋', 1, 2)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `avatar` = VALUES(`avatar`), `description` = VALUES(`description`);

INSERT INTO `tb_mall_product`
(`id`, `merchant_id`, `title`, `sub_title`, `images`, `price`, `origin_price`, `stock`, `sold`, `category`, `category_id`, `sub_category_id`, `spec_summary`, `status`, `score`, `review_count`, `favorite_count`)
VALUES
  (900104, 9001, '冷萃咖啡随行瓶套装', '冷萃原液 6 袋 + 350ml 随行瓶', 'https://images.unsplash.com/photo-1517701604599-bb29b565090c?auto=format&fit=crop&w=900&q=80', 7990, 10900, 92, 268, 'coffee', 1, 101, '6 袋装 / 随行瓶 / 冷萃原液', 1, 48, 72, 201),
  (900105, 9001, '周末早午餐双人券', '咖啡、主食、甜点三件套', 'https://images.unsplash.com/photo-1551218808-94e220e084d2?auto=format&fit=crop&w=900&q=80', 11800, 15800, 45, 193, 'food', 1, 101, '双人餐 / 周末可约 / 到店核销', 1, 47, 58, 164),
  (900204, 9002, '领夹麦克风轻量套装', '手机相机双接口，适合探店视频', 'https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=900&q=80', 16900, 22900, 56, 132, 'gear', 4, 401, '双麦 / 降噪 / Type-C 转接', 1, 49, 66, 215),
  (900205, 9002, '透明旅行收纳四件组', '洗漱、充电线、证件、护肤分区', 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=900&q=80', 3990, 5900, 140, 311, 'travel', 3, 501, '4 件组 / 透明防泼水 / 可登机', 1, 46, 48, 129),
  (900301, 9003, '垂感通勤西装外套', '不易皱，办公室和约会都能穿', 'https://images.unsplash.com/photo-1496747611176-843222e1e57c?auto=format&fit=crop&w=900&q=80', 23900, 32900, 38, 154, 'fashion', 2, 201, '黑色 / 米白 / S-L', 1, 48, 91, 248),
  (900302, 9003, '软皮托特通勤包', '13 寸电脑、雨伞和相机都能放', 'https://images.unsplash.com/photo-1594223274512-ad4803739b7c?auto=format&fit=crop&w=900&q=80', 18800, 25800, 64, 209, 'fashion', 2, 202, '棕色 / 黑色 / 大容量', 1, 47, 64, 197),
  (900303, 9003, '基础白 T 三件装', '厚度适中，不透不松垮', 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?auto=format&fit=crop&w=900&q=80', 9900, 13900, 120, 482, 'fashion', 2, 201, '3 件装 / XS-XL / 纯棉', 1, 46, 112, 306),
  (900401, 9004, '小户型抽屉分隔盒', '厨房、衣柜、书桌都能用', 'https://images.unsplash.com/photo-1586023492125-27b2c045efd7?auto=format&fit=crop&w=900&q=80', 3590, 4900, 180, 356, 'home', 5, 501, '6 件组 / 可水洗 / 半透明', 1, 48, 78, 241),
  (900402, 9004, '晚安木质香氛蜡烛', '雪松、无花果、白茶三种香型', 'https://images.unsplash.com/photo-1602874801007-5f01c3f83d76?auto=format&fit=crop&w=900&q=80', 6900, 9900, 73, 226, 'home', 5, 501, '180g / 约 38 小时 / 三香型', 1, 49, 69, 233),
  (900403, 9004, '浴室无痕置物架', '免打孔，瓶瓶罐罐不再堆台面', 'https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=900&q=80', 4990, 7900, 96, 188, 'home', 5, 501, '双层 / 免打孔 / 承重 8kg', 1, 45, 32, 118),
  (900501, 9005, '桌面无线充电支架', '边充边看消息，适合办公桌', 'https://images.unsplash.com/photo-1616410011236-7a42121dd981?auto=format&fit=crop&w=900&q=80', 12900, 16900, 70, 176, 'digital', 4, 401, '15W / 可折叠 / 黑白双色', 1, 48, 54, 173),
  (900502, 9005, '便携蓝牙键盘', '轻薄静音，咖啡店办公友好', 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=900&q=80', 15900, 21900, 54, 141, 'digital', 4, 402, '蓝牙双模 / 静音 / 便携', 1, 47, 42, 150),
  (900503, 9005, '手机三脚架自拍杆', '视频、拍摄、探店都能用', 'https://images.unsplash.com/photo-1520390138845-fd2d229dd553?auto=format&fit=crop&w=900&q=80', 8990, 12900, 88, 265, 'gear', 4, 401, '1.4m / 蓝牙遥控 / 稳定云台', 1, 46, 77, 220),
  (900504, 9005, '桌面理线磁吸夹', '数据线不再掉到桌子后面', 'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=900&q=80', 2990, 4900, 210, 390, 'digital', 4, 401, '6 枚装 / 磁吸 / 多色', 1, 45, 38, 132)
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
  `category_id` = VALUES(`category_id`),
  `sub_category_id` = VALUES(`sub_category_id`),
  `spec_summary` = VALUES(`spec_summary`),
  `status` = VALUES(`status`),
  `score` = VALUES(`score`),
  `review_count` = VALUES(`review_count`),
  `favorite_count` = VALUES(`favorite_count`);

UPDATE `tb_mall_product`
SET `category_id` = CASE `category`
  WHEN 'food' THEN 1 WHEN 'coffee' THEN 1 WHEN 'fashion' THEN 2 WHEN 'travel' THEN 3
  WHEN 'gear' THEN 4 WHEN 'digital' THEN 4 WHEN 'beauty' THEN 5 WHEN 'home' THEN 5 ELSE `category_id` END,
  `sub_category_id` = CASE `category`
  WHEN 'food' THEN 101 WHEN 'coffee' THEN 101 WHEN 'fashion' THEN 202 WHEN 'travel' THEN 501
  WHEN 'gear' THEN 401 WHEN 'digital' THEN 402 WHEN 'beauty' THEN 502 WHEN 'home' THEN 501 ELSE `sub_category_id` END
WHERE `id` BETWEEN 900101 AND 900599;

INSERT INTO `tb_mall_sku`
(`id`, `product_id`, `sku_name`, `specs`, `image`, `price`, `origin_price`, `stock`, `sold`, `status`)
VALUES
  (9101041, 900104, '标准套装', '{"口味":"经典冷萃","组合":"6袋+随行瓶"}', 'https://images.unsplash.com/photo-1517701604599-bb29b565090c?auto=format&fit=crop&w=900&q=80', 7990, 10900, 52, 146, 1),
  (9102041, 900204, 'Type-C 双麦套装', '{"接口":"Type-C","麦克风":"双人采访"}', 'https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=900&q=80', 16900, 22900, 38, 93, 1),
  (9103011, 900301, '米白 S', '{"颜色":"米白","尺码":"S"}', 'https://images.unsplash.com/photo-1496747611176-843222e1e57c?auto=format&fit=crop&w=900&q=80', 23900, 32900, 12, 48, 1),
  (9103012, 900301, '黑色 M', '{"颜色":"黑色","尺码":"M"}', 'https://images.unsplash.com/photo-1496747611176-843222e1e57c?auto=format&fit=crop&w=900&q=80', 23900, 32900, 16, 61, 1),
  (9104011, 900401, '抽屉六件组', '{"颜色":"半透明","组合":"6件"}', 'https://images.unsplash.com/photo-1586023492125-27b2c045efd7?auto=format&fit=crop&w=900&q=80', 3590, 4900, 100, 231, 1),
  (9105011, 900501, '黑色 15W', '{"颜色":"黑色","功率":"15W"}', 'https://images.unsplash.com/photo-1616410011236-7a42121dd981?auto=format&fit=crop&w=900&q=80', 12900, 16900, 34, 91, 1),
  (9105031, 900503, '蓝牙遥控款', '{"高度":"1.4m","遥控":"蓝牙"}', 'https://images.unsplash.com/photo-1520390138845-fd2d229dd553?auto=format&fit=crop&w=900&q=80', 8990, 12900, 55, 174, 1)
ON DUPLICATE KEY UPDATE `sku_name` = VALUES(`sku_name`), `price` = VALUES(`price`), `stock` = VALUES(`stock`), `sold` = VALUES(`sold`);

INSERT INTO `tb_blog`
(`id`, `user_id`, `shop_id`, `title`, `images`, `video_url`, `content_type`, `tags`, `content`, `liked`, `comments`, `status`, `create_time`)
VALUES
  (900009, 9004, 0, '一周通勤胶囊衣橱：5 件单品穿出 9 套', 'https://images.unsplash.com/photo-1483985988355-763728e1935b?auto=format&fit=crop&w=900&q=80', NULL, 'PRODUCT_NOTE', '通勤穿搭,胶囊衣橱,好物', '西装外套、白 T、直筒裤、托特包和一双乐福鞋，早上不纠结。', 689, 42, 0, NOW() - INTERVAL 1 HOUR),
  (900010, 9005, 0, '小户型收纳不要买太多盒子，先分这 4 类', 'https://images.unsplash.com/photo-1556228453-efd6c1ff04f6?auto=format&fit=crop&w=900&q=80', NULL, 'PRODUCT_NOTE', '收纳,家居,小户型', '厨房高频、浴室潮湿、衣柜换季、桌面杂物分别处理，空间会清爽很多。', 574, 33, 0, NOW() - INTERVAL 2 HOUR),
  (900011, 9007, 0, '桌面效率改造：少买大件，多解决线和充电', 'https://images.unsplash.com/photo-1497366754035-f200968a6e72?auto=format&fit=crop&w=900&q=80', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 'VIDEO', '数码,桌面改造,效率', '无线充、理线夹和一把轻键盘，比换整张桌子更立竿见影。', 801, 55, 0, NOW() - INTERVAL 3 HOUR),
  (900012, 9006, 0, '苏州两天一夜路线：不赶景点也能拍很多', 'https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=900&q=80', 'https://www.w3schools.com/html/mov_bbb.mp4', 'VIDEO', '旅行,苏州,周末', '第一天园林和咖啡，第二天老街和河边，住宿选地铁口附近更轻松。', 642, 29, 0, NOW() - INTERVAL 4 HOUR),
  (900013, 9008, 3, '下班后 20 分钟晚餐：番茄牛肉饭', 'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=900&q=80', NULL, 'IMAGE', '晚餐,快手菜,本地生活', '冷冻牛肉卷、番茄、鸡蛋和米饭，酸甜开胃，收拾也快。', 463, 24, 0, NOW() - INTERVAL 5 HOUR),
  (900014, 9001, 0, '冷萃咖啡怎么带出门：我会这样配', 'https://images.unsplash.com/photo-1517701604599-bb29b565090c?auto=format&fit=crop&w=900&q=80', 'https://media.w3.org/2010/05/bunny/trailer.mp4', 'VIDEO', '咖啡,通勤,好物', '冷萃原液、冰块和随行瓶，早高峰也能喝到稳定的咖啡。', 522, 31, 0, NOW() - INTERVAL 6 HOUR),
  (900015, 9003, 0, '口袋补光灯真实测试：餐厅黄光能救多少', 'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=900&q=80', 'https://media.w3.org/2010/05/sintel/trailer.mp4', 'VIDEO', '拍摄装备,探店,视频', '近距离打光会油，侧上方 45 度更自然，拍甜品和烤肉都明显。', 733, 48, 0, NOW() - INTERVAL 7 HOUR),
  (900016, 9004, 0, '托特包能不能通勤，看这 5 个细节', 'https://images.unsplash.com/photo-1594223274512-ad4803739b7c?auto=format&fit=crop&w=900&q=80', NULL, 'PRODUCT_NOTE', '包包,通勤,穿搭', '底部是否定型、肩带宽度、内袋数量、拉链和重量，决定能不能天天背。', 456, 18, 0, NOW() - INTERVAL 8 HOUR),
  (900017, 9005, 0, '浴室台面清空之后，早上真的快了 5 分钟', 'https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=900&q=80', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 'VIDEO', '家居,浴室收纳,清洁', '把洗漱、护肤、清洁分层放，湿区尽量上墙，水渍少很多。', 388, 20, 0, NOW() - INTERVAL 9 HOUR),
  (900018, 9002, 0, '短途出差我只带一个透明收纳袋', 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=900&q=80', 'https://www.w3schools.com/html/mov_bbb.mp4', 'VIDEO', '旅行收纳,出差,好物', '证件、充电线、护肤小样放一起，安检和酒店入住都不慌。', 501, 27, 0, NOW() - INTERVAL 10 HOUR),
  (900019, 9007, 0, '手机三脚架不是智商税，前提是你会这样用', 'https://images.unsplash.com/photo-1520390138845-fd2d229dd553?auto=format&fit=crop&w=900&q=80', 'https://media.w3.org/2010/05/bunny/trailer.mp4', 'VIDEO', '拍摄,视频,数码', '拍开箱、做菜、探店延时都能用，重点是高度和夹具稳定。', 604, 37, 0, NOW() - INTERVAL 11 HOUR),
  (900020, 9008, 5, '火锅后怎么清爽收尾：我会点这几样', 'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=900&q=80', NULL, 'IMAGE', '火锅,晚餐,探店', '番茄锅底、蔬菜拼盘、冰粉和无糖茶，比一顿全肉舒服很多。', 312, 14, 0, NOW() - INTERVAL 12 HOUR),
  (900021, 9006, 0, '周末城市漫游包：轻一点才走得远', 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=900&q=80', NULL, 'PRODUCT_NOTE', '城市漫游,旅行,包包', '相机、薄外套、雨伞、水杯和充电宝，包本身一定要轻。', 436, 22, 0, NOW() - INTERVAL 13 HOUR),
  (900022, 9005, 0, '香氛蜡烛怎么选不踩雷：先看空间大小', 'https://images.unsplash.com/photo-1602874801007-5f01c3f83d76?auto=format&fit=crop&w=900&q=80', NULL, 'PRODUCT_NOTE', '香氛,家居,睡前仪式', '卧室适合低扩香，客厅可以更明亮，第一次买别选太甜。', 377, 16, 0, NOW() - INTERVAL 14 HOUR),
  (900023, 9007, 0, '咖啡店办公 2 小时，我会带哪几个小设备', 'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=900&q=80', 'https://media.w3.org/2010/05/sintel/trailer.mp4', 'VIDEO', '移动办公,数码,咖啡店', '轻键盘、无线充、小支架，少带线，桌面不乱才更专注。', 559, 36, 0, NOW() - INTERVAL 15 HOUR),
  (900024, 9001, 0, '早午餐双人券适合约会吗？我实际去了', 'https://images.unsplash.com/photo-1551218808-94e220e084d2?auto=format&fit=crop&w=900&q=80', NULL, 'PRODUCT_NOTE', '早午餐,约会,杭州', '座位间距可以，出餐快，甜点比想象中稳，适合周末轻约会。', 492, 26, 0, NOW() - INTERVAL 16 HOUR)
ON DUPLICATE KEY UPDATE
  `title` = VALUES(`title`),
  `images` = VALUES(`images`),
  `video_url` = VALUES(`video_url`),
  `content_type` = VALUES(`content_type`),
  `tags` = VALUES(`tags`),
  `content` = VALUES(`content`),
  `liked` = VALUES(`liked`),
  `comments` = VALUES(`comments`),
  `status` = VALUES(`status`),
  `create_time` = VALUES(`create_time`);

INSERT INTO `tb_blog_product` (`blog_id`, `product_id`, `sort`)
VALUES
  (900009, 900301, 1),
  (900009, 900303, 2),
  (900010, 900401, 1),
  (900010, 900403, 2),
  (900011, 900501, 1),
  (900011, 900504, 2),
  (900014, 900104, 1),
  (900015, 900201, 1),
  (900015, 900204, 2),
  (900016, 900302, 1),
  (900017, 900403, 1),
  (900018, 900205, 1),
  (900019, 900503, 1),
  (900021, 900203, 1),
  (900022, 900402, 1),
  (900023, 900502, 1),
  (900024, 900105, 1)
ON DUPLICATE KEY UPDATE `sort` = VALUES(`sort`);

INSERT INTO `tb_blog_comments` (`id`, `user_id`, `blog_id`, `parent_id`, `answer_id`, `content`, `liked`, `status`)
VALUES
  (900004, 9001, 900009, 0, 0, '这套通勤公式太省时间了，白 T 三件装很实用。', 31, 0),
  (900005, 9004, 900010, 0, 0, '先分类再买盒子这个提醒很关键。', 22, 0),
  (900006, 9007, 900011, 0, 0, '理线夹真的是最便宜但最有效的桌面改造。', 36, 0),
  (900007, 9006, 900012, 0, 0, '路线收藏了，周末不想太赶就照这个走。', 18, 0),
  (900008, 9008, 900014, 0, 0, '冷萃随行瓶这个组合适合夏天通勤。', 25, 0),
  (900009, 9002, 900015, 0, 0, '侧上方 45 度打光学到了，之前总是正面怼。', 44, 0),
  (900010, 9005, 900017, 0, 0, '浴室上墙以后台面真的好擦很多。', 19, 0),
  (900011, 9003, 900019, 0, 0, '三脚架高度比我想象重要，低了视角很怪。', 29, 0)
ON DUPLICATE KEY UPDATE `content` = VALUES(`content`), `liked` = VALUES(`liked`), `status` = VALUES(`status`);

INSERT INTO `tb_video_danmaku`
(`id`, `blog_id`, `user_id`, `content`, `video_second`, `lane`, `status`)
VALUES
  (920001, 900011, 9004, '桌面一下清爽了', 2, 0, 0),
  (920002, 900011, 9005, '无线充这个角度好用', 6, 1, 0),
  (920003, 900012, 9001, '苏州路线收藏', 4, 0, 0),
  (920004, 900014, 9008, '冷萃看起来很适合早八', 3, 2, 0),
  (920005, 900015, 9002, '这个补光对比明显', 5, 1, 0),
  (920006, 900017, 9005, '浴室台面救星', 7, 0, 0),
  (920007, 900018, 9006, '透明袋出差真的方便', 4, 2, 0),
  (920008, 900019, 9007, '拍做饭视频正需要', 8, 1, 0),
  (920009, 900023, 9003, '咖啡店办公同款清单', 6, 3, 0)
ON DUPLICATE KEY UPDATE `content` = VALUES(`content`), `video_second` = VALUES(`video_second`), `lane` = VALUES(`lane`), `status` = VALUES(`status`);

INSERT INTO `tb_video_play_metric`
(`id`, `blog_id`, `user_id`, `duration_second`, `watched_second`, `max_progress`, `completed`)
VALUES
  (925001, 900011, 1, 30, 28, 93, b'1'),
  (925002, 900012, 1, 30, 21, 70, b'0'),
  (925003, 900014, 1, 30, 30, 100, b'1'),
  (925004, 900015, 1, 30, 25, 83, b'1'),
  (925005, 900017, 1, 30, 16, 53, b'0'),
  (925006, 900019, 1, 30, 29, 96, b'1'),
  (925007, 900023, 1, 30, 24, 80, b'0')
ON DUPLICATE KEY UPDATE `watched_second` = VALUES(`watched_second`), `max_progress` = VALUES(`max_progress`), `completed` = VALUES(`completed`);

INSERT INTO `tb_mall_review`
(`id`, `order_id`, `product_id`, `sku_id`, `user_id`, `merchant_id`, `rating`, `content`, `images`, `status`)
VALUES
  (930001, 990000004, 900203, NULL, 1, 9002, 5, '包很轻，放相机和伞以后肩带也不勒。', 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=900&q=80', 0),
  (930002, 990000003, 900201, NULL, 1, 9002, 5, '餐厅黄光环境下确实能救画面，体积也小。', 'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=900&q=80', 0),
  (930003, 990000002, 900103, NULL, 1, 9001, 5, '挂耳风味清楚，路线卡片做得很像礼物。', 'https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?auto=format&fit=crop&w=900&q=80', 0),
  (930004, 990000005, 900102, NULL, 1, 9001, 4, '蛋糕低糖但不寡淡，下午茶刚好。', 'https://images.unsplash.com/photo-1464305795204-6f5bbfc7fb81?auto=format&fit=crop&w=900&q=80', 0)
ON DUPLICATE KEY UPDATE `rating` = VALUES(`rating`), `content` = VALUES(`content`), `status` = VALUES(`status`);

INSERT INTO `tb_mall_favorite` (`user_id`, `target_type`, `target_id`)
VALUES
  (1, 'PRODUCT', 900104),
  (1, 'PRODUCT', 900301),
  (1, 'PRODUCT', 900401),
  (1, 'PRODUCT', 900501),
  (1, 'SHOP', 9001),
  (1, 'SHOP', 9005)
ON DUPLICATE KEY UPDATE `target_id` = VALUES(`target_id`);

INSERT INTO `tb_content_topic` (`keyword`, `heat`, `note_count`)
VALUES
  ('通勤穿搭', 980, 12),
  ('小户型收纳', 860, 9),
  ('桌面改造', 790, 8),
  ('城市漫游', 720, 11),
  ('移动办公', 690, 7),
  ('早午餐', 640, 10),
  ('拍摄装备', 610, 9),
  ('旅行收纳', 590, 8)
ON DUPLICATE KEY UPDATE `heat` = VALUES(`heat`), `note_count` = VALUES(`note_count`);

INSERT INTO `tb_voucher`
(`id`, `shop_id`, `merchant_id`, `product_id`, `title`, `sub_title`, `rules`, `pay_value`, `actual_value`, `type`, `status`, `scope_type`, `category_id`)
VALUES
  (940001, NULL, 9003, NULL, '通勤穿搭满199减30', '服饰包袋专享', '林一通勤衣橱满199元可用', 19900, 3000, 0, 1, 'SHOP', NULL),
  (940002, NULL, 9004, NULL, '家居收纳满80减12', '小家整理专享', '阿宁小家研究所满80元可用', 8000, 1200, 0, 1, 'SHOP', NULL),
  (940003, NULL, 9005, NULL, '数码好物满129减20', '桌面效率装备可用', 'Leo 数码效率店满129元可用', 12900, 2000, 0, 1, 'SHOP', NULL),
  (940004, NULL, NULL, NULL, '平台好物满99减15', '商城通用券', '全平台实物商品满99元可用', 9900, 1500, 0, 1, 'PLATFORM', NULL)
ON DUPLICATE KEY UPDATE `sub_title` = VALUES(`sub_title`), `rules` = VALUES(`rules`), `actual_value` = VALUES(`actual_value`), `status` = VALUES(`status`);

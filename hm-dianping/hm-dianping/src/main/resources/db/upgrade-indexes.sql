-- ============================================================
-- 补全缺失的数据库索引
-- 执行方式：mysql -u root -p xiaohongshu < upgrade-indexes.sql
-- ============================================================

-- tb_blog: 按用户/商户查询频繁，缺少索引
ALTER TABLE `tb_blog` ADD INDEX `idx_blog_user_id` (`user_id`);
ALTER TABLE `tb_blog` ADD INDEX `idx_blog_shop_id` (`shop_id`);

-- tb_blog_comments: 按笔记/用户查询评论频繁，缺少索引
ALTER TABLE `tb_blog_comments` ADD INDEX `idx_comments_blog_id` (`blog_id`);
ALTER TABLE `tb_blog_comments` ADD INDEX `idx_comments_user_id` (`user_id`);

-- tb_sign: 每人每天只能签到一次，需要唯一约束防重
ALTER TABLE `tb_sign` ADD UNIQUE INDEX `uk_user_date` (`user_id`, `date`);

-- tb_voucher: 按商铺查询券频繁，缺少索引
ALTER TABLE `tb_voucher` ADD INDEX `idx_voucher_shop_id` (`shop_id`);

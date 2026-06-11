-- Add role column to tb_user.
-- Usage: mysql -u root -p xiaohongshu < upgrade-roles.sql

SET @column_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'tb_user'
    AND COLUMN_NAME = 'role'
);

SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE `tb_user` ADD COLUMN `role` TINYINT NOT NULL DEFAULT 1 AFTER `icon`',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `tb_user` SET `role` = 1 WHERE `role` = 0;

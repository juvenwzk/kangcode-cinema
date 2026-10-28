-- ============================================================
-- 增量迁移：为业务表增加图片字段（阿里云 OSS 对象存储）
-- 适用：已经执行过 init.sql 的旧库，直接用本脚本补齐，不需要重建表
-- 幂等：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，改用 information_schema 判断后动态执行
-- 用法：mysql -uroot -p movie < sql/migrate-oss-image.sql
-- ============================================================

USE movie;

-- ---------- 1. 用户头像 ----------
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = 'movie' AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'avatar_url') = 0,
    'ALTER TABLE sys_user ADD COLUMN avatar_url VARCHAR(512) DEFAULT NULL COMMENT ''头像 URL（阿里云 OSS）'' AFTER nickname',
    'SELECT ''sys_user.avatar_url 已存在，跳过'''
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. 影院 Logo ----------
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = 'movie' AND TABLE_NAME = 'cinema' AND COLUMN_NAME = 'logo_url') = 0,
    'ALTER TABLE cinema ADD COLUMN logo_url VARCHAR(512) DEFAULT NULL COMMENT ''影院 Logo URL（阿里云 OSS）'' AFTER city',
    'SELECT ''cinema.logo_url 已存在，跳过'''
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. 电影海报 ----------
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = 'movie' AND TABLE_NAME = 'screening' AND COLUMN_NAME = 'poster_url') = 0,
    'ALTER TABLE screening ADD COLUMN poster_url VARCHAR(512) DEFAULT NULL COMMENT ''电影海报 URL（阿里云 OSS）'' AFTER movie_name',
    'SELECT ''screening.poster_url 已存在，跳过'''
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 校验 ----------
SELECT TABLE_NAME AS '表', COLUMN_NAME AS '列', COLUMN_TYPE AS '类型', COLUMN_COMMENT AS '说明'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'movie'
  AND COLUMN_NAME IN ('avatar_url', 'logo_url', 'poster_url')
ORDER BY TABLE_NAME;

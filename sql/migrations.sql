-- ============================================================
-- 已有数据库升级脚本 v2
-- 已用 docker-compose 初始化过库的表结构不会自动更新，
-- 升级时对已有库手动执行一次本脚本即可。
-- 新装库直接使用 schema.sql（已包含以下索引）。
-- ============================================================

-- 热榜排序（ORDER BY like_count DESC）覆盖索引
ALTER TABLE `post`
    ADD INDEX `idx_status_like_created` (`status`, `like_count`, `created_at`);

-- 附近帖子边界盒预筛索引
ALTER TABLE `post`
    ADD INDEX `idx_lat_lng` (`latitude`, `longitude`);

-- ============================================================
-- 老库升级脚本（2026-09-28）
--
-- 背景：schema.sql 后续新增的索引与表没有同步到**已存在**的库（原 migrations.sql 只补了 2 个索引），
--       在做"压测 + 库结构对账"时发现三处漂移，本脚本一次性补齐：
--   1) notification 缺 uk_user_from_type_post
--      → MQ 消费端 insertQuietly() 里「重复通知跳过」形同虚设（库里已实测出现重复通知）
--   2) post 缺 idx_author_status / idx_status_like_created / idx_lat_lng
--      → 我的帖子列表、热榜排序、附近帖子边界盒预筛全部退化为全表扫描
--   3) admin_log 表不存在
--      → 管理后台「操作日志」页与 @AdminLog 切面不可用（写入会报表不存在）
--
-- 说明：本脚本只对**未修复的老库**执行一次；已修复的库重复执行会报
--       "Duplicate key name"，属正常现象（说明已经补过了）。
-- ============================================================

-- 0) 加唯一索引前先清掉历史重复通知（每组保留 id 最小的一条）
DELETE n1 FROM notification n1
  JOIN notification n2
    ON n1.user_id = n2.user_id
   AND n1.from_user_id <=> n2.from_user_id
   AND n1.type = n2.type
   AND n1.post_id <=> n2.post_id
   AND n1.id > n2.id;

-- 1) 通知去重唯一索引
ALTER TABLE `notification`
    ADD UNIQUE INDEX `uk_user_from_type_post` (`user_id`, `from_user_id`, `type`, `post_id`);

-- 2) post 缺失的三个索引
ALTER TABLE `post`
    ADD INDEX `idx_author_status` (`author_id`, `status`),
    ADD INDEX `idx_status_like_created` (`status`, `like_count`, `created_at`),
    ADD INDEX `idx_lat_lng` (`latitude`, `longitude`);

-- 3) admin_log 表（DDL 与 schema.sql 保持一致）
CREATE TABLE `admin_log` (
    `id`             BIGINT          AUTO_INCREMENT PRIMARY KEY,
    `admin_id`       BIGINT          NOT NULL COMMENT '管理员ID',
    `admin_nickname` VARCHAR(50)     DEFAULT NULL COMMENT '管理员昵称',
    `action`         VARCHAR(100)    NOT NULL COMMENT '操作描述',
    `target_type`    VARCHAR(50)     DEFAULT NULL COMMENT '操作对象类型',
    `target_id`      BIGINT          DEFAULT NULL COMMENT '操作对象ID',
    `detail`         TEXT            DEFAULT NULL COMMENT '操作详情(JSON)',
    `ip`             VARCHAR(50)     DEFAULT NULL COMMENT '请求IP',
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX `idx_admin_id` (`admin_id`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员操作日志';

-- 4) 复核（通知 3 个索引 / post 5 个索引 / admin_log 存在）
-- SHOW INDEX FROM `notification`;
-- SHOW INDEX FROM `post`;
-- SHOW TABLES LIKE 'admin_log';

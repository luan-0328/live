-- GeoCommunity 地方话题社区 · 数据库设计 v1.0
-- 引擎：InnoDB · 字符集：utf8mb4（支持中文和emoji）

-- ============================================================
-- 1. 用户表
-- ============================================================
CREATE TABLE `user` (
    `id`              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    `phone`           VARCHAR(20)     NOT NULL COMMENT '手机号',
    `password`        VARCHAR(255)    DEFAULT NULL COMMENT '管理员密码，普通用户为空',
    `nickname`        VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '昵称',
    `avatar`          VARCHAR(500)    DEFAULT NULL COMMENT '头像URL',
    `role`            VARCHAR(20)     NOT NULL DEFAULT 'ROLE_USER' COMMENT 'ROLE_USER / ROLE_ADMIN',
    `follower_count`  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '粉丝数',
    `following_count` INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '关注数',
    `post_count`      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '发帖数',
    `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '1正常 0封禁 -1注销',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- ① 登录/注册时用手机号查用户
    UNIQUE INDEX `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- ============================================================
-- 2. 分类表
-- ============================================================
CREATE TABLE `category` (
    `id`         INT             AUTO_INCREMENT PRIMARY KEY,
    `name`       VARCHAR(50)     NOT NULL COMMENT '分类名',
    `sort`       INT             NOT NULL DEFAULT 0 COMMENT '排序权重，越小越前',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子分类';

-- ============================================================
-- 3. 帖子表（核心表）
-- ============================================================
CREATE TABLE `post` (
    `id`             BIGINT          AUTO_INCREMENT PRIMARY KEY,
    `title`          VARCHAR(200)    NOT NULL DEFAULT '' COMMENT '标题',
    `content`        TEXT            COMMENT '正文',
    `category_id`    INT             NOT NULL COMMENT '分类ID',
    `author_id`      BIGINT          NOT NULL COMMENT '作者ID',
    `images`         JSON            DEFAULT NULL COMMENT '图片URL数组',
    `longitude`      DOUBLE          DEFAULT NULL COMMENT '经度',
    `latitude`       DOUBLE          DEFAULT NULL COMMENT '纬度',
    `like_count`     INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '点赞数',
    `comment_count`  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '评论数',
    `view_count`     INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '浏览数',
    `status`         TINYINT         NOT NULL DEFAULT 1 COMMENT '1正常 -1删除',
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- ② 首页帖子列表：按分类 + 最新/热门排序
    INDEX `idx_category_status_created` (`category_id`, `status`, `created_at`),

    -- ③ 按作者查询帖子（用户主页、我的帖子）
    INDEX `idx_author_status` (`author_id`, `status`),

    -- ③b 热榜排序（like_count DESC）覆盖索引
    INDEX `idx_status_like_created` (`status`, `like_count`, `created_at`),

    -- ③c 附近帖子：边界盒预筛（latitude 先做范围过滤）
    INDEX `idx_lat_lng` (`latitude`, `longitude`),

    -- ④ 搜索帖子（需 ngram 分词器支持中文）
    FULLTEXT INDEX `ft_title_content` (`title`, `content`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子';

-- ============================================================
-- 4. 评论表
-- ============================================================
CREATE TABLE `comment` (
    `id`         BIGINT          AUTO_INCREMENT PRIMARY KEY,
    `post_id`    BIGINT          NOT NULL COMMENT '所属帖子ID',
    `author_id`  BIGINT          NOT NULL COMMENT '评论者ID',
    `parent_id`  BIGINT          DEFAULT NULL COMMENT '所属根评论ID，NULL表示一级评论',
    `reply_to_comment_id` BIGINT DEFAULT NULL COMMENT '实际被回复的评论',
    `reply_to_user_id` BIGINT DEFAULT NULL COMMENT '被回复用户，由服务器确定',
    `content`    TEXT            NOT NULL COMMENT '评论内容',
    `status`     TINYINT         NOT NULL DEFAULT 1 COMMENT '1正常 -1删除',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- ⑤ 查帖子的评论列表，按时间正序
    INDEX `idx_post_status_created` (`post_id`, `status`, `created_at`),
    INDEX `idx_comment_parent_status` (`parent_id`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论';

-- ============================================================
-- 5. 关注表
-- ============================================================
CREATE TABLE `user_follow` (
    `id`          BIGINT      AUTO_INCREMENT PRIMARY KEY,
    `follower_id` BIGINT      NOT NULL COMMENT '关注者',
    `followee_id` BIGINT      NOT NULL COMMENT '被关注者',
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- ⑦ 防重复关注，也用于查我的关注列表（最左前缀 follower_id）
    UNIQUE INDEX `uk_follower_followee` (`follower_id`, `followee_id`),

    -- ⑧ 查我的粉丝列表（唯一索引不覆盖 followee_id 单独查）
    INDEX `idx_followee` (`followee_id`, `created_at`),

    -- 关注自己无意义（接口 1007），数据库层兜底
    CONSTRAINT `ck_no_self_follow` CHECK (`follower_id` != `followee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注';

-- ============================================================
-- 6. 点赞表
-- ============================================================
CREATE TABLE `post_like` (
    `id`         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    `post_id`    BIGINT      NOT NULL COMMENT '帖子ID',
    `user_id`    BIGINT      NOT NULL COMMENT '用户ID',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- ⑩ 防重复点赞 + 查帖子被谁点赞（最左前缀 post_id）
    UNIQUE INDEX `uk_post_user` (`post_id`, `user_id`),

    -- ⑪ 我点赞过的帖子列表（唯一索引不覆盖 user_id 单独查）
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞';

-- ============================================================
-- 7. 收藏表
-- ============================================================
CREATE TABLE `post_favorite` (
    `id`         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    `post_id`    BIGINT      NOT NULL COMMENT '帖子ID',
    `user_id`    BIGINT      NOT NULL COMMENT '用户ID',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- ⑬ 防重复收藏
    UNIQUE INDEX `uk_post_user` (`post_id`, `user_id`),

    -- ⑭ 查我的收藏列表（接口 4.10）
    INDEX `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏';

-- ============================================================
-- 8. 通知表
-- ============================================================
CREATE TABLE `notification` (
    `event_id`    VARCHAR(36)   NOT NULL COMMENT '稳定事件ID',
    `id`          BIGINT        AUTO_INCREMENT PRIMARY KEY,
    `user_id`     BIGINT        NOT NULL COMMENT '接收者ID',
    `from_user_id` BIGINT       DEFAULT NULL COMMENT '触发者ID',
    `type`        VARCHAR(30)   NOT NULL COMMENT 'new_post / reply_post / reply_comment',
    `content`     VARCHAR(500)  NOT NULL COMMENT '通知摘要',
    `post_id`     BIGINT        DEFAULT NULL COMMENT '关联帖子',
    `is_read`     TINYINT       NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- ⑮ 查我的通知列表
    INDEX `idx_user_created` (`user_id`, `created_at`),

    -- ⑯ 查未读通知数（小红点）
    INDEX `idx_user_read` (`user_id`, `is_read`),

    -- 同一事件对同一接收者只通知一次
    UNIQUE INDEX `uk_user_event` (`user_id`, `event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知';

-- ============================================================
-- 9. 举报表
-- ============================================================
CREATE TABLE `report` (
    `id`          BIGINT        AUTO_INCREMENT PRIMARY KEY,
    `reporter_id` BIGINT        NOT NULL COMMENT '举报人ID',
    `target_type` VARCHAR(20)   NOT NULL COMMENT 'post / comment / user',
    `target_id`   BIGINT        NOT NULL COMMENT '被举报对象ID',
    `reason`      VARCHAR(500)  NOT NULL COMMENT '举报原因',
    `status`      TINYINT       NOT NULL DEFAULT 0 COMMENT '0待处理 1已处理 2已驳回',
    `handle_note` VARCHAR(500)  DEFAULT NULL COMMENT '处理备注',
    `handler_id`  BIGINT        DEFAULT NULL COMMENT '处理人(管理员)ID',
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `handled_at`  DATETIME      DEFAULT NULL,

    -- ⑰ 管理员按状态查举报列表
    INDEX `idx_status_created` (`status`, `created_at`),

    -- ⑱ 防重复举报同一对象
    INDEX `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报';

-- ============================================================
-- 10. 管理员操作日志表
-- ============================================================
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

-- ============================================================
-- 初始化数据
-- ============================================================
-- 内置管理员账号由后端 DataInitializer 在启动时创建，密码 BCrypt 存储，不在此处明文写入。

-- 通知事务Outbox：确认投递后删除，未确认记录保留供重试。
CREATE TABLE `notification_outbox` (
    `id` VARCHAR(36) NOT NULL PRIMARY KEY,
    `payload` TEXT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_outbox_created` (`created_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification_failure (
    event_id VARCHAR(36) PRIMARY KEY,
    payload TEXT NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    next_retry_at DATETIME NOT NULL,
    last_error VARCHAR(500),
    created_at DATETIME NOT NULL,
    INDEX idx_failure_due(status,next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 已有库：先完成 migrations_2026-10-03.sql，再执行本脚本一次；新库只用schema.sql。
ALTER TABLE comment ADD COLUMN reply_to_comment_id BIGINT DEFAULT NULL,
    ADD COLUMN reply_to_user_id BIGINT DEFAULT NULL,
    ADD INDEX idx_comment_parent_status(parent_id,status,created_at);
UPDATE comment c JOIN comment p ON c.parent_id=p.id
SET c.reply_to_comment_id=p.id,c.reply_to_user_id=p.author_id;

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

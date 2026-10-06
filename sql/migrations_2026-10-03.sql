-- 已有库升级：在部署新版后端前执行一次。新库直接使用schema.sql。
-- 不重写或删除历史通知；历史记录各自获得独立事件ID。
ALTER TABLE notification ADD COLUMN event_id VARCHAR(36) NULL;
UPDATE notification SET event_id = UUID() WHERE event_id IS NULL;
ALTER TABLE notification
    MODIFY COLUMN event_id VARCHAR(36) NOT NULL,
    DROP INDEX uk_user_from_type_post,
    ADD UNIQUE INDEX uk_user_event (user_id, event_id);
CREATE TABLE IF NOT EXISTS notification_outbox (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    payload TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_outbox_created (created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- RabbitMQ旧的notification.#绑定不会因代码修改自动消失，部署前需移除旧绑定。
-- 旧队列中的消息无eventId，请先停止生产者并排空或导出后迁移重放；不得直接清空业务消息。

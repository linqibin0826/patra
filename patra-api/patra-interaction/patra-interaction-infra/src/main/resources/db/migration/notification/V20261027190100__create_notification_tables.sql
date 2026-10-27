-- ============================================================
-- notification 子域：提醒表（PAP-123，决策 A、D）
-- 按以后通知中心的信箱设计：收件人、类型、触发人、对象、评论、创建时间、已读时间；本版只有站内、只有回复与 @ 三类。
-- 按收件人 user_id 哈希分 16 区（同 collection），所有读写都带 recipient_id，按评论刷新摘录与标删除两条除外。
-- 只存用户 ID；subject_id、comment_id 不建跨 schema 外键。摘录是评论转成的纯文本，提及存 <@ID>（读时换名字）。
-- ============================================================

CREATE TABLE notification.notification
(
    recipient_id       BIGINT          NOT NULL,
    notification_id    BIGINT          NOT NULL,
    type               VARCHAR(32)     NOT NULL,
    actor_id           BIGINT          NOT NULL,
    actor_anonymous    BOOLEAN         NOT NULL,
    subject_id         BIGINT          NOT NULL,
    comment_id         BIGINT          NULL,
    excerpt            TEXT            NULL,
    excerpt_truncated  BOOLEAN         NOT NULL DEFAULT false,
    comment_deleted    BOOLEAN         NOT NULL DEFAULT false,
    created_at         timestamptz(6)  NOT NULL,
    read_at            timestamptz(6)  NULL,
    PRIMARY KEY (recipient_id, notification_id),
    CONSTRAINT ck_notification_type CHECK (type IN ('COMMENT_REPLY', 'POST_COMMENT', 'MENTION')),
    CONSTRAINT ck_notification_deleted_excerpt CHECK (NOT comment_deleted OR excerpt IS NULL)
) PARTITION BY HASH (recipient_id);
CREATE TABLE notification.notification_p00 PARTITION OF notification.notification FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE notification.notification_p01 PARTITION OF notification.notification FOR VALUES WITH (MODULUS 16, REMAINDER 1);
CREATE TABLE notification.notification_p02 PARTITION OF notification.notification FOR VALUES WITH (MODULUS 16, REMAINDER 2);
CREATE TABLE notification.notification_p03 PARTITION OF notification.notification FOR VALUES WITH (MODULUS 16, REMAINDER 3);
CREATE TABLE notification.notification_p04 PARTITION OF notification.notification FOR VALUES WITH (MODULUS 16, REMAINDER 4);
CREATE TABLE notification.notification_p05 PARTITION OF notification.notification FOR VALUES WITH (MODULUS 16, REMAINDER 5);
CREATE TABLE notification.notification_p06 PARTITION OF notification.notification FOR VALUES WITH (MODULUS 16, REMAINDER 6);
CREATE TABLE notification.notification_p07 PARTITION OF notification.notification FOR VALUES WITH (MODULUS 16, REMAINDER 7);
CREATE TABLE notification.notification_p08 PARTITION OF notification.notification FOR VALUES WITH (MODULUS 16, REMAINDER 8);
CREATE TABLE notification.notification_p09 PARTITION OF notification.notification FOR VALUES WITH (MODULUS 16, REMAINDER 9);
CREATE TABLE notification.notification_p10 PARTITION OF notification.notification FOR VALUES WITH (MODULUS 16, REMAINDER 10);
CREATE TABLE notification.notification_p11 PARTITION OF notification.notification FOR VALUES WITH (MODULUS 16, REMAINDER 11);
CREATE TABLE notification.notification_p12 PARTITION OF notification.notification FOR VALUES WITH (MODULUS 16, REMAINDER 12);
CREATE TABLE notification.notification_p13 PARTITION OF notification.notification FOR VALUES WITH (MODULUS 16, REMAINDER 13);
CREATE TABLE notification.notification_p14 PARTITION OF notification.notification FOR VALUES WITH (MODULUS 16, REMAINDER 14);
CREATE TABLE notification.notification_p15 PARTITION OF notification.notification FOR VALUES WITH (MODULUS 16, REMAINDER 15);

COMMENT ON TABLE notification.notification IS 'In-site notifications (mailbox): one row per recipient per event; hash-partitioned by recipient_id into 16';
COMMENT ON COLUMN notification.notification.recipient_id IS 'Recipient user ID (identity); partition key';
COMMENT ON COLUMN notification.notification.notification_id IS 'Snowflake ID; time ordered, also the list cursor';
COMMENT ON COLUMN notification.notification.type IS 'COMMENT_REPLY, POST_COMMENT or MENTION';
COMMENT ON COLUMN notification.notification.actor_id IS 'User who triggered it (identity); never returned when actor_anonymous';
COMMENT ON COLUMN notification.notification.actor_anonymous IS 'Actor is the anonymous owner of the subject; shown as anonymous author';
COMMENT ON COLUMN notification.notification.subject_id IS 'subject.registry local ID; no cross-schema FK';
COMMENT ON COLUMN notification.notification.comment_id IS 'discussion.comment ID; NULL for future non-comment types';
COMMENT ON COLUMN notification.notification.excerpt IS 'Plain-text excerpt of the comment, escaped, mentions as <@ID>; NULL once the comment is deleted';
COMMENT ON COLUMN notification.notification.excerpt_truncated IS 'Whether the stored excerpt was cut';
COMMENT ON COLUMN notification.notification.comment_deleted IS 'The comment was deleted (placeholder or removed)';
COMMENT ON COLUMN notification.notification.created_at IS 'When the triggering comment was published or edited (UTC)';
COMMENT ON COLUMN notification.notification.read_at IS 'When the recipient read it (UTC); NULL = unread';

-- 未读数、全部已读
CREATE INDEX idx_notification_unread ON notification.notification (recipient_id, type) WHERE read_at IS NULL;
-- 一个人对一条评论最多一条提醒（编辑新增 @ 时挡掉已被回复、已是经验作者的人）
CREATE UNIQUE INDEX uk_notification_comment ON notification.notification (recipient_id, comment_id) WHERE comment_id IS NOT NULL;
-- 编辑刷新摘录、删除标记：按评论找全部收件人
CREATE INDEX idx_notification_comment ON notification.notification (comment_id) WHERE comment_id IS NOT NULL;
-- 每日清理 5 个月前的
CREATE INDEX idx_notification_created ON notification.notification (created_at);

-- ============================================================
-- inbox 子域：信箱（PAP-143，工程 spec 2026-10-31-notification-inbox-design 第 6.2 节）
-- 消费 interaction 的事件写入：notification 一行一个收件人一条提醒；comment 一行一条评论（最新正文、版本、墓碑）；
-- subject_tombstone 记已删除的投稿经验。有帮助（PAP-144）也写进 notification（category = HELPFUL）。
-- 只存用户 ID；时间列由代码显式写（注入的时钟），不挂 set_updated_at。
-- ============================================================

-- ------------------------------------------------------------
-- 提醒：按收件人 user_id 哈希分 16 区（列表、未读数、已读都带收件人，只扫一个分区）
-- ------------------------------------------------------------
CREATE TABLE inbox.notification
(
    recipient_id     BIGINT          NOT NULL,
    notification_id  BIGINT          NOT NULL,
    category         VARCHAR(16)     NOT NULL,
    type             VARCHAR(32)     NOT NULL,
    actor_id         BIGINT          NOT NULL,
    actor_anonymous  BOOLEAN         NOT NULL,
    subject_id       BIGINT          NOT NULL,
    comment_id       BIGINT          NULL,
    occurred_at      timestamptz(6)  NOT NULL,
    created_at       timestamptz(6)  NOT NULL,
    read_at          timestamptz(6)  NULL,
    PRIMARY KEY (recipient_id, notification_id),
    CONSTRAINT ck_notification_category CHECK (category IN ('REPLY', 'MENTION', 'HELPFUL')),
    CONSTRAINT ck_notification_type CHECK (type IN ('COMMENT_REPLY', 'POST_COMMENT', 'MENTION')),
    CONSTRAINT ck_notification_category_type CHECK (
        (category = 'REPLY' AND type IN ('COMMENT_REPLY', 'POST_COMMENT'))
            OR (category = 'MENTION' AND type = 'MENTION')),
    CONSTRAINT ck_notification_comment CHECK (category NOT IN ('REPLY', 'MENTION') OR comment_id IS NOT NULL)
) PARTITION BY HASH (recipient_id);
CREATE TABLE inbox.notification_p00 PARTITION OF inbox.notification FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE inbox.notification_p01 PARTITION OF inbox.notification FOR VALUES WITH (MODULUS 16, REMAINDER 1);
CREATE TABLE inbox.notification_p02 PARTITION OF inbox.notification FOR VALUES WITH (MODULUS 16, REMAINDER 2);
CREATE TABLE inbox.notification_p03 PARTITION OF inbox.notification FOR VALUES WITH (MODULUS 16, REMAINDER 3);
CREATE TABLE inbox.notification_p04 PARTITION OF inbox.notification FOR VALUES WITH (MODULUS 16, REMAINDER 4);
CREATE TABLE inbox.notification_p05 PARTITION OF inbox.notification FOR VALUES WITH (MODULUS 16, REMAINDER 5);
CREATE TABLE inbox.notification_p06 PARTITION OF inbox.notification FOR VALUES WITH (MODULUS 16, REMAINDER 6);
CREATE TABLE inbox.notification_p07 PARTITION OF inbox.notification FOR VALUES WITH (MODULUS 16, REMAINDER 7);
CREATE TABLE inbox.notification_p08 PARTITION OF inbox.notification FOR VALUES WITH (MODULUS 16, REMAINDER 8);
CREATE TABLE inbox.notification_p09 PARTITION OF inbox.notification FOR VALUES WITH (MODULUS 16, REMAINDER 9);
CREATE TABLE inbox.notification_p10 PARTITION OF inbox.notification FOR VALUES WITH (MODULUS 16, REMAINDER 10);
CREATE TABLE inbox.notification_p11 PARTITION OF inbox.notification FOR VALUES WITH (MODULUS 16, REMAINDER 11);
CREATE TABLE inbox.notification_p12 PARTITION OF inbox.notification FOR VALUES WITH (MODULUS 16, REMAINDER 12);
CREATE TABLE inbox.notification_p13 PARTITION OF inbox.notification FOR VALUES WITH (MODULUS 16, REMAINDER 13);
CREATE TABLE inbox.notification_p14 PARTITION OF inbox.notification FOR VALUES WITH (MODULUS 16, REMAINDER 14);
CREATE TABLE inbox.notification_p15 PARTITION OF inbox.notification FOR VALUES WITH (MODULUS 16, REMAINDER 15);

COMMENT ON TABLE inbox.notification IS 'In-site notifications: one row per recipient per notification; hash-partitioned by recipient_id into 16';
COMMENT ON COLUMN inbox.notification.recipient_id IS 'Recipient user ID (identity); partition key';
COMMENT ON COLUMN inbox.notification.notification_id IS 'Snowflake ID; tie-breaker of the list order';
COMMENT ON COLUMN inbox.notification.category IS 'Tab: REPLY, MENTION or HELPFUL';
COMMENT ON COLUMN inbox.notification.type IS 'Action: COMMENT_REPLY, POST_COMMENT, MENTION (helpful types added by PAP-144)';
COMMENT ON COLUMN inbox.notification.actor_id IS 'User who triggered it (latest one for merged helpful entries); never returned when actor_anonymous';
COMMENT ON COLUMN inbox.notification.actor_anonymous IS 'Actor is the anonymous owner of the subject; shown as anonymous author';
COMMENT ON COLUMN inbox.notification.subject_id IS 'interaction subject.registry ID; post deletion removes by it';
COMMENT ON COLUMN inbox.notification.comment_id IS 'interaction discussion.comment ID; required for REPLY and MENTION; NULL for helpful on a post';
COMMENT ON COLUMN inbox.notification.occurred_at IS 'Sort time (UTC): when the comment was published or edited; latest click for helpful, so it can move';
COMMENT ON COLUMN inbox.notification.created_at IS 'When the row entered the inbox (UTC); email windows count from it';
COMMENT ON COLUMN inbox.notification.read_at IS 'When the recipient read it (UTC); NULL = unread';

-- 列表与游标：(occurred_at, notification_id) 倒序
CREATE INDEX idx_notification_list ON inbox.notification (recipient_id, category, occurred_at DESC, notification_id DESC);
-- 未读数、全部已读
CREATE INDEX idx_notification_unread ON inbox.notification (recipient_id, category) WHERE read_at IS NULL;
-- 一个人对一条评论最多一条回复或 @ 提醒（既回复又 @ 只一条；重复投递的第二道防线）
CREATE UNIQUE INDEX uk_notification_comment ON inbox.notification (recipient_id, comment_id) WHERE category IN ('REPLY', 'MENTION');
-- 评论删除：按评论删全部收件人的提醒
CREATE INDEX idx_notification_comment ON inbox.notification (comment_id) WHERE comment_id IS NOT NULL;
-- 经验删除：按对象删
CREATE INDEX idx_notification_subject ON inbox.notification (subject_id);
-- 保留期清理
CREATE INDEX idx_notification_occurred ON inbox.notification (occurred_at);

-- ------------------------------------------------------------
-- 评论快照与墓碑：一行一条评论；同一条评论的发表、编辑、删除都先写这一行（行锁让它们互斥）
-- ------------------------------------------------------------
CREATE TABLE inbox.comment
(
    comment_id  BIGINT          NOT NULL,
    subject_id  BIGINT          NOT NULL,
    version     INTEGER         NOT NULL,
    plain_text  TEXT            NULL,
    deleted_at  timestamptz(6)  NULL,
    updated_at  timestamptz(6)  NOT NULL,
    PRIMARY KEY (comment_id),
    CONSTRAINT ck_comment_version CHECK (version >= 0),
    CONSTRAINT ck_comment_tombstone CHECK ((deleted_at IS NULL) = (plain_text IS NOT NULL))
);

COMMENT ON TABLE inbox.comment IS 'Latest plain text of each comment and its tombstone; notifications join it for the excerpt';
COMMENT ON COLUMN inbox.comment.comment_id IS 'interaction discussion.comment ID';
COMMENT ON COLUMN inbox.comment.subject_id IS 'interaction subject.registry ID; post deletion removes by it';
COMMENT ON COLUMN inbox.comment.version IS 'Version of the stored text (1 = published, +1 per edit); 0 = unknown, filled from a helpful event';
COMMENT ON COLUMN inbox.comment.plain_text IS 'Plain text of the latest version (PAP-128), mentions as <@ID>; NULL once deleted';
COMMENT ON COLUMN inbox.comment.deleted_at IS 'Tombstone: when the comment was deleted (UTC); later events for it are discarded';
COMMENT ON COLUMN inbox.comment.updated_at IS 'Last write of this row (UTC, processing time); retention purge uses it';

CREATE INDEX idx_comment_subject ON inbox.comment (subject_id);
CREATE INDEX idx_comment_updated ON inbox.comment (updated_at);

-- ------------------------------------------------------------
-- 已删除的投稿经验：之后它下面评论的事件都丢弃
-- ------------------------------------------------------------
CREATE TABLE inbox.subject_tombstone
(
    subject_id  BIGINT          NOT NULL,
    deleted_at  timestamptz(6)  NOT NULL,
    PRIMARY KEY (subject_id)
);

COMMENT ON TABLE inbox.subject_tombstone IS 'Deleted posts (their own subject); later events of comments under them are discarded';
COMMENT ON COLUMN inbox.subject_tombstone.subject_id IS 'interaction subject.registry ID of the deleted post';
COMMENT ON COLUMN inbox.subject_tombstone.deleted_at IS 'When the post was deleted (UTC, from the event)';

CREATE INDEX idx_subject_tombstone_deleted ON inbox.subject_tombstone (deleted_at);

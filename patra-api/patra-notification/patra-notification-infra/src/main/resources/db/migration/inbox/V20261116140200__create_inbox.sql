-- inbox 子域：提醒信箱、有帮助的参与人、评论副本、帖子墓碑
-- 只存用户 ID；subject_id、comment_id 是 interaction 那边的 ID，引用关系由消费事件的写入路径保证，不建外键。

-- ============================================================
-- ntf_notification：提醒，按 recipient_user_id 哈希分 16 区，主键 (recipient_user_id, id)
-- ============================================================
CREATE TABLE ntf_notification
(
    id                 BIGINT          NOT NULL,
    recipient_user_id  BIGINT          NOT NULL,
    category           VARCHAR(16)     NOT NULL,
    type               VARCHAR(32)     NOT NULL,
    actor_user_id      BIGINT          NOT NULL,
    actor_anonymous    BOOLEAN         NOT NULL,
    subject_id         BIGINT          NOT NULL,
    comment_id         BIGINT          NULL,
    helpful_day        DATE            NULL,
    post_plain_text    TEXT            NULL,
    post_plain_text_at timestamptz(6)  NULL,
    occurred_at        timestamptz(6)  NOT NULL,
    notified_at        timestamptz(6)  NOT NULL,
    read_at            timestamptz(6)  NULL,
    record_remarks     jsonb           NULL,
    ip_address         INET            NULL,
    created_at         timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         BIGINT          NULL,
    updated_at         timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         BIGINT          NULL,
    PRIMARY KEY (recipient_user_id, id),
    CONSTRAINT ck_ntf_notification_category CHECK (category IN ('REPLY', 'MENTION', 'HELPFUL')),
    CONSTRAINT ck_ntf_notification_type CHECK (
        type IN ('COMMENT_REPLY', 'POST_COMMENT', 'MENTION', 'COMMENT_HELPFUL', 'POST_HELPFUL')),
    CONSTRAINT ck_ntf_notification_category_type CHECK (
        (category = 'REPLY' AND type IN ('COMMENT_REPLY', 'POST_COMMENT'))
            OR (category = 'MENTION' AND type = 'MENTION')
            OR (category = 'HELPFUL' AND type IN ('COMMENT_HELPFUL', 'POST_HELPFUL'))),
    CONSTRAINT ck_ntf_notification_comment CHECK (category NOT IN ('REPLY', 'MENTION') OR comment_id IS NOT NULL),
    CONSTRAINT ck_ntf_notification_helpful_day CHECK ((category = 'HELPFUL') = (helpful_day IS NOT NULL)),
    CONSTRAINT ck_ntf_notification_helpful_comment CHECK (
        (type <> 'COMMENT_HELPFUL' OR comment_id IS NOT NULL)
            AND (type <> 'POST_HELPFUL' OR comment_id IS NULL)),
    CONSTRAINT ck_ntf_notification_post_text CHECK (
        (type = 'POST_HELPFUL') = (post_plain_text_at IS NOT NULL)
            AND (post_plain_text IS NULL OR type = 'POST_HELPFUL'))
) PARTITION BY HASH (recipient_user_id);
CREATE TABLE ntf_notification_p00 PARTITION OF ntf_notification FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE ntf_notification_p01 PARTITION OF ntf_notification FOR VALUES WITH (MODULUS 16, REMAINDER 1);
CREATE TABLE ntf_notification_p02 PARTITION OF ntf_notification FOR VALUES WITH (MODULUS 16, REMAINDER 2);
CREATE TABLE ntf_notification_p03 PARTITION OF ntf_notification FOR VALUES WITH (MODULUS 16, REMAINDER 3);
CREATE TABLE ntf_notification_p04 PARTITION OF ntf_notification FOR VALUES WITH (MODULUS 16, REMAINDER 4);
CREATE TABLE ntf_notification_p05 PARTITION OF ntf_notification FOR VALUES WITH (MODULUS 16, REMAINDER 5);
CREATE TABLE ntf_notification_p06 PARTITION OF ntf_notification FOR VALUES WITH (MODULUS 16, REMAINDER 6);
CREATE TABLE ntf_notification_p07 PARTITION OF ntf_notification FOR VALUES WITH (MODULUS 16, REMAINDER 7);
CREATE TABLE ntf_notification_p08 PARTITION OF ntf_notification FOR VALUES WITH (MODULUS 16, REMAINDER 8);
CREATE TABLE ntf_notification_p09 PARTITION OF ntf_notification FOR VALUES WITH (MODULUS 16, REMAINDER 9);
CREATE TABLE ntf_notification_p10 PARTITION OF ntf_notification FOR VALUES WITH (MODULUS 16, REMAINDER 10);
CREATE TABLE ntf_notification_p11 PARTITION OF ntf_notification FOR VALUES WITH (MODULUS 16, REMAINDER 11);
CREATE TABLE ntf_notification_p12 PARTITION OF ntf_notification FOR VALUES WITH (MODULUS 16, REMAINDER 12);
CREATE TABLE ntf_notification_p13 PARTITION OF ntf_notification FOR VALUES WITH (MODULUS 16, REMAINDER 13);
CREATE TABLE ntf_notification_p14 PARTITION OF ntf_notification FOR VALUES WITH (MODULUS 16, REMAINDER 14);
CREATE TABLE ntf_notification_p15 PARTITION OF ntf_notification FOR VALUES WITH (MODULUS 16, REMAINDER 15);
CREATE INDEX idx_ntf_notification_list ON ntf_notification (recipient_user_id, category, occurred_at DESC, id DESC);
CREATE INDEX idx_ntf_notification_unread ON ntf_notification (recipient_user_id, category) WHERE read_at IS NULL;
CREATE UNIQUE INDEX uk_ntf_notification_comment ON ntf_notification (recipient_user_id, comment_id) WHERE category IN ('REPLY', 'MENTION');
CREATE UNIQUE INDEX uk_ntf_notification_helpful ON ntf_notification (recipient_user_id, subject_id, comment_id, helpful_day)
    NULLS NOT DISTINCT WHERE category = 'HELPFUL';
CREATE INDEX idx_ntf_notification_comment ON ntf_notification (comment_id) WHERE comment_id IS NOT NULL;
CREATE INDEX idx_ntf_notification_subject ON ntf_notification (subject_id);
CREATE INDEX idx_ntf_notification_occurred ON ntf_notification (occurred_at);

COMMENT ON TABLE ntf_notification IS '站内提醒：回复、@、有帮助三个标签页，按收件人哈希分 16 区';
COMMENT ON COLUMN ntf_notification.recipient_user_id IS '收件人用户 ID，分区键';
COMMENT ON COLUMN ntf_notification.category IS '标签页：REPLY、MENTION 或 HELPFUL';
COMMENT ON COLUMN ntf_notification.type IS '动作：COMMENT_REPLY、POST_COMMENT、MENTION、COMMENT_HELPFUL 或 POST_HELPFUL';
COMMENT ON COLUMN ntf_notification.actor_user_id IS '触发的人（合并的有帮助取最近一个）；actor_anonymous 时不返回';
COMMENT ON COLUMN ntf_notification.actor_anonymous IS '触发的人是对象的匿名主人，展示成匿名作者';
COMMENT ON COLUMN ntf_notification.subject_id IS 'interaction 的对象 ID（itr_subject.id）；删帖时按它清';
COMMENT ON COLUMN ntf_notification.comment_id IS 'interaction 的评论 ID；回复与 @ 必填，帖子上的有帮助为空';
COMMENT ON COLUMN ntf_notification.helpful_day IS '只用于合并的有帮助：这些点击的北京时间日期';
COMMENT ON COLUMN ntf_notification.post_plain_text IS '只用于 POST_HELPFUL：被点帖子在 post_plain_text_at 时的纯文本，帖子没有正文时为空';
COMMENT ON COLUMN ntf_notification.post_plain_text_at IS '只用于 POST_HELPFUL：post_plain_text 随哪次点击带来（最近一次为准）';
COMMENT ON COLUMN ntf_notification.occurred_at IS '列表排序时间：评论发表或编辑的时间；有帮助取最近一次点击，会挪动';
COMMENT ON COLUMN ntf_notification.notified_at IS '写入信箱的时间，由应用写；离站邮件与每日汇总的时间窗口从它算，和最后在线时间比较';
COMMENT ON COLUMN ntf_notification.read_at IS '收件人读到的时间，未读为空';

CREATE TRIGGER trg_ntf_notification_audit_times
    BEFORE INSERT OR UPDATE ON ntf_notification
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- ntf_notification_helpful_actor：合并的有帮助提醒里每个点过的人
-- 提醒被删时由仓储在同一条语句里一并删（可写 CTE），不靠外键级联
-- ============================================================
CREATE TABLE ntf_notification_helpful_actor
(
    id                BIGINT          NOT NULL,
    recipient_user_id BIGINT          NOT NULL,
    notification_id   BIGINT          NOT NULL,
    actor_user_id     BIGINT          NOT NULL,
    first_marked_at   timestamptz(6)  NOT NULL,
    created_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_ntf_notification_helpful_actor_actor ON ntf_notification_helpful_actor (recipient_user_id, notification_id, actor_user_id);

COMMENT ON TABLE ntf_notification_helpful_actor IS '合并的有帮助提醒的参与人，每个（提醒、点的人）一行';
COMMENT ON COLUMN ntf_notification_helpful_actor.recipient_user_id IS '提醒的收件人用户 ID';
COMMENT ON COLUMN ntf_notification_helpful_actor.notification_id IS '合并的有帮助提醒（ntf_notification.id）';
COMMENT ON COLUMN ntf_notification_helpful_actor.actor_user_id IS '点有帮助的用户 ID；账号注销后保留';
COMMENT ON COLUMN ntf_notification_helpful_actor.first_marked_at IS '这个人在这条提醒上最早的点击时间；取消后再点不往后挪';

CREATE TRIGGER trg_ntf_notification_helpful_actor_audit_times
    BEFORE INSERT OR UPDATE ON ntf_notification_helpful_actor
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- ntf_comment：评论正文副本（展示提醒用），按上游版本只进不退；上游删除后留墓碑
-- ============================================================
CREATE TABLE ntf_comment
(
    id                BIGINT          NOT NULL,
    comment_id        BIGINT          NOT NULL,
    subject_id        BIGINT          NOT NULL,
    source_version    INTEGER         NOT NULL,
    plain_text        TEXT            NULL,
    source_deleted_at timestamptz(6)  NULL,
    synced_at         timestamptz(6)  NOT NULL,
    created_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_ntf_comment_source_version CHECK (source_version >= 0),
    CONSTRAINT ck_ntf_comment_tombstone CHECK ((source_deleted_at IS NULL) = (plain_text IS NOT NULL))
);
CREATE UNIQUE INDEX uk_ntf_comment_comment ON ntf_comment (comment_id);
CREATE INDEX idx_ntf_comment_subject ON ntf_comment (subject_id);
CREATE INDEX idx_ntf_comment_synced ON ntf_comment (synced_at);

COMMENT ON TABLE ntf_comment IS '评论正文副本：展示回复、@ 提醒时用；上游删除后变墓碑，之后的事件丢弃';
COMMENT ON COLUMN ntf_comment.comment_id IS 'interaction 的评论 ID';
COMMENT ON COLUMN ntf_comment.subject_id IS 'interaction 的对象 ID；删帖时按它清';
COMMENT ON COLUMN ntf_comment.source_version IS '副本对应的上游正文版本（发表为 1，每次编辑加 1）；0 表示未知，由有帮助事件补的';
COMMENT ON COLUMN ntf_comment.plain_text IS '最新版本的纯文本，@ 存成 <@ID>；上游删除后为空';
COMMENT ON COLUMN ntf_comment.source_deleted_at IS '墓碑：上游删除这条评论的时间；之后关于它的事件都丢弃';
COMMENT ON COLUMN ntf_comment.synced_at IS '最近一次按上游事件写这一行的时间（插入、版本更新、上游删除），由应用写；过期清理按它删';

CREATE TRIGGER trg_ntf_comment_audit_times
    BEFORE INSERT OR UPDATE ON ntf_comment
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- ntf_subject_tombstone：被删帖子的墓碑，晚到的事件据此丢弃
-- ============================================================
CREATE TABLE ntf_subject_tombstone
(
    id                BIGINT          NOT NULL,
    subject_id        BIGINT          NOT NULL,
    source_deleted_at timestamptz(6)  NOT NULL,
    created_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_ntf_subject_tombstone_subject ON ntf_subject_tombstone (subject_id);
CREATE INDEX idx_ntf_subject_tombstone_deleted ON ntf_subject_tombstone (source_deleted_at);

COMMENT ON TABLE ntf_subject_tombstone IS '被删帖子的墓碑：删帖之后才到的提醒事件据此丢弃';
COMMENT ON COLUMN ntf_subject_tombstone.subject_id IS '被删帖子在 interaction 的对象 ID';
COMMENT ON COLUMN ntf_subject_tombstone.source_deleted_at IS '帖子被删的时间（取自事件）';

CREATE TRIGGER trg_ntf_subject_tombstone_audit_times
    BEFORE INSERT OR UPDATE ON ntf_subject_tombstone
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

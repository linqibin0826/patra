-- ============================================================
-- inbox 子域：有帮助的合并条目（PAP-144，工程 spec 2026-10-31-notification-helpful-design 第 7.1 节）
-- 同一条内容在北京时间同一天被点的所有次数合成 inbox.notification 的一行（category = HELPFUL）；
-- 点过的人与各自第一次点的时刻放附表 inbox.helpful_actor，随信箱行级联删除（评论删除、经验删除、保留期、注销清理都不用改）。
-- 只存用户 ID；时间列由代码显式写。
-- ============================================================

-- ------------------------------------------------------------
-- 信箱行：两个有帮助类型、合并的那一天、点经验时的经验正文
-- ------------------------------------------------------------
ALTER TABLE inbox.notification DROP CONSTRAINT ck_notification_type;
ALTER TABLE inbox.notification ADD CONSTRAINT ck_notification_type CHECK (
    type IN ('COMMENT_REPLY', 'POST_COMMENT', 'MENTION', 'COMMENT_HELPFUL', 'POST_HELPFUL'));

ALTER TABLE inbox.notification DROP CONSTRAINT ck_notification_category_type;
ALTER TABLE inbox.notification ADD CONSTRAINT ck_notification_category_type CHECK (
    (category = 'REPLY' AND type IN ('COMMENT_REPLY', 'POST_COMMENT'))
        OR (category = 'MENTION' AND type = 'MENTION')
        OR (category = 'HELPFUL' AND type IN ('COMMENT_HELPFUL', 'POST_HELPFUL')));

ALTER TABLE inbox.notification ADD COLUMN helpful_day DATE NULL;
ALTER TABLE inbox.notification ADD COLUMN post_plain_text TEXT NULL;
ALTER TABLE inbox.notification ADD COLUMN post_plain_text_at timestamptz(6) NULL;

-- 只有有帮助条目有「那一天」
ALTER TABLE inbox.notification ADD CONSTRAINT ck_notification_helpful_day CHECK (
    (category = 'HELPFUL') = (helpful_day IS NOT NULL));
-- 点评论的有帮助必有评论，点经验的没有
ALTER TABLE inbox.notification ADD CONSTRAINT ck_notification_helpful_comment CHECK (
    (type <> 'COMMENT_HELPFUL' OR comment_id IS NOT NULL)
        AND (type <> 'POST_HELPFUL' OR comment_id IS NULL));
-- 经验正文只在点经验的条目上；它的时刻在点经验的条目上必填（正文本身可空：经验没有文字）
ALTER TABLE inbox.notification ADD CONSTRAINT ck_notification_post_text CHECK (
    (type = 'POST_HELPFUL') = (post_plain_text_at IS NOT NULL)
        AND (post_plain_text IS NULL OR type = 'POST_HELPFUL'));

-- 合并键：一个收件人、一条内容、一天只有一行。点经验时 comment_id 为空，NULLS NOT DISTINCT 让它也算相等
CREATE UNIQUE INDEX uk_notification_helpful ON inbox.notification (recipient_id, subject_id, comment_id, helpful_day)
    NULLS NOT DISTINCT WHERE category = 'HELPFUL';

COMMENT ON COLUMN inbox.notification.type IS 'Action: COMMENT_REPLY, POST_COMMENT, MENTION, COMMENT_HELPFUL or POST_HELPFUL';
COMMENT ON COLUMN inbox.notification.helpful_day IS 'Merged helpful entries only: the Beijing date of the marks (from the event mark time)';
COMMENT ON COLUMN inbox.notification.post_plain_text IS 'POST_HELPFUL only: plain text of the marked post as of post_plain_text_at; NULL when the post has no text';
COMMENT ON COLUMN inbox.notification.post_plain_text_at IS 'POST_HELPFUL only: mark time that post_plain_text came with (latest mark wins)';

-- ------------------------------------------------------------
-- 附表：一个合并条目里点过的人，一人一行，带他第一次点的时刻
-- ------------------------------------------------------------
CREATE TABLE inbox.helpful_actor
(
    recipient_id     BIGINT          NOT NULL,
    notification_id  BIGINT          NOT NULL,
    actor_id         BIGINT          NOT NULL,
    first_marked_at  timestamptz(6)  NOT NULL,
    PRIMARY KEY (recipient_id, notification_id, actor_id),
    CONSTRAINT fk_helpful_actor_notification FOREIGN KEY (recipient_id, notification_id)
        REFERENCES inbox.notification (recipient_id, notification_id) ON DELETE CASCADE
);

COMMENT ON TABLE inbox.helpful_actor IS 'People who marked a merged helpful entry as helpful; deleted with the entry';
COMMENT ON COLUMN inbox.helpful_actor.recipient_id IS 'Recipient of the entry (identity user ID)';
COMMENT ON COLUMN inbox.helpful_actor.notification_id IS 'The merged helpful entry';
COMMENT ON COLUMN inbox.helpful_actor.actor_id IS 'User who marked it (identity user ID); kept after the account is deleted';
COMMENT ON COLUMN inbox.helpful_actor.first_marked_at IS 'Earliest mark time of this user on this entry; re-marks after a cancel do not move it later';

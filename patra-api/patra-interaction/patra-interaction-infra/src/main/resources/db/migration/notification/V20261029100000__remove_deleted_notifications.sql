-- ============================================================
-- notification 子域：内容删除即删提醒（PAP-127）
-- 评论或投稿经验删除时，提醒子域订阅事件直接删掉对应提醒，不再留「已删除」占位，未读数随之一致。
-- 这里清掉按旧做法留下的行，去掉删除标记，并为按对象删提醒补索引。
-- 第 2 步关联 subject.registry 只在这条一次性数据迁移里出现，运行时代码不跨子域读表。
-- ============================================================

-- 1. 已标删除的评论引起的提醒
DELETE FROM notification.notification WHERE comment_deleted;

-- 2. 已删除的投稿经验（登记表 POST、UNAVAILABLE）下的提醒
DELETE FROM notification.notification n
    USING subject.registry r
WHERE n.subject_id = r.subject_id
  AND r.target_type = 3
  AND r.state = 'UNAVAILABLE';

-- 3. 删除标记不再需要
ALTER TABLE notification.notification
    DROP CONSTRAINT ck_notification_deleted_excerpt,
    DROP COLUMN comment_deleted;

-- 4. 经验删除时按对象删提醒（表按收件人分区，没有这条索引会扫全部 16 个分区）
CREATE INDEX idx_notification_subject ON notification.notification (subject_id);

COMMENT ON COLUMN notification.notification.excerpt IS 'Plain-text excerpt of the comment, escaped, mentions as <@ID>';

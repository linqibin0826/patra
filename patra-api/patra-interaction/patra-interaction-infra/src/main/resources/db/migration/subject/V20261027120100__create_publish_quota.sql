-- ============================================================
-- subject.publish_quota：发表守卫的按作者计数（PAP-120，决策 E）
-- 每个作者每种发表一行：当天（北京时间自然日）已发条数与上次发表时刻。编辑不算发表，删除内容不退还额度。
-- 只存用户 ID；注销清理按主键的前导列 user_id 找人。
-- ============================================================
CREATE TABLE subject.publish_quota
(
    user_id            BIGINT          NOT NULL,
    kind               VARCHAR(16)     NOT NULL,
    quota_day          DATE            NOT NULL,
    day_count          INTEGER         NOT NULL,
    last_published_at  timestamptz(6)  NOT NULL,
    PRIMARY KEY (user_id, kind),
    CONSTRAINT ck_publish_quota_kind CHECK (kind IN ('COMMENT', 'POST')),
    CONSTRAINT ck_publish_quota_day_count CHECK (day_count > 0)
);

COMMENT ON TABLE subject.publish_quota IS 'Publish guard counters: one row per (author, kind); rate limits are checked under a per-author advisory lock';
COMMENT ON COLUMN subject.publish_quota.user_id IS 'Author user ID (identity)';
COMMENT ON COLUMN subject.publish_quota.kind IS 'PublishKind: COMMENT (100 per day, 10 s apart) or POST (10 per day)';
COMMENT ON COLUMN subject.publish_quota.quota_day IS 'The Asia/Shanghai calendar day day_count belongs to';
COMMENT ON COLUMN subject.publish_quota.day_count IS 'How many were published on quota_day; deletions never refund';
COMMENT ON COLUMN subject.publish_quota.last_published_at IS 'When the author last published this kind (UTC)';

-- ============================================================
-- subject.publish_quota 加发表种类 HELPFUL（PAP-131，v0.15 有帮助）
-- 有帮助复用发表守卫：每天 300 次、不限间隔，点一次算一次，取消不退。
-- ============================================================
ALTER TABLE subject.publish_quota
    DROP CONSTRAINT ck_publish_quota_kind,
    ADD CONSTRAINT ck_publish_quota_kind CHECK (kind IN ('COMMENT', 'POST', 'HELPFUL'));

COMMENT ON COLUMN subject.publish_quota.kind IS 'PublishKind: COMMENT (100 per day, 10 s apart), POST (10 per day) or HELPFUL (300 per day, no interval)';

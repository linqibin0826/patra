-- ============================================================
-- reaction 子域：有帮助明细、每个目标的有帮助数、每个用户的获赞（PAP-131，release spec v0.15 决策 A、B）
-- 计数与明细同事务维护：明细真插入 / 删除（影响行数 1）才原子 ±1，不按明细重数覆盖。
-- 目标是「评论或帖子 + ID」，ID 是所属子域的主键，不建跨 schema 外键。只存用户 ID。
-- 注销不清理：注销用户点过的与收到的有帮助、获赞全部保留。
-- ============================================================

-- ------------------------------------------------------------
-- 明细：一人对一个目标最多一条
-- ------------------------------------------------------------
CREATE TABLE reaction.helpful
(
    user_id           BIGINT          NOT NULL,
    target_type       VARCHAR(16)     NOT NULL,
    target_id         BIGINT          NOT NULL,
    credited_user_id  BIGINT          NULL,
    created_at        timestamptz(6)  NOT NULL,
    PRIMARY KEY (user_id, target_type, target_id),
    CONSTRAINT ck_helpful_target_type CHECK (target_type IN ('COMMENT', 'POST')),
    CONSTRAINT ck_helpful_not_self CHECK (credited_user_id IS DISTINCT FROM user_id)
);

COMMENT ON TABLE reaction.helpful IS 'Helpful marks: at most one per (user, target); counters in target_stats / user_stats move with inserts and deletes in the same transaction';
COMMENT ON COLUMN reaction.helpful.user_id IS 'Who marked it helpful (identity user ID)';
COMMENT ON COLUMN reaction.helpful.target_type IS 'COMMENT (discussion.comment.comment_id) or POST (post.post.post_id)';
COMMENT ON COLUMN reaction.helpful.target_id IS 'ID of the comment or post; no cross-schema FK';
COMMENT ON COLUMN reaction.helpful.credited_user_id IS 'Whose received-helpful count this mark adds to: the target author; NULL for anonymous posts and comments their anonymous author wrote under them';
COMMENT ON COLUMN reaction.helpful.created_at IS 'When it was marked (UTC)';

CREATE INDEX idx_helpful_target ON reaction.helpful (target_type, target_id);

-- ------------------------------------------------------------
-- 每个目标的有帮助数：有过一次才有行；取消到 0 保留行，目标删除时整行删掉
-- ------------------------------------------------------------
CREATE TABLE reaction.target_stats
(
    target_type    VARCHAR(16)  NOT NULL,
    target_id      BIGINT       NOT NULL,
    helpful_count  INTEGER      NOT NULL,
    PRIMARY KEY (target_type, target_id),
    CONSTRAINT ck_target_stats_type CHECK (target_type IN ('COMMENT', 'POST')),
    CONSTRAINT ck_target_stats_count CHECK (helpful_count >= 0)
);

COMMENT ON TABLE reaction.target_stats IS 'Helpful count per target; a missing row means 0';
COMMENT ON COLUMN reaction.target_stats.target_type IS 'COMMENT or POST, as in reaction.helpful';
COMMENT ON COLUMN reaction.target_stats.target_id IS 'ID of the comment or post';
COMMENT ON COLUMN reaction.target_stats.helpful_count IS 'Number of current helpful marks on the target';

-- ------------------------------------------------------------
-- 每个用户的获赞：第一次被计入时才有行
-- ------------------------------------------------------------
CREATE TABLE reaction.user_stats
(
    user_id           BIGINT  NOT NULL,
    helpful_received  BIGINT  NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT ck_user_stats_received CHECK (helpful_received >= 0)
);

COMMENT ON TABLE reaction.user_stats IS 'Received helpful marks per user (the profile "likes" count); a missing row means 0';
COMMENT ON COLUMN reaction.user_stats.user_id IS 'Target author (identity user ID)';
COMMENT ON COLUMN reaction.user_stats.helpful_received IS 'Helpful marks on the user''s non-deleted, non-anonymous comments and posts';

-- reaction 子域：有帮助明细、每个目标的有帮助数、每个用户收到的有帮助数
-- 计数与明细同事务维护：明细真插入 / 删除（影响行数 1）才原子 ±1，不按明细重数覆盖。
-- 目标是「评论或帖子 + ID」，ID 是所属子域的主键，引用关系由应用保证，不建外键。只存用户 ID。
-- 注销不清理：注销用户点过的与收到的有帮助、获赞全部保留。

-- ============================================================
-- itr_helpful：明细，一人对一个目标最多一条
-- ============================================================
CREATE TABLE itr_helpful
(
    id               BIGINT          NOT NULL,
    user_id          BIGINT          NOT NULL,
    target_type      VARCHAR(16)     NOT NULL,
    target_id        BIGINT          NOT NULL,
    credited_user_id BIGINT          NULL,
    created_at       timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_itr_helpful_target_type CHECK (target_type IN ('COMMENT', 'POST')),
    CONSTRAINT ck_itr_helpful_not_self CHECK (credited_user_id IS DISTINCT FROM user_id)
);
CREATE UNIQUE INDEX uk_itr_helpful_user_target ON itr_helpful (user_id, target_type, target_id);
CREATE INDEX idx_itr_helpful_target ON itr_helpful (target_type, target_id);

COMMENT ON TABLE itr_helpful IS '有帮助明细：一人对一个目标最多一条；两张计数表随插入、删除同事务变化';
COMMENT ON COLUMN itr_helpful.user_id IS '点有帮助的人';
COMMENT ON COLUMN itr_helpful.target_type IS '目标种类：COMMENT（itr_comment）或 POST（itr_post）';
COMMENT ON COLUMN itr_helpful.target_id IS '评论或帖子的 ID';
COMMENT ON COLUMN itr_helpful.credited_user_id IS '这条计入谁的获赞：目标作者；匿名帖子、匿名作者在自己帖子下的评论为空';

CREATE TRIGGER trg_itr_helpful_audit_times
    BEFORE INSERT OR UPDATE ON itr_helpful
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- itr_helpful_target_stats：每个目标的有帮助数
-- 有过一次才有行；取消到 0 保留行，目标删除时整行删掉
-- ============================================================
CREATE TABLE itr_helpful_target_stats
(
    id            BIGINT          NOT NULL,
    target_type   VARCHAR(16)     NOT NULL,
    target_id     BIGINT          NOT NULL,
    helpful_count INTEGER         NOT NULL DEFAULT 0,
    created_at    timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_itr_helpful_target_stats_type CHECK (target_type IN ('COMMENT', 'POST')),
    CONSTRAINT ck_itr_helpful_target_stats_count CHECK (helpful_count >= 0)
);
CREATE UNIQUE INDEX uk_itr_helpful_target_stats_target ON itr_helpful_target_stats (target_type, target_id);

COMMENT ON TABLE itr_helpful_target_stats IS '每个目标的有帮助数；没有行表示 0';
COMMENT ON COLUMN itr_helpful_target_stats.target_type IS '目标种类：COMMENT 或 POST，同 itr_helpful';
COMMENT ON COLUMN itr_helpful_target_stats.target_id IS '评论或帖子的 ID';
COMMENT ON COLUMN itr_helpful_target_stats.helpful_count IS '目标当前的有帮助条数';

CREATE TRIGGER trg_itr_helpful_target_stats_audit_times
    BEFORE INSERT OR UPDATE ON itr_helpful_target_stats
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- itr_helpful_user_stats：每个用户收到的有帮助数，第一次被计入时才有行
-- ============================================================
CREATE TABLE itr_helpful_user_stats
(
    id               BIGINT          NOT NULL,
    user_id          BIGINT          NOT NULL,
    helpful_received BIGINT          NOT NULL DEFAULT 0,
    created_at       timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_itr_helpful_user_stats_received CHECK (helpful_received >= 0)
);
CREATE UNIQUE INDEX uk_itr_helpful_user_stats_user ON itr_helpful_user_stats (user_id);

COMMENT ON TABLE itr_helpful_user_stats IS '每个用户收到的有帮助数（个人主页的获赞）；没有行表示 0';
COMMENT ON COLUMN itr_helpful_user_stats.user_id IS '目标作者的用户 ID';
COMMENT ON COLUMN itr_helpful_user_stats.helpful_received IS '用户未删除、非匿名的评论和帖子上的有帮助条数';

CREATE TRIGGER trg_itr_helpful_user_stats_audit_times
    BEFORE INSERT OR UPDATE ON itr_helpful_user_stats
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

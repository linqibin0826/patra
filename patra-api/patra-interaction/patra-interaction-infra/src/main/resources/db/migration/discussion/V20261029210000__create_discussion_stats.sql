-- ============================================================
-- discussion 子域：讨论串统计（PAP-132，release spec v0.15 §2.1.3、决策 A）
-- 楼统计与对象统计由发表、删除评论的事务同步 ±1，不按明细重数覆盖。「未删除」= state = 'ACTIVE'，占位不算。
-- 没有行等于 0（最后回复时间为空）；不对其他 schema 建外键。
-- ============================================================

-- ------------------------------------------------------------
-- 楼统计：一楼一行，第一次有回复时建行，减到 0 时保留，根评论整条去掉时删行
-- ------------------------------------------------------------
CREATE TABLE discussion.thread_stats
(
    root_id        BIGINT          NOT NULL,
    reply_count    INTEGER         NOT NULL,
    last_reply_at  timestamptz(6)  NULL,
    PRIMARY KEY (root_id),
    CONSTRAINT ck_thread_stats_count CHECK (reply_count >= 0),
    CONSTRAINT ck_thread_stats_last CHECK ((reply_count = 0) = (last_reply_at IS NULL))
);

COMMENT ON TABLE discussion.thread_stats IS 'Per-thread counters kept in the comment write transaction; no row means no replies';
COMMENT ON COLUMN discussion.thread_stats.root_id IS 'Root comment of the thread (discussion.comment.comment_id)';
COMMENT ON COLUMN discussion.thread_stats.reply_count IS 'ACTIVE replies at any depth under the root';
COMMENT ON COLUMN discussion.thread_stats.last_reply_at IS 'Latest created_at among those replies (UTC); NULL when there are none';

-- ------------------------------------------------------------
-- 对象统计：文献的讨论数与投稿经验的回复数都是它；行建了就不删
-- ------------------------------------------------------------
CREATE TABLE discussion.subject_stats
(
    subject_id     BIGINT   NOT NULL,
    comment_count  INTEGER  NOT NULL,
    PRIMARY KEY (subject_id),
    CONSTRAINT ck_subject_stats_count CHECK (comment_count >= 0)
);

COMMENT ON TABLE discussion.subject_stats IS 'Per-subject comment counter kept in the comment write transaction; no row means 0';
COMMENT ON COLUMN discussion.subject_stats.subject_id IS 'subject.registry local ID; no cross-schema FK';
COMMENT ON COLUMN discussion.subject_stats.comment_count IS 'ACTIVE root comments and replies under the subject';

-- ------------------------------------------------------------
-- 回填：与按明细重算同一口径，写成幂等 upsert
-- ------------------------------------------------------------
INSERT INTO discussion.thread_stats (root_id, reply_count, last_reply_at)
SELECT root_id,
       count(*) FILTER (WHERE state = 'ACTIVE'),
       max(created_at) FILTER (WHERE state = 'ACTIVE')
  FROM discussion.comment
 WHERE parent_id IS NOT NULL
 GROUP BY root_id
ON CONFLICT (root_id) DO UPDATE
   SET reply_count = EXCLUDED.reply_count, last_reply_at = EXCLUDED.last_reply_at;

INSERT INTO discussion.subject_stats (subject_id, comment_count)
SELECT subject_id, count(*) FILTER (WHERE state = 'ACTIVE')
  FROM discussion.comment
 GROUP BY subject_id
ON CONFLICT (subject_id) DO UPDATE SET comment_count = EXCLUDED.comment_count;

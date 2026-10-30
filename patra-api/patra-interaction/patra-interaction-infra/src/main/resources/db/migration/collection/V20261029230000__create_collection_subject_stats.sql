-- ============================================================
-- collection 子域：每个对象的收藏人数（PAP-133，release spec v0.15 §2.1.3、决策 A、C）
-- 收藏人数 = 这个对象的文献库条目数（一人放进多个收藏夹只算一次）。
-- 与文献库条目同事务维护：条目真插入 / 真删除（RETURNING 拿到的行）才原子 ±1，不按明细重数覆盖。
-- 没有行 = 0；减到 0 保留行。subject_id 不对 subject.registry 建外键（决策 B）。
-- ============================================================

CREATE TABLE collection.subject_stats
(
    subject_id       BIGINT   NOT NULL,
    collector_count  INTEGER  NOT NULL,
    PRIMARY KEY (subject_id),
    CONSTRAINT ck_subject_stats_collectors CHECK (collector_count >= 0)
);

COMMENT ON TABLE collection.subject_stats IS 'Collector count per subject; a missing row means 0; moves with library_item inserts and deletes in the same transaction';
COMMENT ON COLUMN collection.subject_stats.subject_id IS 'subject.registry local ID; no cross-schema FK';
COMMENT ON COLUMN collection.subject_stats.collector_count IS 'Number of library_item rows for the subject (users who collected it)';

-- 回填：部署时旧容器已停、新容器启动才迁移，迁移期间没有写入
INSERT INTO collection.subject_stats (subject_id, collector_count)
SELECT subject_id, count(*) FROM collection.library_item GROUP BY subject_id;

-- insight 子域：热读榜、看过还看了的快照，以及每种快照的最近一次计算

-- ============================================================
-- anl_hot_reading：热读榜快照，每次计算整表替换
-- ============================================================
CREATE TABLE anl_hot_reading
(
    id             BIGINT            NOT NULL,
    position       SMALLINT          NOT NULL,
    publication_id BIGINT            NOT NULL,
    ranked         BOOLEAN           NOT NULL,
    score          DOUBLE PRECISION  NULL,
    readers_7d     INTEGER           NULL,
    created_at     timestamptz(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     timestamptz(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_anl_hot_reading_position ON anl_hot_reading (position);
CREATE UNIQUE INDEX uk_anl_hot_reading_publication ON anl_hot_reading (publication_id);

COMMENT ON TABLE anl_hot_reading IS '热读榜快照：按近 7 天阅读算出的前几名，不足时用最新收录补位';
COMMENT ON COLUMN anl_hot_reading.position IS '名次，从 1 起，页面按它排';
COMMENT ON COLUMN anl_hot_reading.publication_id IS 'catalog 的文献 ID';
COMMENT ON COLUMN anl_hot_reading.ranked IS '是否靠热度上榜；false 表示最新收录补位';
COMMENT ON COLUMN anl_hot_reading.score IS '热度分，补位为空';
COMMENT ON COLUMN anl_hot_reading.readers_7d IS '近 7 天的读者数，补位为空';

CREATE TRIGGER trg_anl_hot_reading_audit_times
    BEFORE INSERT OR UPDATE ON anl_hot_reading
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- anl_co_reading：看过还看了的快照，每篇文献最多 20 条，每次计算整表替换
-- ============================================================
CREATE TABLE anl_co_reading
(
    id                     BIGINT            NOT NULL,
    publication_id         BIGINT            NOT NULL,
    position               SMALLINT          NOT NULL,
    related_publication_id BIGINT            NOT NULL,
    similarity             DOUBLE PRECISION  NOT NULL,
    co_readers             INTEGER           NOT NULL,
    created_at     timestamptz(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     timestamptz(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_anl_co_reading_publication_position ON anl_co_reading (publication_id, position);

COMMENT ON TABLE anl_co_reading IS '看过还看了：读过这篇的人还读了哪些，按相似度排';
COMMENT ON COLUMN anl_co_reading.publication_id IS 'catalog 的文献 ID';
COMMENT ON COLUMN anl_co_reading.position IS '名次，1～20';
COMMENT ON COLUMN anl_co_reading.related_publication_id IS '一起被读的文献 ID';
COMMENT ON COLUMN anl_co_reading.similarity IS '相似度';
COMMENT ON COLUMN anl_co_reading.co_readers IS '共同读者数，只供排查，不对外';

CREATE TRIGGER trg_anl_co_reading_audit_times
    BEFORE INSERT OR UPDATE ON anl_co_reading
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- anl_snapshot_run：每种快照最近一次计算的时间与行数
-- ============================================================
CREATE TABLE anl_snapshot_run
(
    id             BIGINT            NOT NULL,
    kind           VARCHAR(16)       NOT NULL,
    computed_at    timestamptz(6)    NOT NULL,
    row_count      INTEGER           NOT NULL DEFAULT 0,
    created_at     timestamptz(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     timestamptz(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_anl_snapshot_run_kind CHECK (kind IN ('HOT_READING', 'CO_READING'))
);
CREATE UNIQUE INDEX uk_anl_snapshot_run_kind ON anl_snapshot_run (kind);

COMMENT ON TABLE anl_snapshot_run IS '每种快照最近一次计算：页面据此显示数据更新时间，运维据此看任务是否在跑';
COMMENT ON COLUMN anl_snapshot_run.kind IS '快照种类：HOT_READING 或 CO_READING';
COMMENT ON COLUMN anl_snapshot_run.computed_at IS '这次计算完成的时间';
COMMENT ON COLUMN anl_snapshot_run.row_count IS '这次写入的行数';

CREATE TRIGGER trg_anl_snapshot_run_audit_times
    BEFORE INSERT OR UPDATE ON anl_snapshot_run
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

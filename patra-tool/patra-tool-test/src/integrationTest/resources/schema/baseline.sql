-- SchemaStandardIT 的基线：一套完全符合库表规范的表，每个用例在它上面只做一处改动。
-- 登记文件见同目录的 tables.yml。框架表 flyway_schema_history 故意不带 id、前缀和注释，用来证明框架表只查登记。

CREATE FUNCTION set_audit_times() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.created_at := now();
        NEW.updated_at := NEW.created_at;
        RETURN NEW;
    END IF;
    NEW.created_at := OLD.created_at;
    NEW.updated_at := OLD.updated_at;
    IF NEW IS DISTINCT FROM OLD THEN
        NEW.updated_at := now();
    END IF;
    RETURN NEW;
END
$$;

-- 主表：带全套审计列、乐观锁、软删除，一个部分唯一索引和一个检查约束
CREATE TABLE tst_note
(
    id             BIGINT      NOT NULL PRIMARY KEY,
    title          TEXT        NOT NULL,
    status         VARCHAR(16) NOT NULL,
    deleted_at     TIMESTAMPTZ NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     BIGINT      NULL,
    updated_by     BIGINT      NULL,
    ip_address     INET        NULL,
    record_remarks JSONB       NULL,
    version        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_tst_note_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);
CREATE UNIQUE INDEX uk_tst_note_title ON tst_note (title) WHERE deleted_at IS NULL;
CREATE TRIGGER trg_tst_note_audit_times
    BEFORE INSERT OR UPDATE ON tst_note
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();
COMMENT ON TABLE tst_note IS '测试用笔记';
COMMENT ON COLUMN tst_note.title IS '标题';
COMMENT ON COLUMN tst_note.status IS '状态';
COMMENT ON COLUMN tst_note.deleted_at IS '删除时间，空表示未删除';

-- 其他表：只有 id 和两个审计时间
CREATE TABLE tst_note_tag
(
    id         BIGINT      NOT NULL PRIMARY KEY,
    note_id    BIGINT      NOT NULL,
    tag        TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tst_note_tag_note ON tst_note_tag (note_id);
CREATE TRIGGER trg_tst_note_tag_audit_times
    BEFORE INSERT OR UPDATE ON tst_note_tag
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();
COMMENT ON TABLE tst_note_tag IS '笔记的标签';
COMMENT ON COLUMN tst_note_tag.note_id IS '所属笔记';
COMMENT ON COLUMN tst_note_tag.tag IS '标签文字';

-- 分区表：主键是分区键加 id，分区子表不登记
CREATE TABLE tst_event
(
    user_id    BIGINT      NOT NULL,
    id         BIGINT      NOT NULL,
    kind       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, id)
) PARTITION BY HASH (user_id);
CREATE TABLE tst_event_p00 PARTITION OF tst_event FOR VALUES WITH (MODULUS 2, REMAINDER 0);
CREATE TABLE tst_event_p01 PARTITION OF tst_event FOR VALUES WITH (MODULUS 2, REMAINDER 1);
CREATE UNIQUE INDEX uk_tst_event_kind ON tst_event (user_id, kind);
CREATE TRIGGER trg_tst_event_audit_times
    BEFORE INSERT OR UPDATE ON tst_event
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();
COMMENT ON TABLE tst_event IS '按用户分区的事件';
COMMENT ON COLUMN tst_event.user_id IS '用户 ID，也是分区键';
COMMENT ON COLUMN tst_event.kind IS '事件类型';

-- 框架表：结构由框架决定，规范只要求它在 public 且已登记
CREATE TABLE flyway_schema_history
(
    installed_rank INTEGER      NOT NULL PRIMARY KEY,
    version        VARCHAR(50)  NULL,
    description    VARCHAR(200) NOT NULL,
    installed_on   TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX flyway_schema_history_s_idx ON flyway_schema_history (installed_rank);

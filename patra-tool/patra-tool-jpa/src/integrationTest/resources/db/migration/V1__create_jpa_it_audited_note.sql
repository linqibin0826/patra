-- starter-jpa 集成测试专用表：主表的列与 BaseJpaEntity 一一对应，审计时间由触发器维护（库表规范第 3、4 节）
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

CREATE TABLE jpa_it_audited_note
(
    id             BIGINT       NOT NULL PRIMARY KEY,
    content        VARCHAR(200) NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     BIGINT       NULL,
    updated_by     BIGINT       NULL,
    ip_address     INET         NULL,
    record_remarks JSONB        NULL,
    version        BIGINT       NOT NULL DEFAULT 0
);
CREATE TRIGGER trg_jpa_it_audited_note_audit_times
    BEFORE INSERT OR UPDATE ON jpa_it_audited_note
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

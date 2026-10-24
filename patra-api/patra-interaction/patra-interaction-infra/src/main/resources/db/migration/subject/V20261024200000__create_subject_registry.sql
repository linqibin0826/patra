-- ============================================================
-- subject.registry：对象登记表（PAP-112）
-- 每个被互动过的外部对象一行；互动数据只引用 subject_id。
-- 身份锚定在 natural_key（文献：pmid:…）上，source_id 只是 catalog 内部 ID 的缓存，每日对账刷新。
-- ============================================================

-- set_updated_at()：每个 schema 一份，schema 整体搬走时不依赖 public
CREATE OR REPLACE FUNCTION subject.set_updated_at() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
    NEW.updated_at = now();
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE subject.registry
(
    subject_id   BIGINT          NOT NULL,
    target_type  SMALLINT        NOT NULL,
    natural_key  VARCHAR(64)     NOT NULL,
    source_id    BIGINT          NULL,
    state        VARCHAR(16)     NOT NULL,
    snapshot     jsonb           NOT NULL,
    snapshot_at  timestamptz(6)  NOT NULL,
    created_at   timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (subject_id),
    CONSTRAINT ck_registry_target_type CHECK (target_type IN (1)),
    CONSTRAINT ck_registry_state CHECK (state IN ('ACTIVE', 'UNAVAILABLE')),
    CONSTRAINT ck_registry_source_present CHECK (source_id IS NOT NULL OR state = 'UNAVAILABLE'),
    CONSTRAINT ck_registry_publication_key CHECK (target_type <> 1 OR natural_key ~ '^pmid:[1-9][0-9]{0,14}$')
);

COMMENT ON TABLE subject.registry IS 'Subject registry: one row per external object that has been interacted with; interaction data references subject_id only';
COMMENT ON COLUMN subject.registry.subject_id IS 'PK (snowflake), stable across catalog re-imports';
COMMENT ON COLUMN subject.registry.target_type IS 'SubjectType code: 1 = PUBLICATION; never reused';
COMMENT ON COLUMN subject.registry.natural_key IS 'External identity anchor, e.g. pmid:12345678';
COMMENT ON COLUMN subject.registry.source_id IS 'Cached catalog internal ID; null when the object vanished from catalog';
COMMENT ON COLUMN subject.registry.state IS 'ACTIVE or UNAVAILABLE (soft-deleted or vanished in catalog; can revive)';
COMMENT ON COLUMN subject.registry.snapshot IS 'Display snapshot {title, journal, publishedDate}: fallback, unavailable display and sort keys';
COMMENT ON COLUMN subject.registry.snapshot_at IS 'When the snapshot was taken from catalog (UTC)';
COMMENT ON COLUMN subject.registry.created_at IS 'Creation time (UTC)';
COMMENT ON COLUMN subject.registry.updated_at IS 'Last update time (UTC)';

CREATE UNIQUE INDEX uk_registry_natural_key ON subject.registry (target_type, natural_key);
CREATE UNIQUE INDEX uk_registry_source_id ON subject.registry (target_type, source_id) WHERE source_id IS NOT NULL;

CREATE TRIGGER trg_registry_updated_at
    BEFORE UPDATE ON subject.registry
    FOR EACH ROW EXECUTE FUNCTION subject.set_updated_at();

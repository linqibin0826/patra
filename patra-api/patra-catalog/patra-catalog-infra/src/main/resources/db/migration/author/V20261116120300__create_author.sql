-- author 子域：作者、名字变体、ORCID
-- 引用关系由应用保证，不建外键

-- ------------------------------------------------------------
-- cat_author
-- ------------------------------------------------------------
CREATE TABLE cat_author
(
    id BIGINT NOT NULL PRIMARY KEY,
    normalized_key VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    provenance_code VARCHAR(32) NOT NULL,
    last_synced_at TIMESTAMPTZ NULL,
    ext_data JSONB NULL,
    deleted_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT      NULL,
    updated_by BIGINT      NULL,
    ip_address INET        NULL,
    record_remarks JSONB       NULL,
    version BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_cat_author_display_name ON cat_author (display_name);
CREATE INDEX idx_cat_author_display_name_lower ON cat_author (lower((display_name)::text));
CREATE INDEX idx_cat_author_normalized_key ON cat_author (normalized_key);
CREATE INDEX idx_cat_author_provenance ON cat_author (provenance_code);
CREATE INDEX idx_cat_author_status ON cat_author (status);
CREATE TRIGGER trg_cat_author_audit_times
    BEFORE INSERT OR UPDATE ON cat_author
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_author IS '作者';
COMMENT ON COLUMN cat_author.normalized_key IS '姓名规范化格式，对应 PubMed Computed Authors 的 name 字段';
COMMENT ON COLUMN cat_author.display_name IS '展示名称（从首个名字变体派生）';
COMMENT ON COLUMN cat_author.status IS '作者状态';
COMMENT ON COLUMN cat_author.provenance_code IS '数据来源代码';
COMMENT ON COLUMN cat_author.last_synced_at IS '最后同步时间（UTC，微秒精度）';
COMMENT ON COLUMN cat_author.ext_data IS '扩展数据（JSON 格式，预留 ORCID API 补充信息）';
COMMENT ON COLUMN cat_author.deleted_at IS '删除时间，空表示未删除';

-- ------------------------------------------------------------
-- cat_author_name_variant
-- ------------------------------------------------------------
CREATE TABLE cat_author_name_variant
(
    id BIGINT NOT NULL PRIMARY KEY,
    author_id BIGINT NOT NULL,
    last_name VARCHAR(200) NULL,
    fore_name VARCHAR(200) NULL,
    initials VARCHAR(10) NULL,
    full_string VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_author_name_variant_author_full ON cat_author_name_variant (author_id, full_string);
CREATE INDEX idx_cat_author_name_variant_last_name_lower ON cat_author_name_variant (lower((last_name)::text));
CREATE INDEX idx_cat_author_name_variant_author ON cat_author_name_variant (author_id);
CREATE INDEX idx_cat_author_name_variant_last_name ON cat_author_name_variant (last_name);
CREATE TRIGGER trg_cat_author_name_variant_audit_times
    BEFORE INSERT OR UPDATE ON cat_author_name_variant
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_author_name_variant IS '作者名字变体';
COMMENT ON COLUMN cat_author_name_variant.author_id IS '所属作者（cat_author.id）';
COMMENT ON COLUMN cat_author_name_variant.last_name IS '姓（Last Name/Family Name）';
COMMENT ON COLUMN cat_author_name_variant.fore_name IS '名（First Name/Given Name，可选）';
COMMENT ON COLUMN cat_author_name_variant.initials IS '姓名缩写（如 "Z", "JK"）';
COMMENT ON COLUMN cat_author_name_variant.full_string IS '原始字符串（如 "Lu,Zhiyong,Z"）';

-- ------------------------------------------------------------
-- cat_author_orcid
-- ------------------------------------------------------------
CREATE TABLE cat_author_orcid
(
    id BIGINT NOT NULL PRIMARY KEY,
    author_id BIGINT NOT NULL,
    orcid VARCHAR(19) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_author_orcid_value ON cat_author_orcid (orcid);
CREATE INDEX idx_cat_author_orcid_author ON cat_author_orcid (author_id);
CREATE TRIGGER trg_cat_author_orcid_audit_times
    BEFORE INSERT OR UPDATE ON cat_author_orcid
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_author_orcid IS '作者 ORCID';
COMMENT ON COLUMN cat_author_orcid.author_id IS '所属作者（cat_author.id）';
COMMENT ON COLUMN cat_author_orcid.orcid IS 'ORCID 标识符（格式: 0000-0001-2345-6789）';
COMMENT ON COLUMN cat_author_orcid.is_primary IS '是否为主要 ORCID';

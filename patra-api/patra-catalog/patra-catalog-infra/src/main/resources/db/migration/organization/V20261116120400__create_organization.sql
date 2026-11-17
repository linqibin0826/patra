-- organization 子域：机构（ROR）及其名称、外部标识符、关系、位置、中文别名
-- 引用关系由应用保证，不建外键

-- ------------------------------------------------------------
-- cat_organization
-- ------------------------------------------------------------
CREATE TABLE cat_organization
(
    id BIGINT NOT NULL PRIMARY KEY,
    ror_id VARCHAR(50) NOT NULL,
    display_name VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    established INTEGER NULL,
    types JSONB NULL,
    domains JSONB NULL,
    links JSONB NULL,
    admin_info JSONB NULL,
    dedup_key VARCHAR(32) NULL,
    metadata JSONB NULL,
    deleted_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT      NULL,
    updated_by BIGINT      NULL,
    ip_address INET        NULL,
    record_remarks JSONB       NULL,
    version BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_cat_organization_ror ON cat_organization (ror_id);
CREATE INDEX idx_cat_organization_dedup_key ON cat_organization (dedup_key);
CREATE INDEX idx_cat_organization_display_name ON cat_organization (display_name);
CREATE INDEX idx_cat_organization_status ON cat_organization (status);
CREATE TRIGGER trg_cat_organization_audit_times
    BEFORE INSERT OR UPDATE ON cat_organization
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_organization IS '机构';
COMMENT ON COLUMN cat_organization.ror_id IS 'ROR ID（唯一标识符，如 "03vek6s52"）';
COMMENT ON COLUMN cat_organization.display_name IS '显示名称（主名称）';
COMMENT ON COLUMN cat_organization.status IS '机构状态：ACTIVE/INACTIVE/WITHDRAWN';
COMMENT ON COLUMN cat_organization.established IS '成立年份';
COMMENT ON COLUMN cat_organization.types IS '机构类型集合（JSON 数组）';
COMMENT ON COLUMN cat_organization.domains IS '域名列表（JSON 数组）';
COMMENT ON COLUMN cat_organization.links IS '网站链接（JSON 数组）';
COMMENT ON COLUMN cat_organization.admin_info IS 'ROR 管理元数据（JSON 对象）';
COMMENT ON COLUMN cat_organization.dedup_key IS '去重键（MD5 哈希）';
COMMENT ON COLUMN cat_organization.metadata IS '扩展元数据（JSON）';
COMMENT ON COLUMN cat_organization.deleted_at IS '删除时间，空表示未删除';

-- ------------------------------------------------------------
-- cat_organization_name
-- ------------------------------------------------------------
CREATE TABLE cat_organization_name
(
    id BIGINT NOT NULL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    name_value VARCHAR(500) NOT NULL,
    types JSONB NULL,
    lang VARCHAR(10) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_organization_name_value ON cat_organization_name (organization_id, name_value, lang);
CREATE INDEX idx_cat_organization_name_organization ON cat_organization_name (organization_id);
CREATE INDEX idx_cat_organization_name_value ON cat_organization_name (name_value);
CREATE INDEX idx_cat_organization_name_value_trgm ON cat_organization_name USING gin (name_value gin_trgm_ops);
CREATE TRIGGER trg_cat_organization_name_audit_times
    BEFORE INSERT OR UPDATE ON cat_organization_name
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_organization_name IS '机构名称';
COMMENT ON COLUMN cat_organization_name.organization_id IS '所属机构 ID（逻辑外键）';
COMMENT ON COLUMN cat_organization_name.name_value IS '名称文本';
COMMENT ON COLUMN cat_organization_name.types IS '名称类型集合（JSON 数组）';
COMMENT ON COLUMN cat_organization_name.lang IS '语言代码（ISO 639-1/639-3）';

-- ------------------------------------------------------------
-- cat_organization_external_id
-- ------------------------------------------------------------
CREATE TABLE cat_organization_external_id
(
    id BIGINT NOT NULL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    id_type VARCHAR(20) NOT NULL,
    all_values JSONB NOT NULL,
    preferred_value VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_organization_external_id_organization_type ON cat_organization_external_id (organization_id, id_type);
CREATE INDEX idx_cat_organization_external_id_organization ON cat_organization_external_id (organization_id);
CREATE INDEX idx_cat_organization_external_id_preferred ON cat_organization_external_id (preferred_value);
CREATE TRIGGER trg_cat_organization_external_id_audit_times
    BEFORE INSERT OR UPDATE ON cat_organization_external_id
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_organization_external_id IS '机构外部标识符';
COMMENT ON COLUMN cat_organization_external_id.organization_id IS '所属机构 ID（逻辑外键）';
COMMENT ON COLUMN cat_organization_external_id.id_type IS '标识符类型代码：GRID/ISNI/WIKIDATA/FUNDREF/RINGGOLD';
COMMENT ON COLUMN cat_organization_external_id.all_values IS '所有标识符值（JSON 数组）';
COMMENT ON COLUMN cat_organization_external_id.preferred_value IS '首选标识符值';

-- ------------------------------------------------------------
-- cat_organization_relation
-- ------------------------------------------------------------
CREATE TABLE cat_organization_relation
(
    id BIGINT NOT NULL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    relation_type VARCHAR(20) NOT NULL,
    related_ror_id VARCHAR(50) NOT NULL,
    related_label VARCHAR(500) NULL,
    related_organization_id BIGINT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_organization_relation_related ON cat_organization_relation (organization_id, relation_type, related_ror_id);
CREATE INDEX idx_cat_organization_relation_organization ON cat_organization_relation (organization_id);
CREATE INDEX idx_cat_organization_relation_related_organization ON cat_organization_relation (related_organization_id);
CREATE INDEX idx_cat_organization_relation_related_ror ON cat_organization_relation (related_ror_id);
CREATE TRIGGER trg_cat_organization_relation_audit_times
    BEFORE INSERT OR UPDATE ON cat_organization_relation
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_organization_relation IS '机构关系';
COMMENT ON COLUMN cat_organization_relation.organization_id IS '源机构 ID（逻辑外键）';
COMMENT ON COLUMN cat_organization_relation.relation_type IS '关系类型代码：PARENT/CHILD/RELATED/SUCCESSOR/PREDECESSOR';
COMMENT ON COLUMN cat_organization_relation.related_ror_id IS '关联机构的 ROR ID';
COMMENT ON COLUMN cat_organization_relation.related_label IS '关联机构的显示名称（冗余）';
COMMENT ON COLUMN cat_organization_relation.related_organization_id IS '关联机构的内部 ID（延迟填充）';

-- ------------------------------------------------------------
-- cat_organization_location
-- ------------------------------------------------------------
CREATE TABLE cat_organization_location
(
    id BIGINT NOT NULL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    geonames_id INTEGER NULL,
    continent_code VARCHAR(2) NULL,
    continent_name VARCHAR(50) NULL,
    country_code VARCHAR(2) NULL,
    country_name VARCHAR(100) NULL,
    subdivision_code VARCHAR(10) NULL,
    subdivision_name VARCHAR(100) NULL,
    city_name VARCHAR(200) NULL,
    latitude NUMERIC(10,7) NULL,
    longitude NUMERIC(10,7) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cat_organization_location_organization_geonames ON cat_organization_location (organization_id, geonames_id);
CREATE INDEX idx_cat_organization_location_country ON cat_organization_location (country_code);
CREATE INDEX idx_cat_organization_location_geonames ON cat_organization_location (geonames_id);
CREATE INDEX idx_cat_organization_location_organization ON cat_organization_location (organization_id);
CREATE TRIGGER trg_cat_organization_location_audit_times
    BEFORE INSERT OR UPDATE ON cat_organization_location
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_organization_location IS '机构地理位置';
COMMENT ON COLUMN cat_organization_location.organization_id IS '所属机构 ID（逻辑外键）';
COMMENT ON COLUMN cat_organization_location.geonames_id IS 'GeoNames 地理位置 ID';
COMMENT ON COLUMN cat_organization_location.continent_code IS '洲代码（如 NA=北美洲、AS=亚洲）';
COMMENT ON COLUMN cat_organization_location.continent_name IS '洲名称';
COMMENT ON COLUMN cat_organization_location.country_code IS '国家代码（ISO 3166-1 alpha-2，如 US、CN）';
COMMENT ON COLUMN cat_organization_location.country_name IS '国家名称';
COMMENT ON COLUMN cat_organization_location.subdivision_code IS '省/州代码（ISO 3166-2，如 US-CA、CN-BJ）';
COMMENT ON COLUMN cat_organization_location.subdivision_name IS '省/州名称';
COMMENT ON COLUMN cat_organization_location.city_name IS '城市名称';
COMMENT ON COLUMN cat_organization_location.latitude IS '纬度（-90 到 +90）';
COMMENT ON COLUMN cat_organization_location.longitude IS '经度（-180 到 +180）';

-- ------------------------------------------------------------
-- cat_organization_alias
-- ------------------------------------------------------------
CREATE TABLE cat_organization_alias
(
    id BIGINT NOT NULL PRIMARY KEY,
    ror_id VARCHAR(50) NOT NULL,
    alias VARCHAR(500) NOT NULL,
    lang VARCHAR(10) NULL,
    alias_type VARCHAR(20) NOT NULL,
    source VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_cat_organization_alias_source CHECK (((source)::text = ANY ((ARRAY['SEED'::character varying, 'WIKIDATA'::character varying])::text[]))),
    CONSTRAINT ck_cat_organization_alias_type CHECK (((alias_type)::text = ANY ((ARRAY['LABEL'::character varying, 'ALIAS'::character varying])::text[])))
);
CREATE UNIQUE INDEX uk_cat_organization_alias_ror_alias ON cat_organization_alias (ror_id, alias);
CREATE INDEX idx_cat_organization_alias_ror ON cat_organization_alias (ror_id);
CREATE INDEX idx_cat_organization_alias_trgm ON cat_organization_alias USING gin (alias gin_trgm_ops);
CREATE UNIQUE INDEX uk_cat_organization_alias_zh_label ON cat_organization_alias (ror_id) WHERE (((alias_type)::text = 'LABEL'::text) AND ((lang)::text = 'zh'::text));
CREATE TRIGGER trg_cat_organization_alias_audit_times
    BEFORE INSERT OR UPDATE ON cat_organization_alias
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE cat_organization_alias IS 'Patra 自维护的机构别名：补 ROR 缺的中文名与简称，只参与建议匹配与中文名取值';
COMMENT ON COLUMN cat_organization_alias.ror_id IS '9 位裸 ROR ID，与 cat_organization.ror_id 对应（逻辑外键，ROR 重导不影响）';
COMMENT ON COLUMN cat_organization_alias.alias IS '别名原文';
COMMENT ON COLUMN cat_organization_alias.lang IS 'ISO 639-1 语言代码，中文为 zh';
COMMENT ON COLUMN cat_organization_alias.alias_type IS 'LABEL = 可作显示名的全名（lang 为 zh 时优先于 ROR 的中文名）；ALIAS = 简称、别名、旧名，只参与匹配';
COMMENT ON COLUMN cat_organization_alias.source IS 'SEED = 手工种子；WIKIDATA = 从 Wikidata 取的 zh 标签与别名';

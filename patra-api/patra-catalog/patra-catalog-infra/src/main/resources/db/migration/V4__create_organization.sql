-- ============================================================
-- Patra Catalog — Organization + Investigator PostgreSQL baseline
-- 来源: V1.3.0 (organization 5 表) + V1.3.1 (investigator 3 表) = 8 表
-- 特别改动:
--   - cat_organization_name.value → name_value（PG 保留字，§5.3）
--     含约束 uk_org_name_value 与索引 idx_org_name_value 同步重命名
-- ============================================================

-- ============================================================
-- 表 1: cat_organization (机构主表)
-- ============================================================

CREATE TABLE cat_organization (
    id              BIGINT          NOT NULL,
    ror_id          VARCHAR(50)     NOT NULL,
    display_name    VARCHAR(500)    NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    established     INTEGER         NULL,
    types           JSONB           NULL,
    domains         JSONB           NULL,
    links           JSONB           NULL,
    admin_info      JSONB           NULL,
    dedup_key       VARCHAR(32)     NULL,
    metadata        JSONB           NULL,
    record_remarks  TEXT            NULL,
    created_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      BYTEA           NULL,
    deleted_at      TIMESTAMPTZ(6)  NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_ror_id UNIQUE (ror_id)
);

CREATE INDEX idx_org_display_name ON cat_organization (display_name);
CREATE INDEX idx_org_status       ON cat_organization (status);
CREATE INDEX idx_org_dedup_key    ON cat_organization (dedup_key);

CREATE TRIGGER trg_cat_organization_updated_at
BEFORE UPDATE ON cat_organization
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 2: cat_organization_name (机构名称表)
-- value → name_value（PG 保留字冲突，§5.3）
-- 含约束 uk_org_name_value 与索引 idx_org_name_value 同步更名
-- ============================================================

CREATE TABLE cat_organization_name (
    id          BIGINT          NOT NULL,
    org_id      BIGINT          NOT NULL,
    name_value  VARCHAR(500)    NOT NULL,
    types       JSONB           NULL,
    lang        VARCHAR(10)     NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_org_name_value UNIQUE (org_id, name_value, lang)
);

CREATE INDEX idx_org_name_org_id    ON cat_organization_name (org_id);
CREATE INDEX idx_org_name_value     ON cat_organization_name (name_value);


-- ============================================================
-- 表 3: cat_organization_external_id (机构外部标识符表)
-- ============================================================

CREATE TABLE cat_organization_external_id (
    id               BIGINT          NOT NULL,
    org_id           BIGINT          NOT NULL,
    id_type          VARCHAR(20)     NOT NULL,
    all_values       JSONB           NOT NULL,
    preferred_value  VARCHAR(100)    NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_org_ext_id_type UNIQUE (org_id, id_type)
);

CREATE INDEX idx_org_ext_id_org_id    ON cat_organization_external_id (org_id);
CREATE INDEX idx_org_ext_id_preferred ON cat_organization_external_id (preferred_value);


-- ============================================================
-- 表 4: cat_organization_relation (机构关系表)
-- ============================================================

CREATE TABLE cat_organization_relation (
    id             BIGINT          NOT NULL,
    org_id         BIGINT          NOT NULL,
    relation_type  VARCHAR(20)     NOT NULL,
    related_ror_id VARCHAR(50)     NOT NULL,
    related_label  VARCHAR(500)    NULL,
    related_org_id BIGINT          NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_org_relation UNIQUE (org_id, relation_type, related_ror_id)
);

CREATE INDEX idx_org_rel_org_id         ON cat_organization_relation (org_id);
CREATE INDEX idx_org_rel_related_ror_id ON cat_organization_relation (related_ror_id);
CREATE INDEX idx_org_rel_related_org_id ON cat_organization_relation (related_org_id);


-- ============================================================
-- 表 5: cat_organization_location (机构地理位置表)
-- ============================================================

CREATE TABLE cat_organization_location (
    id               BIGINT          NOT NULL,
    org_id           BIGINT          NOT NULL,
    geonames_id      INTEGER         NULL,
    continent_code   VARCHAR(2)      NULL,
    continent_name   VARCHAR(50)     NULL,
    country_code     VARCHAR(2)      NULL,
    country_name     VARCHAR(100)    NULL,
    subdivision_code VARCHAR(10)     NULL,
    subdivision_name VARCHAR(100)    NULL,
    city_name        VARCHAR(200)    NULL,
    latitude         NUMERIC(10,7)   NULL,
    longitude        NUMERIC(10,7)   NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_org_location UNIQUE (org_id, geonames_id)
);

CREATE INDEX idx_org_loc_org_id   ON cat_organization_location (org_id);
CREATE INDEX idx_org_loc_country  ON cat_organization_location (country_code);
CREATE INDEX idx_org_loc_geonames ON cat_organization_location (geonames_id);


-- ============================================================
-- 表 6: cat_investigator (研究者表)
-- ============================================================

CREATE TABLE cat_investigator (
    id                  BIGINT          NOT NULL,
    last_name           VARCHAR(200)    NULL,
    fore_name           VARCHAR(200)    NULL,
    initials            VARCHAR(50)     NULL,
    suffix              VARCHAR(50)     NULL,
    orcid               VARCHAR(50)     NULL,
    researcher_id       VARCHAR(100)    NULL,
    investigator_type   VARCHAR(100)    NULL,
    affiliation_name    VARCHAR(500)    NULL,
    email               VARCHAR(255)    NULL,
    dedup_key           VARCHAR(255)    NULL,
    metadata            JSONB           NULL,
    record_remarks      TEXT            NULL,
    created_at          TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    created_by          BIGINT          NULL,
    created_by_name     VARCHAR(100)    NULL,
    updated_at          TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_by          BIGINT          NULL,
    updated_by_name     VARCHAR(100)    NULL,
    version             BIGINT          NOT NULL DEFAULT 0,
    ip_address          BYTEA           NULL,

    PRIMARY KEY (id)
);

CREATE INDEX idx_inv_orcid     ON cat_investigator (orcid);
CREATE INDEX idx_inv_dedup_key ON cat_investigator (dedup_key);
CREATE INDEX idx_inv_email     ON cat_investigator (email);

CREATE TRIGGER trg_cat_investigator_updated_at
BEFORE UPDATE ON cat_investigator
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 7: cat_publication_investigator (文献-研究者关联表)
-- ============================================================

CREATE TABLE cat_publication_investigator (
    id              BIGINT          NOT NULL,
    publication_id  BIGINT          NOT NULL,
    investigator_id BIGINT          NOT NULL,
    role            VARCHAR(100)    NULL,
    is_contact      BOOLEAN         NOT NULL DEFAULT false,
    order_num       INTEGER         NULL,
    responsibility  VARCHAR(1000)   NULL,
    metadata        JSONB           NULL,
    record_remarks  TEXT            NULL,
    created_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      BYTEA           NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_pub_investigator UNIQUE (publication_id, investigator_id)
);

CREATE INDEX idx_inv_rel_publication  ON cat_publication_investigator (publication_id);
CREATE INDEX idx_inv_rel_investigator ON cat_publication_investigator (investigator_id);
CREATE INDEX idx_inv_rel_role         ON cat_publication_investigator (role);

CREATE TRIGGER trg_cat_publication_investigator_updated_at
BEFORE UPDATE ON cat_publication_investigator
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 8: cat_publication_personal_name_subject (人物主题表)
-- ============================================================

CREATE TABLE cat_publication_personal_name_subject (
    id              BIGINT          NOT NULL,
    publication_id  BIGINT          NOT NULL,
    last_name       VARCHAR(200)    NULL,
    fore_name       VARCHAR(200)    NULL,
    initials        VARCHAR(50)     NULL,
    suffix          VARCHAR(100)    NULL,
    dates           VARCHAR(100)    NULL,
    description     VARCHAR(500)    NULL,
    subject_type    VARCHAR(50)     NULL,
    identifier      VARCHAR(100)    NULL,
    order_num       INTEGER         NULL,
    metadata        JSONB           NULL,
    record_remarks  TEXT            NULL,
    created_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      BYTEA           NULL,

    PRIMARY KEY (id)
);

CREATE INDEX idx_pns_publication ON cat_publication_personal_name_subject (publication_id);
CREATE INDEX idx_pns_subject_type ON cat_publication_personal_name_subject (subject_type);
CREATE INDEX idx_pns_last_name    ON cat_publication_personal_name_subject (last_name);

CREATE TRIGGER trg_cat_publication_personal_name_subject_updated_at
BEFORE UPDATE ON cat_publication_personal_name_subject
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

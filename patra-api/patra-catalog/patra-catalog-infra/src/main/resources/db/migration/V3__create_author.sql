-- ============================================================
-- Patra Catalog — Author 聚合 PostgreSQL baseline
-- 来源: V1.2.0 (author 3 表) + V1.2.1 (2 表) = 5 表
-- 特别改动:
--   - 删除 idx_publication (cat_publication_author) 冗余索引（§4.50）
--     被 uk_pub_author 首列覆盖
--   - 删除 idx_pub_author (cat_publication_author_affiliation) 冗余索引（§4.50）
--     被 uk_pub_author_order 首列覆盖
--   - 追加 2 个函数索引，支持 Spring Data IgnoreCase 派生方法（§4.53）
-- ============================================================

-- ============================================================
-- 表 1: cat_author (作者表 - 聚合根)
-- ============================================================

CREATE TABLE cat_author (
    id              BIGINT          NOT NULL,
    normalized_key  VARCHAR(100)    NOT NULL,
    display_name    VARCHAR(200)    NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    provenance_code VARCHAR(32)     NOT NULL,
    last_synced_at  TIMESTAMPTZ(6)  NULL,
    ext_data        JSONB           NULL,
    record_remarks  JSONB           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      BYTEA           NULL,
    created_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,
    deleted_at      TIMESTAMPTZ(6)  NULL,

    PRIMARY KEY (id)
);

CREATE INDEX idx_author_normalized_key ON cat_author (normalized_key);
CREATE INDEX idx_author_status         ON cat_author (status);
CREATE INDEX idx_author_provenance     ON cat_author (provenance_code);
CREATE INDEX idx_author_display_name   ON cat_author (display_name);

CREATE TRIGGER trg_cat_author_updated_at
BEFORE UPDATE ON cat_author
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 2: cat_author_name_variant (作者名字变体表)
-- ============================================================

CREATE TABLE cat_author_name_variant (
    id          BIGINT          NOT NULL,
    author_id   BIGINT          NOT NULL,
    last_name   VARCHAR(200)    NULL,
    fore_name   VARCHAR(200)    NULL,
    initials    VARCHAR(10)     NULL,
    full_string VARCHAR(300)    NOT NULL,
    created_at  TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    version     BIGINT          NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    CONSTRAINT uk_author_full UNIQUE (author_id, full_string)
);

CREATE INDEX idx_variant_author_id ON cat_author_name_variant (author_id);
CREATE INDEX idx_variant_last_name ON cat_author_name_variant (last_name);

CREATE TRIGGER trg_cat_author_name_variant_updated_at
BEFORE UPDATE ON cat_author_name_variant
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 3: cat_author_orcid (作者ORCID表)
-- ============================================================

CREATE TABLE cat_author_orcid (
    id          BIGINT          NOT NULL,
    author_id   BIGINT          NOT NULL,
    orcid       VARCHAR(19)     NOT NULL,
    is_primary  BOOLEAN         NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    version     BIGINT          NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    CONSTRAINT uk_orcid UNIQUE (orcid)
);

CREATE INDEX idx_orcid_author_id ON cat_author_orcid (author_id);

CREATE TRIGGER trg_cat_author_orcid_updated_at
BEFORE UPDATE ON cat_author_orcid
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 4: cat_publication_author (文献-作者关联表)
-- 冗余索引 idx_publication 已删除（§4.50），被 uk_pub_author 首列覆盖
-- ============================================================

CREATE TABLE cat_publication_author (
    id                       BIGINT          NOT NULL,
    publication_id           BIGINT          NOT NULL,
    author_id                BIGINT          NOT NULL,
    author_order             INTEGER         NOT NULL DEFAULT 1,
    is_first_author          BOOLEAN         NOT NULL DEFAULT false,
    is_corresponding_author  BOOLEAN         NOT NULL DEFAULT false,
    is_equal_contribution    BOOLEAN         NOT NULL DEFAULT false,
    email                    VARCHAR(255)    NULL,
    author_metadata          JSONB           NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_pub_author   UNIQUE (publication_id, author_id),
    CONSTRAINT uk_author_order UNIQUE (publication_id, author_order)
);

CREATE INDEX idx_pub_author_author    ON cat_publication_author (author_id);
CREATE INDEX idx_first_author         ON cat_publication_author (is_first_author);
CREATE INDEX idx_corresponding        ON cat_publication_author (is_corresponding_author);


-- ============================================================
-- 表 5: cat_publication_author_affiliation (作者-机构归属表)
-- 冗余索引 idx_pub_author 已删除（§4.50），被 uk_pub_author_order 首列覆盖
-- ============================================================

CREATE TABLE cat_publication_author_affiliation (
    id                      BIGINT          NOT NULL,
    pub_author_id           BIGINT          NOT NULL,
    publication_id          BIGINT          NOT NULL,
    author_id               BIGINT          NOT NULL,
    affiliation_order       INTEGER         NOT NULL DEFAULT 1,
    affiliation_string      VARCHAR(2000)   NOT NULL,
    ror_id                  VARCHAR(50)     NULL,
    ringgold_id             VARCHAR(20)     NULL,
    grid_id                 VARCHAR(50)     NULL,
    organization_id         BIGINT          NULL,
    disambiguation_status   VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    disambiguation_method   VARCHAR(50)     NULL,
    disambiguation_score    NUMERIC(5,4)    NULL,
    disambiguated_at        TIMESTAMPTZ(6)  NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_pub_author_order UNIQUE (pub_author_id, affiliation_order)
);

CREATE INDEX idx_affil_publication     ON cat_publication_author_affiliation (publication_id);
CREATE INDEX idx_affil_author          ON cat_publication_author_affiliation (author_id);
CREATE INDEX idx_affil_organization    ON cat_publication_author_affiliation (organization_id);
CREATE INDEX idx_affil_ror_id          ON cat_publication_author_affiliation (ror_id);
CREATE INDEX idx_affil_ringgold_id     ON cat_publication_author_affiliation (ringgold_id);
CREATE INDEX idx_affil_disambig_status ON cat_publication_author_affiliation (disambiguation_status);


-- ============================================================
-- 函数索引：支持 Spring Data 派生方法 findByXxxContainingIgnoreCase（§4.53）
-- PG 下 Hibernate 生成 LOWER(col) LIKE LOWER(?)，B-tree 索引必须建在函数列上
-- ============================================================

CREATE INDEX idx_author_display_name_lower
    ON cat_author (lower(display_name));

CREATE INDEX idx_author_variant_last_name_lower
    ON cat_author_name_variant (lower(last_name));

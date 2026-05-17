-- ============================================================
-- Patra Catalog — Publication 集大成 PostgreSQL baseline
-- 来源: V1.1.0 (3 表) + V1.1.1 (4 表) + V1.1.2 (1 表)
--       + V1.4.3 (1 表) + V1.5.1 (4 表) = 13 表
-- 特别改动:
--   - cat_publication_identifier.value → identifier_value（PG 保留字）
--   - 删除 2 个 FULLTEXT INDEX（ft_title / ft_plain_text）
-- ============================================================

-- ============================================================
-- 表 1: cat_publication (出版物主表 - 聚合根)
-- 生成列: language_base = split_part(language_code, '-', 1)
-- FULLTEXT ft_title 已删除 (§4.19)
-- ============================================================

CREATE TABLE cat_publication (
    id                  BIGINT                      NOT NULL,
    provenance_code     VARCHAR(32)                 NOT NULL,
    pmid                VARCHAR(15)                 NULL,
    doi                 VARCHAR(200)                NULL,
    venue_id            BIGINT                      NULL,
    venue_instance_id   BIGINT                      NULL,
    title               VARCHAR(2000)               NOT NULL,
    original_title      VARCHAR(1000)               NULL,
    language_raw        VARCHAR(50)                 NULL,
    language_code       VARCHAR(10)                 NULL,
    language_base       VARCHAR(5) GENERATED ALWAYS AS (split_part(language_code, '-', 1)) STORED,
    publication_status  VARCHAR(32)                 NULL,
    media_type          VARCHAR(32)                 NULL,
    publication_year    SMALLINT                    NULL,
    is_oa               BOOLEAN                     NOT NULL DEFAULT false,
    oa_status           VARCHAR(20)                 NULL,
    authors_complete    BOOLEAN                     NOT NULL DEFAULT true,
    citation_count      INTEGER                     NULL DEFAULT 0,
    number_of_references INTEGER                   NULL DEFAULT 0,
    conflict_of_interest VARCHAR(500)               NULL,
    ext_data            JSONB                       NULL,
    last_synced_at      TIMESTAMPTZ(6)              NULL,
    record_remarks      JSONB                       NULL,
    version             BIGINT                      NOT NULL DEFAULT 0,
    ip_address          BYTEA                       NULL,
    created_at          TIMESTAMPTZ(6)              NOT NULL DEFAULT now(),
    created_by          BIGINT                      NULL,
    created_by_name     VARCHAR(100)                NULL,
    updated_at          TIMESTAMPTZ(6)              NOT NULL DEFAULT now(),
    updated_by          BIGINT                      NULL,
    updated_by_name     VARCHAR(100)                NULL,
    deleted_at          TIMESTAMPTZ(6)              NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_pmid UNIQUE (pmid),
    CONSTRAINT uk_doi  UNIQUE (doi)
);

CREATE INDEX idx_pub_provenance_code  ON cat_publication (provenance_code);
CREATE INDEX idx_pub_venue            ON cat_publication (venue_id);
CREATE INDEX idx_pub_venue_instance   ON cat_publication (venue_instance_id);
CREATE INDEX idx_pub_publication_year ON cat_publication (publication_year);
CREATE INDEX idx_pub_language_base    ON cat_publication (language_base);
CREATE INDEX idx_pub_is_oa            ON cat_publication (is_oa);

CREATE TRIGGER trg_cat_publication_updated_at
BEFORE UPDATE ON cat_publication
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 2: cat_publication_identifier (标识符表)
-- value → identifier_value (§5.3, PG 保留字冲突)
-- ============================================================

CREATE TABLE cat_publication_identifier (
    id              BIGINT          NOT NULL,
    publication_id  BIGINT          NOT NULL,
    type            VARCHAR(20)     NOT NULL,
    identifier_value VARCHAR(255)   NOT NULL,
    source          VARCHAR(50)     NULL,
    record_remarks  JSONB           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      BYTEA           NULL,
    created_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,

    PRIMARY KEY (id)
);

CREATE INDEX idx_pub_type       ON cat_publication_identifier (publication_id, type);
CREATE INDEX idx_type_id_value  ON cat_publication_identifier (type, identifier_value);

CREATE TRIGGER trg_cat_publication_identifier_updated_at
BEFORE UPDATE ON cat_publication_identifier
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 3: cat_publication_abstract (摘要表)
-- FULLTEXT ft_plain_text 已删除 (§4.19)
-- ============================================================

CREATE TABLE cat_publication_abstract (
    id                  BIGINT          NOT NULL,
    publication_id      BIGINT          NOT NULL,
    plain_text          TEXT            NULL,
    structured_sections JSONB           NULL,
    copyright           VARCHAR(1000)   NULL,
    abstract_type       VARCHAR(32)     NULL,
    record_remarks      JSONB           NULL,
    version             BIGINT          NOT NULL DEFAULT 0,
    ip_address          BYTEA           NULL,
    created_at          TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    created_by          BIGINT          NULL,
    created_by_name     VARCHAR(100)    NULL,
    updated_at          TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_by          BIGINT          NULL,
    updated_by_name     VARCHAR(100)    NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_pub_abstract UNIQUE (publication_id)
);

CREATE TRIGGER trg_cat_publication_abstract_updated_at
BEFORE UPDATE ON cat_publication_abstract
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 4: cat_publication_date (日期信息表)
-- ============================================================

CREATE TABLE cat_publication_date (
    id              BIGINT          NOT NULL,
    publication_id  BIGINT          NOT NULL,
    date_type       VARCHAR(50)     NOT NULL,
    date_value      DATE            NULL,
    year            SMALLINT        NOT NULL,
    month           SMALLINT        NULL,
    day             SMALLINT        NULL,
    date_precision  VARCHAR(10)     NOT NULL DEFAULT 'year',
    season          VARCHAR(100)    NULL,
    date_string     VARCHAR(200)    NULL,
    is_primary      BOOLEAN         NOT NULL DEFAULT false,
    order_num       INTEGER         NULL,
    metadata        JSONB           NULL,
    record_remarks  JSONB           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      BYTEA           NULL,
    created_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,

    PRIMARY KEY (id)
);

CREATE INDEX idx_date_publication ON cat_publication_date (publication_id);
CREATE INDEX idx_date_type        ON cat_publication_date (date_type);
CREATE INDEX idx_date_year        ON cat_publication_date (year);
CREATE INDEX idx_date_value       ON cat_publication_date (date_value);

CREATE TRIGGER trg_cat_publication_date_updated_at
BEFORE UPDATE ON cat_publication_date
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 5: cat_publication_metadata (元数据表)
-- ============================================================

CREATE TABLE cat_publication_metadata (
    id                  BIGINT          NOT NULL,
    publication_id      BIGINT          NOT NULL,
    indexing_status     VARCHAR(50)     NULL,
    indexing_method     VARCHAR(50)     NULL,
    indexed_date        DATE            NULL,
    data_source         VARCHAR(50)     NULL,
    import_batch        VARCHAR(50)     NULL,
    import_date         TIMESTAMPTZ(6)  NULL,
    owner               VARCHAR(50)     NULL,
    citation_subset     VARCHAR(20)     NULL,
    quality_score       VARCHAR(2)      NULL,
    completeness_score  VARCHAR(2)      NULL,
    has_full_text       BOOLEAN         NOT NULL DEFAULT false,
    full_text_url       VARCHAR(200)    NULL,
    review_status       VARCHAR(50)     NULL,
    review_date         DATE            NULL,
    reviewer            VARCHAR(100)    NULL,
    validation_errors   JSONB           NULL,
    processing_notes    JSONB           NULL,
    ext_metadata        JSONB           NULL,
    record_remarks      JSONB           NULL,
    version             BIGINT          NOT NULL DEFAULT 0,
    ip_address          BYTEA           NULL,
    created_at          TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    created_by          BIGINT          NULL,
    created_by_name     VARCHAR(100)    NULL,
    updated_at          TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_by          BIGINT          NULL,
    updated_by_name     VARCHAR(100)    NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_pub_metadata UNIQUE (publication_id)
);

CREATE INDEX idx_meta_indexing_status ON cat_publication_metadata (indexing_status);
CREATE INDEX idx_meta_data_source     ON cat_publication_metadata (data_source);
CREATE INDEX idx_meta_import_batch    ON cat_publication_metadata (import_batch);
CREATE INDEX idx_meta_review_status   ON cat_publication_metadata (review_status);

CREATE TRIGGER trg_cat_publication_metadata_updated_at
BEFORE UPDATE ON cat_publication_metadata
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 6: cat_publication_alternative_abstract (其他语言摘要表)
-- ============================================================

CREATE TABLE cat_publication_alternative_abstract (
    id                  BIGINT          NOT NULL,
    publication_id      BIGINT          NOT NULL,
    abstract_id         BIGINT          NULL,
    language_code       VARCHAR(10)     NOT NULL,
    source_type         VARCHAR(64)     NOT NULL DEFAULT 'unknown',
    language_name       VARCHAR(50)     NULL,
    plain_text          TEXT            NULL,
    structured_sections JSONB           NULL,
    translation_type    VARCHAR(50)     NULL,
    translator          VARCHAR(100)    NULL,
    translation_date    DATE            NULL,
    quality_level       VARCHAR(50)     NULL,
    is_official         BOOLEAN         NOT NULL DEFAULT false,
    order_num           INTEGER         NULL,
    metadata            JSONB           NULL,
    record_remarks      JSONB           NULL,
    version             BIGINT          NOT NULL DEFAULT 0,
    ip_address          BYTEA           NULL,
    created_at          TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    created_by          BIGINT          NULL,
    created_by_name     VARCHAR(100)    NULL,
    updated_at          TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_by          BIGINT          NULL,
    updated_by_name     VARCHAR(100)    NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_abstract_lang_source UNIQUE (publication_id, language_code, source_type)
);

CREATE INDEX idx_alt_abstract_publication ON cat_publication_alternative_abstract (publication_id);
CREATE INDEX idx_alt_abstract_abstract    ON cat_publication_alternative_abstract (abstract_id);
CREATE INDEX idx_alt_abstract_language    ON cat_publication_alternative_abstract (language_code);

CREATE TRIGGER trg_cat_publication_alternative_abstract_updated_at
BEFORE UPDATE ON cat_publication_alternative_abstract
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 7: cat_publication_oa_location (开放获取位置表)
-- ============================================================

CREATE TABLE cat_publication_oa_location (
    id               BIGINT          NOT NULL,
    publication_id   BIGINT          NOT NULL,
    oa_status        VARCHAR(20)     NOT NULL,
    location_type    VARCHAR(50)     NULL,
    url              VARCHAR(500)    NULL,
    host_domain      VARCHAR(200)    NULL,
    repository_name  VARCHAR(100)    NULL,
    repository_id    VARCHAR(100)    NULL,
    version_type     VARCHAR(50)     NULL,
    license          VARCHAR(100)    NULL,
    available_date   DATE            NULL,
    embargo_end_date DATE            NULL,
    is_best          BOOLEAN         NOT NULL DEFAULT false,
    priority         INTEGER         NULL,
    evidence_source  VARCHAR(50)     NULL,
    checked_date     TIMESTAMPTZ(6)  NULL,
    is_active        BOOLEAN         NOT NULL DEFAULT true,
    pmcid            VARCHAR(200)    NULL,
    access_metrics   JSONB           NULL,
    metadata         JSONB           NULL,
    record_remarks   JSONB           NULL,
    version          BIGINT          NOT NULL DEFAULT 0,
    ip_address       BYTEA           NULL,
    created_at       TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    created_by       BIGINT          NULL,
    created_by_name  VARCHAR(100)    NULL,
    updated_at       TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_by       BIGINT          NULL,
    updated_by_name  VARCHAR(100)    NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_oa_url UNIQUE (publication_id, url)
);

CREATE INDEX idx_oa_publication   ON cat_publication_oa_location (publication_id);
CREATE INDEX idx_oa_status        ON cat_publication_oa_location (oa_status);
CREATE INDEX idx_oa_location_type ON cat_publication_oa_location (location_type);

CREATE TRIGGER trg_cat_publication_oa_location_updated_at
BEFORE UPDATE ON cat_publication_oa_location
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 8: cat_publication_reference (参考文献表)
-- ============================================================

CREATE TABLE cat_publication_reference (
    id                    BIGINT          NOT NULL,
    publication_id        BIGINT          NOT NULL,
    cited_publication_id  BIGINT          NULL,
    cited_pmid            VARCHAR(20)     NULL,
    cited_doi             VARCHAR(200)    NULL,
    citation_text         VARCHAR(2000)   NULL,
    article_title         VARCHAR(500)    NULL,
    source                VARCHAR(500)    NULL,
    volume                VARCHAR(100)    NULL,
    issue                 VARCHAR(100)    NULL,
    pages                 VARCHAR(50)     NULL,
    year                  SMALLINT        NULL,
    authors               VARCHAR(500)    NULL,
    reference_type        VARCHAR(50)     NULL,
    reference_number      INTEGER         NOT NULL,
    is_retracted          BOOLEAN         NOT NULL DEFAULT false,
    metadata              JSONB           NULL,
    record_remarks        JSONB           NULL,
    version               BIGINT          NOT NULL DEFAULT 0,
    ip_address            BYTEA           NULL,
    created_at            TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    created_by            BIGINT          NULL,
    created_by_name       VARCHAR(100)    NULL,
    updated_at            TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_by            BIGINT          NULL,
    updated_by_name       VARCHAR(100)    NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_reference_num UNIQUE (publication_id, reference_number)
);

CREATE INDEX idx_ref_publication  ON cat_publication_reference (publication_id);
CREATE INDEX idx_ref_cited_pub    ON cat_publication_reference (cited_publication_id);
CREATE INDEX idx_ref_cited_pmid   ON cat_publication_reference (cited_pmid);
CREATE INDEX idx_ref_cited_doi    ON cat_publication_reference (cited_doi);
CREATE INDEX idx_ref_year         ON cat_publication_reference (year);
CREATE INDEX idx_ref_retracted    ON cat_publication_reference (is_retracted);

CREATE TRIGGER trg_cat_publication_reference_updated_at
BEFORE UPDATE ON cat_publication_reference
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 9: cat_publication_type (文献出版类型关联表)
-- ============================================================

CREATE TABLE cat_publication_type (
    id                BIGINT          NOT NULL,
    publication_id    BIGINT          NOT NULL,
    type_id           VARCHAR(50)     NULL,
    type_value        VARCHAR(200)    NOT NULL,
    vocabulary_source VARCHAR(50)     NULL,
    type_order        INTEGER         NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_pub_type UNIQUE (publication_id, type_value, vocabulary_source)
);

CREATE INDEX idx_pub_type_publication ON cat_publication_type (publication_id);
CREATE INDEX idx_pub_type_value       ON cat_publication_type (type_value);


-- ============================================================
-- 表 10: cat_publication_external_reference (外部引用表)
-- ============================================================

CREATE TABLE cat_publication_external_reference (
    id                BIGINT          NOT NULL,
    publication_id    BIGINT          NOT NULL,
    database_name     VARCHAR(50)     NOT NULL,
    database_category VARCHAR(100)    NULL,
    accession_number  VARCHAR(200)    NOT NULL,
    url               VARCHAR(500)    NULL,
    reference_type    VARCHAR(50)     NULL,
    description       VARCHAR(500)    NULL,
    access_date       DATE            NULL,
    database_version  VARCHAR(50)     NULL,
    order_num         INTEGER         NOT NULL DEFAULT 1,
    metadata          JSONB           NULL,
    record_remarks    JSONB           NULL,
    version           BIGINT          NOT NULL DEFAULT 0,
    ip_address        BYTEA           NULL,
    created_at        TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    created_by        BIGINT          NULL,
    created_by_name   VARCHAR(100)    NULL,
    updated_at        TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_by        BIGINT          NULL,
    updated_by_name   VARCHAR(100)    NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_external_ref UNIQUE (publication_id, database_name, accession_number)
);

CREATE INDEX idx_ext_ref_publication ON cat_publication_external_reference (publication_id);
CREATE INDEX idx_ext_ref_database    ON cat_publication_external_reference (database_name);
CREATE INDEX idx_ext_ref_accession   ON cat_publication_external_reference (accession_number);
CREATE INDEX idx_ext_ref_category    ON cat_publication_external_reference (database_category);

CREATE TRIGGER trg_cat_publication_external_reference_updated_at
BEFORE UPDATE ON cat_publication_external_reference
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 11: cat_publication_related_item (相关项目表)
-- ============================================================

CREATE TABLE cat_publication_related_item (
    id                      BIGINT          NOT NULL,
    publication_id          BIGINT          NOT NULL,
    related_publication_id  BIGINT          NULL,
    related_pmid            VARCHAR(20)     NULL,
    related_doi             VARCHAR(200)    NULL,
    relationship_type       VARCHAR(50)     NOT NULL,
    title                   VARCHAR(500)    NULL,
    description             VARCHAR(500)    NULL,
    relationship_date       TIMESTAMPTZ(6)  NULL,
    initiated_by            VARCHAR(100)    NULL,
    status                  VARCHAR(50)     NULL,
    order_num               INTEGER         NOT NULL DEFAULT 1,
    metadata                JSONB           NULL,
    record_remarks          JSONB           NULL,
    version                 BIGINT          NOT NULL DEFAULT 0,
    ip_address              BYTEA           NULL,
    created_at              TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    created_by              BIGINT          NULL,
    created_by_name         VARCHAR(100)    NULL,
    updated_at              TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_by              BIGINT          NULL,
    updated_by_name         VARCHAR(100)    NULL,

    PRIMARY KEY (id)
);

CREATE INDEX idx_related_publication    ON cat_publication_related_item (publication_id);
CREATE INDEX idx_related_pub_id         ON cat_publication_related_item (related_publication_id);
CREATE INDEX idx_relationship_type      ON cat_publication_related_item (relationship_type);
CREATE INDEX idx_related_status         ON cat_publication_related_item (status);
CREATE INDEX idx_related_date           ON cat_publication_related_item (relationship_date);

CREATE TRIGGER trg_cat_publication_related_item_updated_at
BEFORE UPDATE ON cat_publication_related_item
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 12: cat_publication_supplemental_object (补充对象表)
-- ============================================================

CREATE TABLE cat_publication_supplemental_object (
    id              BIGINT          NOT NULL,
    publication_id  BIGINT          NOT NULL,
    object_type     VARCHAR(50)     NOT NULL,
    content_type    VARCHAR(50)     NULL,
    title           VARCHAR(500)    NULL,
    description     VARCHAR(1000)   NULL,
    url             VARCHAR(500)    NULL,
    file_name       VARCHAR(255)    NULL,
    file_size       BIGINT          NULL,
    doi             VARCHAR(100)    NULL,
    license         VARCHAR(50)     NULL,
    authors         VARCHAR(500)    NULL,
    order_num       INTEGER         NOT NULL DEFAULT 1,
    is_public       BOOLEAN         NOT NULL DEFAULT true,
    available_date  DATE            NULL,
    metadata        JSONB           NULL,
    record_remarks  JSONB           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      BYTEA           NULL,
    created_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,

    PRIMARY KEY (id)
);

CREATE INDEX idx_suppl_publication  ON cat_publication_supplemental_object (publication_id);
CREATE INDEX idx_suppl_object_type  ON cat_publication_supplemental_object (object_type);
CREATE INDEX idx_suppl_public       ON cat_publication_supplemental_object (is_public);
CREATE INDEX idx_suppl_doi          ON cat_publication_supplemental_object (doi);
CREATE INDEX idx_suppl_avail_date   ON cat_publication_supplemental_object (available_date);

CREATE TRIGGER trg_cat_publication_supplemental_object_updated_at
BEFORE UPDATE ON cat_publication_supplemental_object
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 13: cat_publication_history (发布历史表)
-- ============================================================

CREATE TABLE cat_publication_history (
    id              BIGINT          NOT NULL,
    publication_id  BIGINT          NOT NULL,
    event_type      VARCHAR(50)     NOT NULL,
    event_date      TIMESTAMPTZ(6)  NOT NULL,
    date_precision  VARCHAR(10)     NULL,
    description     VARCHAR(500)    NULL,
    actor           VARCHAR(100)    NULL,
    previous_status VARCHAR(100)    NULL,
    new_status      VARCHAR(100)    NULL,
    order_num       INTEGER         NOT NULL,
    is_public       BOOLEAN         NOT NULL DEFAULT true,
    metadata        JSONB           NULL,
    record_remarks  JSONB           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      BYTEA           NULL,
    created_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_history_order UNIQUE (publication_id, order_num)
);

CREATE INDEX idx_history_publication ON cat_publication_history (publication_id);
CREATE INDEX idx_history_event_type  ON cat_publication_history (event_type);
CREATE INDEX idx_history_event_date  ON cat_publication_history (event_date);
CREATE INDEX idx_pub_history_date    ON cat_publication_history (publication_id, event_date, order_num);

CREATE TRIGGER trg_cat_publication_history_updated_at
BEFORE UPDATE ON cat_publication_history
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

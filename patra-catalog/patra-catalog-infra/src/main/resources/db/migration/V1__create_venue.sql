-- ============================================================
-- Patra Catalog — Venue 聚合 PostgreSQL baseline
-- 来源: V1.0.0 (venue 7 表) + V1.0.1 (rating 4 表) = 11 表
-- ============================================================

-- set_updated_at() 函数 — catalog 服务副本（每服务独立定义，方案 A）
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
    NEW.updated_at = now();
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- 表 1: cat_venue (出版载体表 - 最小聚合根)
-- ============================================================

CREATE TABLE cat_venue (
    id                  BIGINT                      NOT NULL,
    venue_type          VARCHAR(32)                 NOT NULL,
    title               VARCHAR(500)                NOT NULL,
    provenance_code     VARCHAR(32)                 NOT NULL,
    last_synced_at      TIMESTAMPTZ(6)              NULL,
    nlm_id              VARCHAR(20)                 NULL,
    issn_l              VARCHAR(10)                 NULL,
    openalex_id         VARCHAR(50)                 NULL,
    abbreviated_title   VARCHAR(200)                NULL,
    primary_language    VARCHAR(10)                 NULL,
    country_code        VARCHAR(2)                  NULL,
    image_object_key    VARCHAR(512)                NULL,
    cited_by_count      INTEGER                     NULL,
    publication_profile JSONB                       NULL,
    citation_metrics    JSONB                       NULL,
    open_access         JSONB                       NULL,
    affiliated_societies JSONB                      NULL,
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

    CONSTRAINT chk_venue_country_code CHECK (
        country_code IS NULL OR country_code ~ '^[A-Z]{2}$'
    )
);

CREATE INDEX idx_venue_type         ON cat_venue (venue_type);
CREATE INDEX idx_title              ON cat_venue (title);
CREATE INDEX idx_provenance         ON cat_venue (provenance_code);
CREATE INDEX idx_cited_by_count     ON cat_venue (cited_by_count);

CREATE TRIGGER trg_cat_venue_updated_at
BEFORE UPDATE ON cat_venue
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 2: cat_venue_identifier (载体标识符表)
-- 冗余索引 idx_venue_id 被 uk_venue_type_value 首列覆盖，已删除（§4.50）
-- ============================================================

CREATE TABLE cat_venue_identifier (
    id               BIGINT          NOT NULL,
    venue_id         BIGINT          NOT NULL,
    identifier_type  VARCHAR(32)     NOT NULL,
    identifier_value VARCHAR(255)    NOT NULL,
    is_primary       BOOLEAN         NOT NULL DEFAULT false,

    PRIMARY KEY (id),

    CONSTRAINT uk_venue_type_value UNIQUE (venue_id, identifier_type, identifier_value)
);

CREATE INDEX idx_type_value ON cat_venue_identifier (identifier_type, identifier_value);


-- ============================================================
-- 表 3: cat_venue_publication_stats (年度发文统计表)
-- ============================================================

CREATE TABLE cat_venue_publication_stats (
    id              BIGINT          NOT NULL,
    venue_id        BIGINT          NOT NULL,
    year            SMALLINT        NOT NULL,
    works_count     INTEGER         NOT NULL DEFAULT 0,
    cited_by_count  INTEGER         NOT NULL DEFAULT 0,
    oa_works_count  INTEGER         NULL,
    created_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    version         BIGINT          NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    CONSTRAINT uk_venue_year UNIQUE (venue_id, year)
);

CREATE INDEX idx_stats_year            ON cat_venue_publication_stats (year);
CREATE INDEX idx_stats_works_count     ON cat_venue_publication_stats (works_count);
CREATE INDEX idx_stats_cited_by_count  ON cat_venue_publication_stats (cited_by_count);

CREATE TRIGGER trg_cat_venue_publication_stats_updated_at
BEFORE UPDATE ON cat_venue_publication_stats
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 4: cat_venue_instance (载体实例表 - 独立聚合根)
-- ============================================================

CREATE TABLE cat_venue_instance (
    id                   BIGINT          NOT NULL,
    venue_id             BIGINT          NOT NULL,
    volume               VARCHAR(100)    NULL,
    issue                VARCHAR(100)    NULL,
    edition              VARCHAR(100)    NULL,
    publication_year     SMALLINT        NOT NULL,
    publication_month    SMALLINT        NULL,
    publication_day      SMALLINT        NULL,
    conference_name      VARCHAR(255)    NULL,
    conference_start_date DATE           NULL,
    conference_end_date  DATE            NULL,
    conference_location  VARCHAR(200)    NULL,
    instance_metadata    JSONB           NULL,
    record_remarks       JSONB           NULL,
    version              BIGINT          NOT NULL DEFAULT 0,
    ip_address           BYTEA           NULL,
    created_at           TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    created_by           BIGINT          NULL,
    created_by_name      VARCHAR(100)    NULL,
    updated_at           TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_by           BIGINT          NULL,
    updated_by_name      VARCHAR(100)    NULL,

    PRIMARY KEY (id)
);

CREATE INDEX idx_venue_volume_issue ON cat_venue_instance (venue_id, volume, issue);
CREATE INDEX idx_publication_year   ON cat_venue_instance (publication_year);
CREATE INDEX idx_venue_id           ON cat_venue_instance (venue_id);

CREATE TRIGGER trg_cat_venue_instance_updated_at
BEFORE UPDATE ON cat_venue_instance
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 5: cat_venue_mesh (期刊MeSH主题表)
-- ============================================================

CREATE TABLE cat_venue_mesh (
    id              BIGINT          NOT NULL,
    venue_id        BIGINT          NOT NULL,
    descriptor_name VARCHAR(255)    NOT NULL,
    descriptor_ui   VARCHAR(20)     NULL,
    is_major_topic  BOOLEAN         NOT NULL DEFAULT false,
    qualifier_name  VARCHAR(100)    NULL,
    qualifier_ui    VARCHAR(20)     NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_venue_mesh UNIQUE (venue_id, descriptor_name)
);

CREATE INDEX idx_venue_mesh_venue_id   ON cat_venue_mesh (venue_id);
CREATE INDEX idx_descriptor_ui         ON cat_venue_mesh (descriptor_ui);
CREATE INDEX idx_is_major              ON cat_venue_mesh (is_major_topic);


-- ============================================================
-- 表 6: cat_venue_relation (期刊关联表)
-- ============================================================

CREATE TABLE cat_venue_relation (
    id               BIGINT          NOT NULL,
    venue_id         BIGINT          NOT NULL,
    related_venue_id BIGINT          NULL,
    related_nlm_id   VARCHAR(20)     NULL,
    related_title    VARCHAR(500)    NOT NULL,
    relation_type    VARCHAR(32)     NOT NULL,
    effective_date   DATE            NULL,
    notes            VARCHAR(500)    NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_venue_related_type UNIQUE (venue_id, related_nlm_id, relation_type)
);

CREATE INDEX idx_venue_relation_venue_id   ON cat_venue_relation (venue_id);
CREATE INDEX idx_relation_type             ON cat_venue_relation (relation_type);
CREATE INDEX idx_related_venue             ON cat_venue_relation (related_venue_id);


-- ============================================================
-- 表 7: cat_venue_indexing_history (索引历史表)
-- ============================================================

CREATE TABLE cat_venue_indexing_history (
    id                  BIGINT          NOT NULL,
    venue_id            BIGINT          NOT NULL,
    indexing_source     VARCHAR(32)     NOT NULL,
    currently_indexed   BOOLEAN         NOT NULL DEFAULT false,
    indexing_treatment  VARCHAR(20)     NULL,
    citation_subset     VARCHAR(20)     NULL,
    start_year          SMALLINT        NULL,
    start_volume        VARCHAR(20)     NULL,
    start_issue         VARCHAR(20)     NULL,
    end_year            SMALLINT        NULL,
    end_volume          VARCHAR(20)     NULL,
    end_issue           VARCHAR(20)     NULL,
    created_at          TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    version             BIGINT          NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    CONSTRAINT uk_venue_source_start UNIQUE (venue_id, indexing_source, start_year)
);

CREATE INDEX idx_indexing_venue_id    ON cat_venue_indexing_history (venue_id);
CREATE INDEX idx_source_indexed       ON cat_venue_indexing_history (indexing_source, currently_indexed);
CREATE INDEX idx_citation_subset      ON cat_venue_indexing_history (citation_subset);

CREATE TRIGGER trg_cat_venue_indexing_history_updated_at
BEFORE UPDATE ON cat_venue_indexing_history
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 8: cat_venue_jcr_rating (JCR 期刊评级表)
-- ============================================================

CREATE TABLE cat_venue_jcr_rating (
    id                   BIGINT          NOT NULL,
    venue_id             BIGINT          NOT NULL,
    year                 SMALLINT        NOT NULL,
    impact_factor        NUMERIC(10,4)   NULL,
    wos_overall_quartile VARCHAR(10)     NULL,
    subject              VARCHAR(100)    NULL,
    collection           VARCHAR(10)     NULL,
    jif_quartile         VARCHAR(10)     NULL,
    jif_rank             VARCHAR(20)     NULL,
    jif_percentile       NUMERIC(5,2)    NULL,
    jci_subject          VARCHAR(100)    NULL,
    jci_collection       VARCHAR(10)     NULL,
    jci_quartile         VARCHAR(10)     NULL,
    jci_rank             VARCHAR(20)     NULL,
    jci_percentile       NUMERIC(5,2)    NULL,
    jci_value            NUMERIC(10,4)   NULL,
    self_citation_rate   NUMERIC(5,2)    NULL,
    research_direction   VARCHAR(200)    NULL,
    source_url           VARCHAR(500)    NULL,
    fetched_at           TIMESTAMPTZ(6)  NULL,
    created_at           TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    version              BIGINT          NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    CONSTRAINT uk_jcr_venue_year UNIQUE (venue_id, year)
);

CREATE INDEX idx_jcr_venue_id      ON cat_venue_jcr_rating (venue_id);
CREATE INDEX idx_jif_quartile      ON cat_venue_jcr_rating (jif_quartile);
CREATE INDEX idx_impact_factor     ON cat_venue_jcr_rating (impact_factor);

CREATE TRIGGER trg_cat_venue_jcr_rating_updated_at
BEFORE UPDATE ON cat_venue_jcr_rating
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 9: cat_venue_cas_rating (CAS 中科院分区表)
-- ============================================================

CREATE TABLE cat_venue_cas_rating (
    id                BIGINT          NOT NULL,
    venue_id          BIGINT          NOT NULL,
    year              SMALLINT        NOT NULL,
    edition           VARCHAR(20)     NOT NULL,
    major_category    VARCHAR(50)     NULL,
    major_quartile    VARCHAR(10)     NULL,
    minor_subject     VARCHAR(100)    NULL,
    minor_quartile    VARCHAR(10)     NULL,
    is_top_journal    BOOLEAN         NULL,
    is_review_journal BOOLEAN         NULL,
    source_url        VARCHAR(500)    NULL,
    fetched_at        TIMESTAMPTZ(6)  NULL,
    created_at        TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    version           BIGINT          NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    CONSTRAINT uk_cas_venue_year_edition UNIQUE (venue_id, year, edition)
);

CREATE INDEX idx_cas_venue_id     ON cat_venue_cas_rating (venue_id);
CREATE INDEX idx_major_quartile   ON cat_venue_cas_rating (major_quartile);
CREATE INDEX idx_is_top_journal   ON cat_venue_cas_rating (is_top_journal);

CREATE TRIGGER trg_cat_venue_cas_rating_updated_at
BEFORE UPDATE ON cat_venue_cas_rating
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 10: cat_venue_scopus_rating (Scopus 期刊指标表)
-- ============================================================

CREATE TABLE cat_venue_scopus_rating (
    id                  BIGINT          NOT NULL,
    venue_id            BIGINT          NOT NULL,
    year                SMALLINT        NOT NULL,
    cite_score          NUMERIC(10,4)   NULL,
    cite_score_tracker  NUMERIC(10,4)   NULL,
    sjr                 NUMERIC(10,4)   NULL,
    snip                NUMERIC(10,4)   NULL,
    document_count      INTEGER         NULL,
    citation_count      INTEGER         NULL,
    percent_cited       NUMERIC(5,2)    NULL,
    subject_area        VARCHAR(200)    NULL,
    quartile            VARCHAR(5)      NULL,
    percentile          NUMERIC(5,2)    NULL,
    scopus_source_id    VARCHAR(20)     NULL,
    source_url          VARCHAR(500)    NULL,
    fetched_at          TIMESTAMPTZ(6)  NULL,
    created_at          TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    version             BIGINT          NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    CONSTRAINT uk_scopus_venue_year UNIQUE (venue_id, year)
);

CREATE INDEX idx_scopus_venue_id  ON cat_venue_scopus_rating (venue_id);
CREATE INDEX idx_cite_score       ON cat_venue_scopus_rating (cite_score);
CREATE INDEX idx_quartile         ON cat_venue_scopus_rating (quartile);

CREATE TRIGGER trg_cat_venue_scopus_rating_updated_at
BEFORE UPDATE ON cat_venue_scopus_rating
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 11: cat_venue_cas_warning (CAS 中科院期刊预警名单)
-- ============================================================

CREATE TABLE cat_venue_cas_warning (
    id              BIGINT          NOT NULL,
    venue_id        BIGINT          NOT NULL,
    published_year  SMALLINT        NOT NULL,
    published_month SMALLINT        NULL,
    edition_label   VARCHAR(30)     NOT NULL,
    in_warning_list BOOLEAN         NOT NULL,
    warning_level   VARCHAR(10)     NULL,
    raw_text        VARCHAR(500)    NULL,
    source_url      VARCHAR(500)    NULL,
    fetched_at      TIMESTAMPTZ(6)  NULL,
    created_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    version         BIGINT          NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    CONSTRAINT uk_warning_venue_year_edition UNIQUE (venue_id, published_year, edition_label)
);

CREATE INDEX idx_cas_warning_venue_id ON cat_venue_cas_warning (venue_id);
CREATE INDEX idx_in_warning           ON cat_venue_cas_warning (in_warning_list);
CREATE INDEX idx_warning_level        ON cat_venue_cas_warning (warning_level);
CREATE INDEX idx_published_year       ON cat_venue_cas_warning (published_year);

CREATE TRIGGER trg_cat_venue_cas_warning_updated_at
BEFORE UPDATE ON cat_venue_cas_warning
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

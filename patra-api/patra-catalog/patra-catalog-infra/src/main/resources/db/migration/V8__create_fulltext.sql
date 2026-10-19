-- ============================================================
-- Patra Catalog — v0.11 全文阅读
-- PubMed 增量同步进度表、全文记录表、正文表 (3 表)
-- ============================================================

-- ============================================================
-- 表 1: cat_pubmed_update_file (PubMed updatefiles 增量同步进度)
-- ============================================================

CREATE TABLE cat_pubmed_update_file (
    id                    BIGINT          NOT NULL,
    file_name             VARCHAR(40)     NOT NULL,
    file_seq              INT             NOT NULL,
    status                VARCHAR(20)     NOT NULL,
    scope                 VARCHAR(20)     NOT NULL,
    created_count         INT             NOT NULL DEFAULT 0,
    updated_count         INT             NOT NULL DEFAULT 0,
    deleted_count         INT             NOT NULL DEFAULT 0,
    skipped_absent_count  INT             NOT NULL DEFAULT 0,
    skipped_invalid_count INT             NOT NULL DEFAULT 0,
    doi_conflict_count    INT             NOT NULL DEFAULT 0,
    skip_error_count      INT             NOT NULL DEFAULT 0,
    started_at            TIMESTAMPTZ(6)  NULL,
    finished_at           TIMESTAMPTZ(6)  NULL,
    last_error            VARCHAR(2000)   NULL,
    job_generation        VARCHAR(64)     NULL,
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

    CONSTRAINT uk_pubmed_update_file_name UNIQUE (file_name)
);

CREATE INDEX idx_pubmed_update_file_seq ON cat_pubmed_update_file (file_seq);

CREATE TRIGGER trg_cat_pubmed_update_file_updated_at
BEFORE UPDATE ON cat_pubmed_update_file
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 2: cat_publication_fulltext (全文记录与状态，一篇文献一行)
-- ============================================================

CREATE TABLE cat_publication_fulltext (
    id                    BIGINT          NOT NULL,
    publication_id        BIGINT          NOT NULL,
    pmcid                 VARCHAR(20)     NOT NULL,
    pmc_version           VARCHAR(30)     NULL,
    status                VARCHAR(20)     NOT NULL,
    unavailable_reason    VARCHAR(30)     NULL,
    license_code          VARCHAR(30)     NULL,
    is_manuscript         BOOLEAN         NULL,
    object_prefix         VARCHAR(200)    NULL,
    xml_md5               CHAR(32)        NULL,
    pdf_object_key        VARCHAR(300)    NULL,
    pdf_size              BIGINT          NULL,
    pdf_md5               CHAR(32)        NULL,
    media                 JSONB           NOT NULL DEFAULT '[]'::jsonb,
    fetched_at            TIMESTAMPTZ(6)  NULL,
    parser_version        INT             NULL,
    parsed_at             TIMESTAMPTZ(6)  NULL,
    word_count            INT             NULL,
    attempt_count         INT             NOT NULL DEFAULT 0,
    last_error            VARCHAR(2000)   NULL,
    next_attempt_at       TIMESTAMPTZ(6)  NULL,
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

    CONSTRAINT uk_publication_fulltext_publication UNIQUE (publication_id)
);

CREATE INDEX idx_fulltext_status_next   ON cat_publication_fulltext (status, next_attempt_at);
CREATE INDEX idx_fulltext_status_parser ON cat_publication_fulltext (status, parser_version);
CREATE INDEX idx_fulltext_pmcid         ON cat_publication_fulltext (pmcid);

CREATE TRIGGER trg_cat_publication_fulltext_updated_at
BEFORE UPDATE ON cat_publication_fulltext
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 3: cat_publication_fulltext_body (解析后的正文 JSON)
-- 正文表没有 id 列、不建 JPA 实体：写入用 native upsert
-- (INSERT … ON CONFLICT (publication_id) DO UPDATE)，读取用 native 查询。
-- 只按 publication_id 整篇读，不在 JSONB 里查询，不建 GIN 索引。
-- ============================================================

CREATE TABLE cat_publication_fulltext_body (
    publication_id        BIGINT          NOT NULL,
    body                  JSONB           NOT NULL,
    parser_version        INT             NOT NULL,
    created_at            TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),

    PRIMARY KEY (publication_id)
);

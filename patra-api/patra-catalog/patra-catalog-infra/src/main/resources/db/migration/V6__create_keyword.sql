-- ============================================================
-- Patra Catalog — Keyword PostgreSQL baseline
-- 来源: V1.4.2 (2 表)
-- 特别改动:
--   - 删除 FULLTEXT INDEX ft_keyword_term（§4.19）
--   - 删除 idx_major（cat_publication_keyword）冗余索引（§4.50）
--     idx_major (keyword_id, is_major) 被 idx_keyword_pub 前缀覆盖
-- ============================================================

-- ============================================================
-- 表 1: cat_keyword (关键词表)
-- FULLTEXT ft_keyword_term 已删除 (§4.19)
-- ============================================================

CREATE TABLE cat_keyword (
    id              BIGINT          NOT NULL,
    term            VARCHAR(500)    NOT NULL,
    source          VARCHAR(50)     NULL,
    language        VARCHAR(10)     NULL,
    normalized_term VARCHAR(500)    NULL,
    frequency       INTEGER         NULL DEFAULT 0,
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

    CONSTRAINT uk_normalized_term UNIQUE (normalized_term)
);

CREATE INDEX idx_keyword_frequency  ON cat_keyword (frequency DESC);
CREATE INDEX idx_keyword_source_lang ON cat_keyword (source, language);

CREATE TRIGGER trg_cat_keyword_updated_at
BEFORE UPDATE ON cat_keyword
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 2: cat_publication_keyword (文献-关键词关联表)
-- 冗余索引 idx_major (keyword_id, is_major) 已删除（§4.50）
-- idx_keyword_pub (keyword_id, publication_id) 已覆盖 keyword_id 前缀
-- ============================================================

CREATE TABLE cat_publication_keyword (
    id              BIGINT          NOT NULL,
    publication_id  BIGINT          NOT NULL,
    keyword_id      BIGINT          NOT NULL,
    is_major        BOOLEAN         NOT NULL DEFAULT false,
    order_num       INTEGER         NULL,
    keyword_set     VARCHAR(50)     NULL,

    PRIMARY KEY (id)
);

CREATE INDEX idx_pub_keyword  ON cat_publication_keyword (publication_id, keyword_id);
CREATE INDEX idx_keyword_pub  ON cat_publication_keyword (keyword_id, publication_id);

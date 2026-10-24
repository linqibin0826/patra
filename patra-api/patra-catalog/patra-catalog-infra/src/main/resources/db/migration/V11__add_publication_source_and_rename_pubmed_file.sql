-- ============================================================
-- PAP-110：可重放导入
-- 1. cat_publication_source：每篇文献最后一次写入（或软删）它的 PubMed 文件位置，以及该文件里这篇的原文
--    - source_rank = 年 × 10000 + 文件序号；后出现的文件取代先出现的（NLM 规则），据此挡住旧版本
--    - raw_xml 为逐篇切出的 <PubmedArticle>，供本地重算；只被软删、从没写过原文的「墓碑」行为 NULL
--    - 按 PMID 主键：与身份锚一致，换库重导内部 ID 不受影响；墓碑行没有对应的 cat_publication
--    - 只由原生 SQL 读写（upsert），不建 JPA 实体，审计列只留时间
-- 2. cat_pubmed_update_file → cat_pubmed_file：进度表同时记录 baseline 与 updatefiles（kind 区分）
-- ============================================================

CREATE TABLE cat_publication_source (
    pmid         VARCHAR(15)     NOT NULL,
    source_file  VARCHAR(40)     NOT NULL,
    source_rank  INTEGER         NOT NULL,
    raw_xml      TEXT            NULL,
    parsed_at    TIMESTAMPTZ(6)  NULL,
    created_at   TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),

    PRIMARY KEY (pmid)
);

ALTER TABLE cat_publication_source ALTER COLUMN raw_xml SET COMPRESSION lz4;

CREATE TRIGGER trg_cat_publication_source_updated_at
BEFORE UPDATE ON cat_publication_source
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


ALTER TABLE cat_pubmed_update_file RENAME TO cat_pubmed_file;
ALTER TABLE cat_pubmed_file RENAME CONSTRAINT uk_pubmed_update_file_name TO uk_pubmed_file_name;
ALTER TABLE cat_pubmed_file RENAME CONSTRAINT cat_pubmed_update_file_pkey TO cat_pubmed_file_pkey;
ALTER INDEX idx_pubmed_update_file_seq RENAME TO idx_pubmed_file_seq;
ALTER TRIGGER trg_cat_pubmed_update_file_updated_at ON cat_pubmed_file
    RENAME TO trg_cat_pubmed_file_updated_at;

ALTER TABLE cat_pubmed_file
    ADD COLUMN kind                VARCHAR(10) NOT NULL DEFAULT 'UPDATE',
    ADD COLUMN skipped_stale_count INT         NOT NULL DEFAULT 0;
ALTER TABLE cat_pubmed_file ALTER COLUMN kind DROP DEFAULT;

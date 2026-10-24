-- ============================================================
-- PAP-110：引用数据列
-- - publication_month / publication_day：主出版日期的冗余（同 publication_year），精度由哪几个为 NULL 表达，
--   供卡片、按出版日期排序、导出直接读；缺的月日不补
-- - medline_pgn / start_page / end_page：PubMed Pagination 原文（MedlinePgn 为 NLM 缩写形式，如 1451-61）
-- - journal_iso_abbreviation：文献 XML 的 Journal/ISOAbbreviation（期刊目录没有这个字段，故放文献行）
-- ============================================================

ALTER TABLE cat_publication
    ADD COLUMN publication_month        SMALLINT     NULL,
    ADD COLUMN publication_day          SMALLINT     NULL,
    ADD COLUMN medline_pgn              VARCHAR(200) NULL,
    ADD COLUMN start_page               VARCHAR(50)  NULL,
    ADD COLUMN end_page                 VARCHAR(50)  NULL,
    ADD COLUMN journal_iso_abbreviation VARCHAR(200) NULL,
    ADD CONSTRAINT ck_pub_month CHECK (publication_month BETWEEN 1 AND 12),
    ADD CONSTRAINT ck_pub_day CHECK (publication_day BETWEEN 1 AND 31),
    ADD CONSTRAINT ck_pub_day_needs_month CHECK (publication_day IS NULL OR publication_month IS NOT NULL);

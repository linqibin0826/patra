-- ============================================================
-- Patra Catalog — MeSH + MeSH SCR PostgreSQL baseline
-- 来源: V1.4.0 (mesh descriptor 子系统 10 表)
--       + V1.4.1 (mesh SCR 子系统 5 表) = 15 表
-- 特别改动:
--   - 删除 3 个 FULLTEXT INDEX（§4.19）：
--     ft_name_note (cat_mesh_descriptor), ft_term (cat_mesh_entry_term),
--     ft_name_note (cat_mesh_scr)
-- ============================================================

-- ============================================================
-- 表 1: cat_mesh_descriptor (MeSH 主题词表)
-- FULLTEXT ft_name_note 已删除 (§4.19)
-- ============================================================

CREATE TABLE cat_mesh_descriptor (
    id                          BIGINT          NOT NULL,
    ui                          VARCHAR(10)     NOT NULL,
    name                        VARCHAR(255)    NOT NULL,
    descriptor_class            VARCHAR(50)     NULL,
    scope_note                  TEXT            NULL,
    annotation                  TEXT            NULL,
    previous_indexing           TEXT            NULL,
    public_mesh_note            TEXT            NULL,
    consider_also               TEXT            NULL,
    history_note                TEXT            NULL,
    online_note                 TEXT            NULL,
    nlm_classification_number   VARCHAR(50)     NULL,
    date_created                DATE            NULL,
    date_revised                DATE            NULL,
    date_established            DATE            NULL,
    active_status               BOOLEAN         NOT NULL DEFAULT true,
    mesh_version                VARCHAR(10)     NULL,
    metadata                    JSONB           NULL,
    record_remarks              JSONB           NULL,
    version                     BIGINT          NOT NULL DEFAULT 0,
    ip_address                  BYTEA           NULL,
    created_at                  TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    created_by                  BIGINT          NULL,
    created_by_name             VARCHAR(100)    NULL,
    updated_at                  TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),
    updated_by                  BIGINT          NULL,
    updated_by_name             VARCHAR(100)    NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_mesh_ui UNIQUE (ui)
);

CREATE INDEX idx_mesh_desc_name         ON cat_mesh_descriptor (name);
CREATE INDEX idx_mesh_active_version    ON cat_mesh_descriptor (active_status, mesh_version);

CREATE TRIGGER trg_cat_mesh_descriptor_updated_at
BEFORE UPDATE ON cat_mesh_descriptor
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 2: cat_mesh_qualifier (MeSH 限定词表)
-- ============================================================

CREATE TABLE cat_mesh_qualifier (
    id              BIGINT          NOT NULL,
    ui              VARCHAR(10)     NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    abbreviation    VARCHAR(10)     NULL,
    annotation      TEXT            NULL,
    date_created    DATE            NULL,
    date_revised    DATE            NULL,
    date_established DATE           NULL,
    active_status   BOOLEAN         NOT NULL DEFAULT true,
    mesh_version    VARCHAR(10)     NULL,
    history_note    TEXT            NULL,
    online_note     TEXT            NULL,
    tree_numbers    JSONB           NULL,
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

    CONSTRAINT uk_qualifier_ui UNIQUE (ui)
);

CREATE INDEX idx_mesh_qual_name ON cat_mesh_qualifier (name);

CREATE TRIGGER trg_cat_mesh_qualifier_updated_at
BEFORE UPDATE ON cat_mesh_qualifier
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 3: cat_mesh_tree_number (MeSH 树形编号表)
-- ============================================================

CREATE TABLE cat_mesh_tree_number (
    id              BIGINT          NOT NULL,
    descriptor_ui   VARCHAR(10)     NOT NULL,
    tree_number     VARCHAR(100)    NOT NULL,
    tree_level      SMALLINT        NOT NULL,
    is_primary      BOOLEAN         NOT NULL DEFAULT true,

    PRIMARY KEY (id),

    CONSTRAINT uk_tree_number_descriptor UNIQUE (tree_number, descriptor_ui)
);

CREATE INDEX idx_tree_descriptor_ui ON cat_mesh_tree_number (descriptor_ui);
CREATE INDEX idx_tree_prefix        ON cat_mesh_tree_number (tree_number);
CREATE INDEX idx_tree_level         ON cat_mesh_tree_number (tree_level, descriptor_ui);


-- ============================================================
-- 表 4: cat_mesh_entry_term (MeSH 入口术语表)
-- FULLTEXT ft_term 已删除 (§4.19)
-- ============================================================

CREATE TABLE cat_mesh_entry_term (
    id                  BIGINT          NOT NULL,
    record_type         VARCHAR(20)     NOT NULL DEFAULT 'DESCRIPTOR',
    owner_ui            VARCHAR(10)     NOT NULL,
    term_ui             VARCHAR(10)     NULL,
    concept_ui          VARCHAR(10)     NULL,
    term                VARCHAR(255)    NOT NULL,
    lexical_tag         VARCHAR(10)     NULL,
    is_print_flag       BOOLEAN         NOT NULL DEFAULT true,
    record_preferred    VARCHAR(10)     NULL,
    is_permuted_term    BOOLEAN         NOT NULL DEFAULT false,
    is_concept_preferred BOOLEAN        NOT NULL DEFAULT false,
    abbreviation        VARCHAR(50)     NULL,
    sort_version        VARCHAR(255)    NULL,
    entry_version       VARCHAR(100)    NULL,
    term_note           TEXT            NULL,
    date_created        DATE            NULL,
    thesaurus_ids       JSONB           NULL,

    PRIMARY KEY (id)
);

CREATE INDEX idx_entry_term_owner_ui        ON cat_mesh_entry_term (owner_ui);
CREATE INDEX idx_entry_term_concept_ui      ON cat_mesh_entry_term (concept_ui);
CREATE INDEX idx_entry_term_record_type     ON cat_mesh_entry_term (record_type, owner_ui);


-- ============================================================
-- 表 5: cat_mesh_concept (MeSH 概念表)
-- ============================================================

CREATE TABLE cat_mesh_concept (
    id                                  BIGINT          NOT NULL,
    record_type                         VARCHAR(20)     NOT NULL DEFAULT 'DESCRIPTOR',
    owner_ui                            VARCHAR(10)     NOT NULL,
    concept_ui                          VARCHAR(10)     NOT NULL,
    concept_name                        VARCHAR(255)    NOT NULL,
    is_preferred                        BOOLEAN         NOT NULL DEFAULT false,
    casn1_name                          TEXT            NULL,
    registry_numbers                    JSONB           NULL,
    related_registry_numbers            JSONB           NULL,
    scope_note                          TEXT            NULL,
    translators_english_scope_note      TEXT            NULL,
    translators_scope_note              TEXT            NULL,
    concept_status                      VARCHAR(10)     NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_concept_ui UNIQUE (concept_ui)
);

CREATE INDEX idx_mesh_concept_owner_ui      ON cat_mesh_concept (owner_ui);
CREATE INDEX idx_mesh_concept_record_type   ON cat_mesh_concept (record_type, owner_ui);


-- ============================================================
-- 表 6: cat_mesh_concept_relation (MeSH 概念关系表)
-- ============================================================

CREATE TABLE cat_mesh_concept_relation (
    id              BIGINT          NOT NULL,
    descriptor_ui   VARCHAR(10)     NOT NULL,
    concept_ui      VARCHAR(10)     NOT NULL,
    is_preferred    BOOLEAN         NOT NULL DEFAULT false,
    relation_name   VARCHAR(10)     NULL,
    concept1_ui     VARCHAR(10)     NOT NULL,
    concept2_ui     VARCHAR(10)     NOT NULL,

    PRIMARY KEY (id)
);

CREATE INDEX idx_mesh_rel_descriptor_ui ON cat_mesh_concept_relation (descriptor_ui);
CREATE INDEX idx_mesh_rel_concept       ON cat_mesh_concept_relation (concept_ui);


-- ============================================================
-- 表 7: cat_mesh_entry_combination (MeSH 组合条目表)
-- ============================================================

CREATE TABLE cat_mesh_entry_combination (
    id                   BIGINT          NOT NULL,
    descriptor_ui        VARCHAR(10)     NOT NULL,
    ecin_descriptor_ui   VARCHAR(10)     NOT NULL,
    ecin_qualifier_ui    VARCHAR(10)     NOT NULL,
    ecout_descriptor_ui  VARCHAR(10)     NOT NULL,
    ecout_qualifier_ui   VARCHAR(10)     NULL,

    PRIMARY KEY (id)
);

CREATE INDEX idx_entry_combo_descriptor  ON cat_mesh_entry_combination (descriptor_ui);
CREATE INDEX idx_ecin_descriptor         ON cat_mesh_entry_combination (ecin_descriptor_ui);
CREATE INDEX idx_ecout_descriptor        ON cat_mesh_entry_combination (ecout_descriptor_ui);


-- ============================================================
-- 表 8: cat_publication_mesh_heading (文献-MeSH 主题标引表)
-- ============================================================

CREATE TABLE cat_publication_mesh_heading (
    id              BIGINT          NOT NULL,
    publication_id  BIGINT          NOT NULL,
    descriptor_ui   VARCHAR(10)     NOT NULL,
    is_major_topic  BOOLEAN         NOT NULL DEFAULT false,
    heading_order   INTEGER         NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_pub_descriptor UNIQUE (publication_id, descriptor_ui)
);

CREATE INDEX idx_mesh_heading_publication ON cat_publication_mesh_heading (publication_id);
CREATE INDEX idx_mesh_heading_descriptor  ON cat_publication_mesh_heading (descriptor_ui);
CREATE INDEX idx_mesh_heading_major_topic ON cat_publication_mesh_heading (is_major_topic);


-- ============================================================
-- 表 9: cat_publication_mesh_qualifier (文献-MeSH 限定词关联表)
-- ============================================================

CREATE TABLE cat_publication_mesh_qualifier (
    id                          BIGINT          NOT NULL,
    publication_mesh_heading_id BIGINT          NOT NULL,
    qualifier_ui                VARCHAR(10)     NOT NULL,
    is_major_topic              BOOLEAN         NOT NULL DEFAULT false,
    qualifier_order             INTEGER         NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_heading_qualifier UNIQUE (publication_mesh_heading_id, qualifier_ui)
);

CREATE INDEX idx_mesh_qual_heading      ON cat_publication_mesh_qualifier (publication_mesh_heading_id);
CREATE INDEX idx_mesh_qual_qualifier_ui ON cat_publication_mesh_qualifier (qualifier_ui);
CREATE INDEX idx_mesh_qual_major_topic  ON cat_publication_mesh_qualifier (is_major_topic);


-- ============================================================
-- 表 10: cat_publication_suppl_mesh (文献-补充MeSH概念关联表)
-- ============================================================

CREATE TABLE cat_publication_suppl_mesh (
    id              BIGINT          NOT NULL,
    publication_id  BIGINT          NOT NULL,
    scr_ui          VARCHAR(10)     NOT NULL,
    suppl_order     INTEGER         NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_pub_scr UNIQUE (publication_id, scr_ui)
);

CREATE INDEX idx_suppl_mesh_scr_ui ON cat_publication_suppl_mesh (scr_ui);


-- ============================================================
-- 表 11: cat_mesh_scr (MeSH 补充概念记录主表)
-- FULLTEXT ft_name_note 已删除 (§4.19)
-- ============================================================

CREATE TABLE cat_mesh_scr (
    id                  BIGINT          NOT NULL,
    ui                  VARCHAR(10)     NOT NULL,
    name                VARCHAR(500)    NOT NULL,
    scr_class           SMALLINT        NOT NULL DEFAULT 1,
    note                TEXT            NULL,
    frequency           VARCHAR(50)     NULL,
    previous_indexing   TEXT            NULL,
    date_created        DATE            NULL,
    date_revised        DATE            NULL,
    active_status       BOOLEAN         NOT NULL DEFAULT true,
    mesh_version        VARCHAR(10)     NULL,
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

    CONSTRAINT uk_scr_ui UNIQUE (ui)
);

CREATE INDEX idx_scr_name           ON cat_mesh_scr (name);
CREATE INDEX idx_scr_class          ON cat_mesh_scr (scr_class);
CREATE INDEX idx_scr_active_version ON cat_mesh_scr (active_status, mesh_version);
CREATE INDEX idx_scr_revised        ON cat_mesh_scr (date_revised, mesh_version);

CREATE TRIGGER trg_cat_mesh_scr_updated_at
BEFORE UPDATE ON cat_mesh_scr
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 表 12: cat_mesh_scr_heading_mapped_to (SCR 映射关系表)
-- ============================================================

CREATE TABLE cat_mesh_scr_heading_mapped_to (
    id              BIGINT          NOT NULL,
    scr_ui          VARCHAR(10)     NOT NULL,
    descriptor_ui   VARCHAR(10)     NOT NULL,
    qualifier_ui    VARCHAR(10)     NULL,
    major_topic     BOOLEAN         NOT NULL DEFAULT false,

    PRIMARY KEY (id),

    CONSTRAINT uk_scr_desc_qual UNIQUE (scr_ui, descriptor_ui, qualifier_ui)
);

CREATE INDEX idx_scr_mapped_scr_ui      ON cat_mesh_scr_heading_mapped_to (scr_ui);
CREATE INDEX idx_scr_mapped_descriptor  ON cat_mesh_scr_heading_mapped_to (descriptor_ui);


-- ============================================================
-- 表 13: cat_mesh_scr_source (SCR 来源表)
-- ============================================================

CREATE TABLE cat_mesh_scr_source (
    id          BIGINT          NOT NULL,
    scr_ui      VARCHAR(10)     NOT NULL,
    source      VARCHAR(500)    NOT NULL,
    order_num   INTEGER         NULL,

    PRIMARY KEY (id)
);

CREATE INDEX idx_scr_source_scr_ui ON cat_mesh_scr_source (scr_ui);


-- ============================================================
-- 表 14: cat_mesh_scr_indexing_info (SCR 索引信息表)
-- ============================================================

CREATE TABLE cat_mesh_scr_indexing_info (
    id              BIGINT          NOT NULL,
    scr_ui          VARCHAR(10)     NOT NULL,
    descriptor_ui   VARCHAR(10)     NULL,
    qualifier_ui    VARCHAR(10)     NULL,
    chemical_ui     VARCHAR(10)     NULL,

    PRIMARY KEY (id)
);

CREATE INDEX idx_scr_idx_info_scr_ui ON cat_mesh_scr_indexing_info (scr_ui);


-- ============================================================
-- 表 15: cat_mesh_scr_pharmacological_action (SCR 药理作用表)
-- ============================================================

CREATE TABLE cat_mesh_scr_pharmacological_action (
    id              BIGINT          NOT NULL,
    scr_ui          VARCHAR(10)     NOT NULL,
    descriptor_ui   VARCHAR(10)     NOT NULL,
    descriptor_name VARCHAR(255)    NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_scr_pa_descriptor UNIQUE (scr_ui, descriptor_ui)
);

CREATE INDEX idx_scr_pa_scr_ui      ON cat_mesh_scr_pharmacological_action (scr_ui);
CREATE INDEX idx_scr_pa_descriptor  ON cat_mesh_scr_pharmacological_action (descriptor_ui);

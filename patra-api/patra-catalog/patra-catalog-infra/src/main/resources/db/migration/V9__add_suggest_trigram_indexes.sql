-- ============================================================
-- Patra Catalog — V9: 机构与 MeSH 建议接口的 trigram 索引 + Patra 自维护的机构别名表（PAP-94）
-- 设计: docs/patra/specs/2026-10-21-catalog-org-mesh-suggest-design.md 第 10、16 节
-- ============================================================

-- pg_trgm 是 trusted 扩展，官方 postgres:17 镜像自带
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================================
-- 机构名称：任意位置子串 / 前缀 / 词首的 ILIKE 都走它
-- ============================================================

CREATE INDEX idx_org_name_value_trgm
    ON cat_organization_name USING gin (name_value gin_trgm_ops);

-- ============================================================
-- MeSH 主题词名：只给主题类（class 1）且 active 的；谓词与建议查询的 WHERE 逐字相同
-- ============================================================

CREATE INDEX idx_mesh_desc_name_trgm
    ON cat_mesh_descriptor USING gin (name gin_trgm_ops)
    WHERE descriptor_class = '1' AND active_status;

-- ============================================================
-- MeSH 入口词：只给主题词的非排列入口词
-- ============================================================

CREATE INDEX idx_entry_term_term_trgm
    ON cat_mesh_entry_term USING gin (term gin_trgm_ops)
    WHERE record_type = 'DESCRIPTOR' AND is_permuted_term = false;

-- ============================================================
-- 表: cat_organization_alias (Patra 自维护的机构别名)
-- 补 ROR 缺的中文名与简称；按 ror_id 关联，ROR 重导后内部 ID 变了也不失效。
-- 种子数据在可重复迁移 R__cat_organization_alias_seed.sql 里，没有 JPA 实体。
-- ============================================================

CREATE TABLE cat_organization_alias (
    id          BIGINT          GENERATED ALWAYS AS IDENTITY,
    ror_id      VARCHAR(50)     NOT NULL,
    alias       VARCHAR(500)    NOT NULL,
    lang        VARCHAR(10)     NULL,
    alias_type  VARCHAR(20)     NOT NULL,
    source      VARCHAR(20)     NOT NULL,
    created_at  TIMESTAMPTZ(6)  NOT NULL DEFAULT now(),

    PRIMARY KEY (id),

    CONSTRAINT uk_org_alias UNIQUE (ror_id, alias),
    CONSTRAINT ck_org_alias_type CHECK (alias_type IN ('LABEL', 'ALIAS')),
    CONSTRAINT ck_org_alias_source CHECK (source IN ('SEED', 'WIKIDATA'))
);

COMMENT ON TABLE cat_organization_alias IS 'Patra 自维护的机构别名：补 ROR 缺的中文名与简称，只参与建议匹配与中文名取值';
COMMENT ON COLUMN cat_organization_alias.ror_id IS '9 位裸 ROR ID，与 cat_organization.ror_id 对应（逻辑外键，ROR 重导不影响）';
COMMENT ON COLUMN cat_organization_alias.alias IS '别名原文';
COMMENT ON COLUMN cat_organization_alias.lang IS 'ISO 639-1 语言代码，中文为 zh';
COMMENT ON COLUMN cat_organization_alias.alias_type IS 'LABEL = 可作显示名的全名（lang 为 zh 时优先于 ROR 的中文名）；ALIAS = 简称、别名、旧名，只参与匹配';
COMMENT ON COLUMN cat_organization_alias.source IS 'SEED = 手工种子；WIKIDATA = 从 Wikidata 取的 zh 标签与别名';

CREATE INDEX idx_org_alias_ror_id ON cat_organization_alias (ror_id);
CREATE INDEX idx_org_alias_trgm   ON cat_organization_alias USING gin (alias gin_trgm_ops);

-- 一家机构只能有一条中文 LABEL：zhName 取 zh LABEL，两条文字不同的 LABEL 会让「最短者胜出」压掉审过的名字。
-- 种子（R__cat_organization_alias_seed.sql）按此规则生成：某机构已有手工 LABEL 时，Wikidata 的 LABEL 生成为 ALIAS。
CREATE UNIQUE INDEX uk_org_alias_zh_label
    ON cat_organization_alias (ror_id)
    WHERE alias_type = 'LABEL' AND lang = 'zh';

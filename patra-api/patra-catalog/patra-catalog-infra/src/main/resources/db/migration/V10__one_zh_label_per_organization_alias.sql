-- ============================================================
-- Patra Catalog — V10: 一家机构只能有一条中文 LABEL 别名（PAP-94 评审修正）
-- zhName 取别名表的 zh LABEL 优先；两条来源不同、文字不同的 LABEL 会让「最短者胜出」压掉审过的名字，
-- 所以用局部唯一索引把这条规则放进库里。种子（R__cat_organization_alias_seed.sql）按此规则生成：
-- 某机构已有手工 LABEL 时，Wikidata 的 LABEL 生成为 ALIAS。
-- ============================================================

-- 先清掉种子行：可重复迁移在本版本迁移之后重跑，会按修正后的种子重新插入；
-- 已应用 V9 的库里可能存着两条 LABEL 的旧种子，不清掉建不了唯一索引。
DELETE FROM cat_organization_alias WHERE source IN ('SEED', 'WIKIDATA');

CREATE UNIQUE INDEX uk_org_alias_zh_label
    ON cat_organization_alias (ror_id)
    WHERE alias_type = 'LABEL' AND lang = 'zh';

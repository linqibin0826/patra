-- ============================================================
-- Patra Catalog — 期刊 NLM ID 全局唯一（PAP-117）
-- interaction 以 nlm:{NLM ID} 锚定期刊对象，一个 NLM ID 只能指向一个期刊。
-- 上线前须在 mini 核对没有重复，步骤见
-- docs/patra/specs/2026-10-27-catalog-venue-identity-design.md 第 11 节。
-- ============================================================

-- 主 NLM：覆盖软删行；允许为空（多个 NULL 互不冲突）
ALTER TABLE cat_venue
    ADD CONSTRAINT uk_venue_nlm_id UNIQUE (nlm_id);

-- 标识符表里 NLM 类型的值全表唯一：主 NLM、副 NLM 都只属于一个期刊
CREATE UNIQUE INDEX uk_venue_identifier_nlm
    ON cat_venue_identifier (identifier_value)
    WHERE identifier_type = 'NLM';

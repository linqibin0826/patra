-- ============================================================
-- Patra Catalog — V13: 载体实例唯一键
-- 规范: .claude/rules/tech/transaction.md 第 3 节（并发 find-or-create 用 ON CONFLICT）
-- ============================================================

-- 同一载体、同一年份下，卷 / 期 / 版次 / 会议名相同即同一实例。
-- 这些列允许为 NULL，NULLS NOT DISTINCT 让 NULL 也参与比较，否则「只有卷号没有期号」会无限重复。
CREATE UNIQUE INDEX uk_venue_instance_identity
    ON cat_venue_instance (venue_id, volume, issue, edition, conference_name, publication_year)
    NULLS NOT DISTINCT;

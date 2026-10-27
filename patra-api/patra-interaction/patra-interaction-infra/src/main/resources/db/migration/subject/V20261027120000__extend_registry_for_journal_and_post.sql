-- ============================================================
-- subject.registry 扩到期刊与本地对象（PAP-120）
-- 类型 2 = JOURNAL（外部对象，nlm: 锚，NLM ID 按字符串存、保留前导零）；
-- 类型 3 = POST（本地对象，post: 锚，source_id 就是帖子 ID，由投稿经验子域登记、改状态，不对账）。
-- 所有者两列：本地对象必填，外部对象为空，登记后不变。
-- ============================================================

ALTER TABLE subject.registry
    ADD COLUMN owner_user_id   BIGINT   NULL,
    ADD COLUMN owner_anonymous BOOLEAN  NULL;

ALTER TABLE subject.registry DROP CONSTRAINT ck_registry_target_type;

-- ck_registry_post_key：帖子永远有 source_id（失效时也有），且锚就是 'post:' || source_id。
-- 必须显式写 source_id IS NOT NULL：否则 source_id 为 NULL 时整个表达式是 NULL，CHECK 会放行
-- （失效行又满足 ck_registry_source_present，两条都拦不住）。
ALTER TABLE subject.registry
    ADD CONSTRAINT ck_registry_target_type CHECK (target_type IN (1, 2, 3)),
    ADD CONSTRAINT ck_registry_journal_key CHECK (target_type <> 2 OR natural_key ~ '^nlm:[0-9]{1,19}[A-Z]?$'),
    ADD CONSTRAINT ck_registry_post_key CHECK (target_type <> 3 OR (source_id IS NOT NULL AND natural_key ~ '^post:[1-9][0-9]{0,18}$' AND natural_key = 'post:' || source_id)),
    ADD CONSTRAINT ck_registry_owner_pair CHECK ((owner_user_id IS NULL) = (owner_anonymous IS NULL)),
    ADD CONSTRAINT ck_registry_owner_local CHECK ((target_type = 3) = (owner_user_id IS NOT NULL));

COMMENT ON TABLE subject.registry IS 'Subject registry: one row per object that has been interacted with (external objects resolved from catalog, local objects registered by their owning subdomain); interaction data references subject_id only';
COMMENT ON COLUMN subject.registry.snapshot IS 'Display snapshot {title, journal, publishedDate, abbreviation}: fallback, unavailable display and sort keys; local objects fill it themselves';
COMMENT ON COLUMN subject.registry.target_type IS 'SubjectType code: 1 = PUBLICATION, 2 = JOURNAL, 3 = POST (local); never reused';
COMMENT ON COLUMN subject.registry.natural_key IS 'Identity anchor: pmid:…, nlm:… (string, leading zeros kept), post:{post id}';
COMMENT ON COLUMN subject.registry.source_id IS 'External: cached catalog internal ID, null when it vanished from catalog; local: the owning subdomain''s ID';
COMMENT ON COLUMN subject.registry.owner_user_id IS 'Owner user ID of a local object (POST); null for external objects; never changes';
COMMENT ON COLUMN subject.registry.owner_anonymous IS 'Whether the owner chose to be anonymous; null for external objects; never changes';

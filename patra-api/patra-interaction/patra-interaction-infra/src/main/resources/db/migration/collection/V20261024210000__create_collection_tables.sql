-- ============================================================
-- collection 子域：文献库条目、收藏夹、收藏夹条目（PAP-113）
-- 状态窄表（library_item、collection_item）按 user_id 哈希分 16 区，所有查询都带 user_id。
-- 只存用户 ID，不存用户名、昵称、头像；subject_id 不对 subject.registry 建外键（决策 B）。
-- ============================================================

-- set_updated_at()：每个 schema 一份，schema 整体搬走时不依赖别的 schema；只给 library_item 用（改备注不显式写 updated_at）
CREATE OR REPLACE FUNCTION collection.set_updated_at() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
    NEW.updated_at = now();
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ------------------------------------------------------------
-- 文献库条目：用户 × 对象唯一
-- ------------------------------------------------------------
CREATE TABLE collection.library_item
(
    user_id       BIGINT          NOT NULL,
    subject_id    BIGINT          NOT NULL,
    note          TEXT            NULL,
    collected_at  timestamptz(6)  NOT NULL,
    updated_at    timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, subject_id)
) PARTITION BY HASH (user_id);
CREATE TABLE collection.library_item_p00 PARTITION OF collection.library_item FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE collection.library_item_p01 PARTITION OF collection.library_item FOR VALUES WITH (MODULUS 16, REMAINDER 1);
CREATE TABLE collection.library_item_p02 PARTITION OF collection.library_item FOR VALUES WITH (MODULUS 16, REMAINDER 2);
CREATE TABLE collection.library_item_p03 PARTITION OF collection.library_item FOR VALUES WITH (MODULUS 16, REMAINDER 3);
CREATE TABLE collection.library_item_p04 PARTITION OF collection.library_item FOR VALUES WITH (MODULUS 16, REMAINDER 4);
CREATE TABLE collection.library_item_p05 PARTITION OF collection.library_item FOR VALUES WITH (MODULUS 16, REMAINDER 5);
CREATE TABLE collection.library_item_p06 PARTITION OF collection.library_item FOR VALUES WITH (MODULUS 16, REMAINDER 6);
CREATE TABLE collection.library_item_p07 PARTITION OF collection.library_item FOR VALUES WITH (MODULUS 16, REMAINDER 7);
CREATE TABLE collection.library_item_p08 PARTITION OF collection.library_item FOR VALUES WITH (MODULUS 16, REMAINDER 8);
CREATE TABLE collection.library_item_p09 PARTITION OF collection.library_item FOR VALUES WITH (MODULUS 16, REMAINDER 9);
CREATE TABLE collection.library_item_p10 PARTITION OF collection.library_item FOR VALUES WITH (MODULUS 16, REMAINDER 10);
CREATE TABLE collection.library_item_p11 PARTITION OF collection.library_item FOR VALUES WITH (MODULUS 16, REMAINDER 11);
CREATE TABLE collection.library_item_p12 PARTITION OF collection.library_item FOR VALUES WITH (MODULUS 16, REMAINDER 12);
CREATE TABLE collection.library_item_p13 PARTITION OF collection.library_item FOR VALUES WITH (MODULUS 16, REMAINDER 13);
CREATE TABLE collection.library_item_p14 PARTITION OF collection.library_item FOR VALUES WITH (MODULUS 16, REMAINDER 14);
CREATE TABLE collection.library_item_p15 PARTITION OF collection.library_item FOR VALUES WITH (MODULUS 16, REMAINDER 15);

COMMENT ON TABLE collection.library_item IS 'Library items: one row per (user, subject); hash-partitioned by user_id into 16';
COMMENT ON COLUMN collection.library_item.user_id IS 'Owner user ID (identity); partition key';
COMMENT ON COLUMN collection.library_item.subject_id IS 'subject.registry local ID; no cross-schema FK';
COMMENT ON COLUMN collection.library_item.note IS 'Private plain-text note; NULL when absent';
COMMENT ON COLUMN collection.library_item.collected_at IS 'When the item entered the library (UTC)';
COMMENT ON COLUMN collection.library_item.updated_at IS 'Last update time (UTC)';

CREATE INDEX idx_library_item_collected ON collection.library_item (user_id, collected_at DESC, subject_id DESC);

CREATE TRIGGER trg_library_item_updated_at
    BEFORE UPDATE ON collection.library_item
    FOR EACH ROW EXECUTE FUNCTION collection.set_updated_at();

-- ------------------------------------------------------------
-- 收藏夹：每人 ≤ 200 个，不分区
-- ------------------------------------------------------------
CREATE TABLE collection.collection
(
    collection_id BIGINT          NOT NULL,
    user_id       BIGINT          NOT NULL,
    name          VARCHAR(400)    NOT NULL,
    description   TEXT            NULL,
    visibility    VARCHAR(16)     NOT NULL,
    item_count    INTEGER         NOT NULL DEFAULT 0,
    created_at    timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (collection_id),
    CONSTRAINT ck_collection_visibility CHECK (visibility IN ('PRIVATE', 'PUBLIC')),
    CONSTRAINT ck_collection_item_count CHECK (item_count BETWEEN 0 AND 5000)
);

COMMENT ON TABLE collection.collection IS 'Collections: flat folders over library items; private by default';
COMMENT ON COLUMN collection.collection.collection_id IS 'PK (snowflake)';
COMMENT ON COLUMN collection.collection.user_id IS 'Owner user ID (identity)';
COMMENT ON COLUMN collection.collection.name IS 'Name: 1-100 graphemes, unique per owner';
COMMENT ON COLUMN collection.collection.description IS 'Optional plain-text description, up to 500 graphemes';
COMMENT ON COLUMN collection.collection.visibility IS 'PRIVATE or PUBLIC';
COMMENT ON COLUMN collection.collection.item_count IS 'Maintained count of collection_item rows, updated under the owner lock';
COMMENT ON COLUMN collection.collection.created_at IS 'Creation time (UTC)';
COMMENT ON COLUMN collection.collection.updated_at IS 'Last change of text, visibility or membership (UTC)';

CREATE UNIQUE INDEX uk_collection_owner_name ON collection.collection (user_id, name);
CREATE INDEX idx_collection_owner_updated ON collection.collection (user_id, updated_at DESC);
CREATE INDEX idx_collection_owner_public ON collection.collection (user_id) WHERE visibility = 'PUBLIC';

-- 不挂 set_updated_at：每条写入都显式给 updated_at（应用注入的时钟）。触发器在「新值等于旧值」时会换成数据库时间，
-- 同一时刻建夹再放条目时更新时间会早于创建时间

-- ------------------------------------------------------------
-- 收藏夹条目：收藏夹 × 对象唯一；user_id 是收藏夹所有者，冗余进来做分区键
-- ------------------------------------------------------------
CREATE TABLE collection.collection_item
(
    user_id       BIGINT          NOT NULL,
    collection_id BIGINT          NOT NULL,
    subject_id    BIGINT          NOT NULL,
    added_at      timestamptz(6)  NOT NULL,
    PRIMARY KEY (user_id, collection_id, subject_id)
) PARTITION BY HASH (user_id);
CREATE TABLE collection.collection_item_p00 PARTITION OF collection.collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE collection.collection_item_p01 PARTITION OF collection.collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 1);
CREATE TABLE collection.collection_item_p02 PARTITION OF collection.collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 2);
CREATE TABLE collection.collection_item_p03 PARTITION OF collection.collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 3);
CREATE TABLE collection.collection_item_p04 PARTITION OF collection.collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 4);
CREATE TABLE collection.collection_item_p05 PARTITION OF collection.collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 5);
CREATE TABLE collection.collection_item_p06 PARTITION OF collection.collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 6);
CREATE TABLE collection.collection_item_p07 PARTITION OF collection.collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 7);
CREATE TABLE collection.collection_item_p08 PARTITION OF collection.collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 8);
CREATE TABLE collection.collection_item_p09 PARTITION OF collection.collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 9);
CREATE TABLE collection.collection_item_p10 PARTITION OF collection.collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 10);
CREATE TABLE collection.collection_item_p11 PARTITION OF collection.collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 11);
CREATE TABLE collection.collection_item_p12 PARTITION OF collection.collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 12);
CREATE TABLE collection.collection_item_p13 PARTITION OF collection.collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 13);
CREATE TABLE collection.collection_item_p14 PARTITION OF collection.collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 14);
CREATE TABLE collection.collection_item_p15 PARTITION OF collection.collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 15);

COMMENT ON TABLE collection.collection_item IS 'Collection membership of library items; hash-partitioned by user_id into 16';
COMMENT ON COLUMN collection.collection_item.user_id IS 'Collection owner user ID; partition key';
COMMENT ON COLUMN collection.collection_item.collection_id IS 'collection.collection ID; integrity kept by the write path';
COMMENT ON COLUMN collection.collection_item.subject_id IS 'subject.registry local ID; must be in the owner library';
COMMENT ON COLUMN collection.collection_item.added_at IS 'When the item was added to this collection (UTC)';

CREATE INDEX idx_collection_item_added ON collection.collection_item (user_id, collection_id, added_at DESC, subject_id DESC);
CREATE INDEX idx_collection_item_subject ON collection.collection_item (user_id, subject_id);

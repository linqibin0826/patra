-- collection 子域：文献库条目、收藏夹、收藏夹条目、每个对象的收藏人数
-- 两张条目表按 user_id 哈希分 16 区，所有查询都带 user_id；主键以分区键打头 (user_id, id)。
-- 只存用户 ID；subject_id 引用 itr_subject.id，collection_id 引用 itr_collection.id，引用关系由应用保证，不建外键。

-- ============================================================
-- itr_library_item：文献库条目，用户 × 对象唯一
-- ============================================================
CREATE TABLE itr_library_item
(
    id           BIGINT          NOT NULL,
    user_id      BIGINT          NOT NULL,
    subject_id   BIGINT          NOT NULL,
    note         TEXT            NULL,
    collected_at timestamptz(6)  NOT NULL,
    created_at   timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, id)
) PARTITION BY HASH (user_id);
CREATE TABLE itr_library_item_p00 PARTITION OF itr_library_item FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE itr_library_item_p01 PARTITION OF itr_library_item FOR VALUES WITH (MODULUS 16, REMAINDER 1);
CREATE TABLE itr_library_item_p02 PARTITION OF itr_library_item FOR VALUES WITH (MODULUS 16, REMAINDER 2);
CREATE TABLE itr_library_item_p03 PARTITION OF itr_library_item FOR VALUES WITH (MODULUS 16, REMAINDER 3);
CREATE TABLE itr_library_item_p04 PARTITION OF itr_library_item FOR VALUES WITH (MODULUS 16, REMAINDER 4);
CREATE TABLE itr_library_item_p05 PARTITION OF itr_library_item FOR VALUES WITH (MODULUS 16, REMAINDER 5);
CREATE TABLE itr_library_item_p06 PARTITION OF itr_library_item FOR VALUES WITH (MODULUS 16, REMAINDER 6);
CREATE TABLE itr_library_item_p07 PARTITION OF itr_library_item FOR VALUES WITH (MODULUS 16, REMAINDER 7);
CREATE TABLE itr_library_item_p08 PARTITION OF itr_library_item FOR VALUES WITH (MODULUS 16, REMAINDER 8);
CREATE TABLE itr_library_item_p09 PARTITION OF itr_library_item FOR VALUES WITH (MODULUS 16, REMAINDER 9);
CREATE TABLE itr_library_item_p10 PARTITION OF itr_library_item FOR VALUES WITH (MODULUS 16, REMAINDER 10);
CREATE TABLE itr_library_item_p11 PARTITION OF itr_library_item FOR VALUES WITH (MODULUS 16, REMAINDER 11);
CREATE TABLE itr_library_item_p12 PARTITION OF itr_library_item FOR VALUES WITH (MODULUS 16, REMAINDER 12);
CREATE TABLE itr_library_item_p13 PARTITION OF itr_library_item FOR VALUES WITH (MODULUS 16, REMAINDER 13);
CREATE TABLE itr_library_item_p14 PARTITION OF itr_library_item FOR VALUES WITH (MODULUS 16, REMAINDER 14);
CREATE TABLE itr_library_item_p15 PARTITION OF itr_library_item FOR VALUES WITH (MODULUS 16, REMAINDER 15);
CREATE UNIQUE INDEX uk_itr_library_item_user_subject ON itr_library_item (user_id, subject_id);
CREATE INDEX idx_itr_library_item_collected ON itr_library_item (user_id, collected_at DESC, subject_id DESC);

COMMENT ON TABLE itr_library_item IS '文献库条目：每个（用户、对象）一行，按 user_id 哈希分 16 区';
COMMENT ON COLUMN itr_library_item.user_id IS '所有者用户 ID，分区键';
COMMENT ON COLUMN itr_library_item.subject_id IS '收藏的对象（itr_subject.id）';
COMMENT ON COLUMN itr_library_item.note IS '私人纯文本备注，没有为空';
COMMENT ON COLUMN itr_library_item.collected_at IS '收进文献库的时间';

CREATE TRIGGER trg_itr_library_item_audit_times
    BEFORE INSERT OR UPDATE ON itr_library_item
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- itr_collection：收藏夹，每人不超过 200 个，不分区
-- ============================================================
CREATE TABLE itr_collection
(
    id                 BIGINT          NOT NULL,
    user_id            BIGINT          NOT NULL,
    name               VARCHAR(400)    NOT NULL,
    description        TEXT            NULL,
    visibility         VARCHAR(16)     NOT NULL,
    item_count         INTEGER         NOT NULL DEFAULT 0,
    content_changed_at timestamptz(6)  NOT NULL,
    record_remarks     jsonb           NULL,
    ip_address         INET            NULL,
    created_at         timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         BIGINT          NULL,
    updated_at         timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         BIGINT          NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_itr_collection_visibility CHECK (visibility IN ('PRIVATE', 'PUBLIC')),
    CONSTRAINT ck_itr_collection_item_count CHECK (item_count BETWEEN 0 AND 5000)
);
CREATE UNIQUE INDEX uk_itr_collection_owner_name ON itr_collection (user_id, name);
CREATE INDEX idx_itr_collection_owner_changed ON itr_collection (user_id, content_changed_at DESC);
CREATE INDEX idx_itr_collection_owner_public ON itr_collection (user_id) WHERE visibility = 'PUBLIC';

COMMENT ON TABLE itr_collection IS '收藏夹：文献库条目上的平铺文件夹，默认私有';
COMMENT ON COLUMN itr_collection.user_id IS '所有者用户 ID';
COMMENT ON COLUMN itr_collection.name IS '名称：1～100 个字，同一所有者下唯一';
COMMENT ON COLUMN itr_collection.description IS '可选的纯文本简介，最多 500 个字';
COMMENT ON COLUMN itr_collection.visibility IS '可见性：PRIVATE 或 PUBLIC';
COMMENT ON COLUMN itr_collection.item_count IS '收藏夹里的条目数，在所有者锁内随条目增删维护';
COMMENT ON COLUMN itr_collection.content_changed_at IS '内容最近一次变化的时间（建夹、改名或简介、改可见性、增减文献），由应用写；列表按它排序，接口的「更新于」取它';

CREATE TRIGGER trg_itr_collection_audit_times
    BEFORE INSERT OR UPDATE ON itr_collection
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- itr_collection_item：收藏夹条目，收藏夹 × 对象唯一；user_id 是收藏夹所有者，冗余进来做分区键
-- ============================================================
CREATE TABLE itr_collection_item
(
    id            BIGINT          NOT NULL,
    user_id       BIGINT          NOT NULL,
    collection_id BIGINT          NOT NULL,
    subject_id    BIGINT          NOT NULL,
    added_at      timestamptz(6)  NOT NULL,
    created_at    timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, id)
) PARTITION BY HASH (user_id);
CREATE TABLE itr_collection_item_p00 PARTITION OF itr_collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE itr_collection_item_p01 PARTITION OF itr_collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 1);
CREATE TABLE itr_collection_item_p02 PARTITION OF itr_collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 2);
CREATE TABLE itr_collection_item_p03 PARTITION OF itr_collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 3);
CREATE TABLE itr_collection_item_p04 PARTITION OF itr_collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 4);
CREATE TABLE itr_collection_item_p05 PARTITION OF itr_collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 5);
CREATE TABLE itr_collection_item_p06 PARTITION OF itr_collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 6);
CREATE TABLE itr_collection_item_p07 PARTITION OF itr_collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 7);
CREATE TABLE itr_collection_item_p08 PARTITION OF itr_collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 8);
CREATE TABLE itr_collection_item_p09 PARTITION OF itr_collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 9);
CREATE TABLE itr_collection_item_p10 PARTITION OF itr_collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 10);
CREATE TABLE itr_collection_item_p11 PARTITION OF itr_collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 11);
CREATE TABLE itr_collection_item_p12 PARTITION OF itr_collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 12);
CREATE TABLE itr_collection_item_p13 PARTITION OF itr_collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 13);
CREATE TABLE itr_collection_item_p14 PARTITION OF itr_collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 14);
CREATE TABLE itr_collection_item_p15 PARTITION OF itr_collection_item FOR VALUES WITH (MODULUS 16, REMAINDER 15);
CREATE UNIQUE INDEX uk_itr_collection_item_member ON itr_collection_item (user_id, collection_id, subject_id);
CREATE INDEX idx_itr_collection_item_added ON itr_collection_item (user_id, collection_id, added_at DESC, subject_id DESC);
CREATE INDEX idx_itr_collection_item_subject ON itr_collection_item (user_id, subject_id);

COMMENT ON TABLE itr_collection_item IS '收藏夹条目：文献库条目在哪些收藏夹里，按 user_id 哈希分 16 区';
COMMENT ON COLUMN itr_collection_item.user_id IS '收藏夹所有者用户 ID，分区键';
COMMENT ON COLUMN itr_collection_item.collection_id IS '所属收藏夹（itr_collection.id）';
COMMENT ON COLUMN itr_collection_item.subject_id IS '对象（itr_subject.id），必须在所有者的文献库里';
COMMENT ON COLUMN itr_collection_item.added_at IS '放进这个收藏夹的时间';

CREATE TRIGGER trg_itr_collection_item_audit_times
    BEFORE INSERT OR UPDATE ON itr_collection_item
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- itr_library_subject_stats：每个对象的收藏人数（= 这个对象的文献库条目数，一人放进多个收藏夹只算一次）
-- 与文献库条目同事务维护：条目真插入 / 真删除才原子 ±1，不按明细重数覆盖。没有行表示 0；减到 0 保留行。
-- ============================================================
CREATE TABLE itr_library_subject_stats
(
    id              BIGINT          NOT NULL,
    subject_id      BIGINT          NOT NULL,
    collector_count INTEGER         NOT NULL DEFAULT 0,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_itr_library_subject_stats_collectors CHECK (collector_count >= 0)
);
CREATE UNIQUE INDEX uk_itr_library_subject_stats_subject ON itr_library_subject_stats (subject_id);

COMMENT ON TABLE itr_library_subject_stats IS '每个对象的收藏人数；没有行表示 0，随文献库条目增删同事务变化';
COMMENT ON COLUMN itr_library_subject_stats.subject_id IS '对象（itr_subject.id）';
COMMENT ON COLUMN itr_library_subject_stats.collector_count IS '这个对象的文献库条目数（收藏过它的人数）';

CREATE TRIGGER trg_itr_library_subject_stats_audit_times
    BEFORE INSERT OR UPDATE ON itr_library_subject_stats
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

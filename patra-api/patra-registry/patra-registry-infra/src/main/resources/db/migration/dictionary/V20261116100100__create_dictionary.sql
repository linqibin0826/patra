-- 字典子域：字典类型、字典条目、条目的外部编码、参考标准
-- 引用关系由应用保证，不建外键；种子数据在 db/seed 的 R__ 脚本里

-- ------------------------------------------------------------
-- 字典类型
-- ------------------------------------------------------------
CREATE TABLE sys_dict_type
(
    id                 BIGINT       NOT NULL PRIMARY KEY,
    type_code          VARCHAR(64)  NOT NULL,
    type_name          VARCHAR(200) NOT NULL,
    description        VARCHAR(500) NULL,
    allow_custom_items BOOLEAN      NOT NULL DEFAULT false,
    is_system          BOOLEAN      NOT NULL DEFAULT true,
    reserved_json      JSONB        NULL,
    deleted_at         TIMESTAMPTZ  NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         BIGINT       NULL,
    updated_by         BIGINT       NULL,
    ip_address         INET         NULL,
    record_remarks     JSONB        NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_sys_dict_type_code_format CHECK (type_code ~ '^[a-z0-9_]{1,64}$')
);
CREATE UNIQUE INDEX uk_sys_dict_type_code ON sys_dict_type (type_code);
CREATE TRIGGER trg_sys_dict_type_audit_times
    BEFORE INSERT OR UPDATE ON sys_dict_type
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE sys_dict_type IS '字典类型，如国家、语言';
COMMENT ON COLUMN sys_dict_type.type_code IS '类型编码，小写下划线，跨环境稳定，如 country';
COMMENT ON COLUMN sys_dict_type.type_name IS '类型的显示名';
COMMENT ON COLUMN sys_dict_type.description IS '用途和边界说明';
COMMENT ON COLUMN sys_dict_type.allow_custom_items IS '是否允许业务在这个类型下自建条目';
COMMENT ON COLUMN sys_dict_type.is_system IS '是否系统内置：true 系统内置，false 业务定义';
COMMENT ON COLUMN sys_dict_type.reserved_json IS '额外元数据，如界面颜色、图标、排序策略';
COMMENT ON COLUMN sys_dict_type.deleted_at IS '删除时间，空表示未删除';

-- ------------------------------------------------------------
-- 字典条目
-- ------------------------------------------------------------
CREATE TABLE sys_dict_item
(
    id              BIGINT       NOT NULL PRIMARY KEY,
    dict_type_id    BIGINT       NOT NULL,
    item_code       VARCHAR(64)  NOT NULL,
    item_name       VARCHAR(200) NOT NULL,
    short_name      VARCHAR(64)  NULL,
    description     VARCHAR(500) NULL,
    display_order   INTEGER      NOT NULL DEFAULT 100,
    is_default      BOOLEAN      NOT NULL DEFAULT false,
    enabled         BOOLEAN      NOT NULL DEFAULT true,
    label_color     VARCHAR(32)  NULL,
    icon_name       VARCHAR(64)  NULL,
    attributes_json JSONB        NULL,
    deleted_at      TIMESTAMPTZ  NULL,
    -- 只有启用、未删除的默认条目才等于所属类型 ID，配合唯一索引保证每个类型最多一个默认条目
    default_key     BIGINT GENERATED ALWAYS AS
        (CASE WHEN (is_default AND enabled AND deleted_at IS NULL) THEN dict_type_id END) STORED,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      BIGINT       NULL,
    updated_by      BIGINT       NULL,
    ip_address      INET         NULL,
    record_remarks  JSONB        NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_sys_dict_item_code_format CHECK (item_code ~ '^[A-Za-z0-9_]{1,64}$'),
    CONSTRAINT ck_sys_dict_item_display_order CHECK (display_order >= 0)
);
CREATE UNIQUE INDEX uk_sys_dict_item_type_code ON sys_dict_item (dict_type_id, item_code);
CREATE UNIQUE INDEX uk_sys_dict_item_default_per_type ON sys_dict_item (default_key);
CREATE TRIGGER trg_sys_dict_item_audit_times
    BEFORE INSERT OR UPDATE ON sys_dict_item
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE sys_dict_item IS '字典类型下的条目，如国家里的 CN、语言里的 en';
COMMENT ON COLUMN sys_dict_item.dict_type_id IS '所属字典类型（sys_dict_type.id）';
COMMENT ON COLUMN sys_dict_item.item_code IS '条目编码，在所属类型内唯一，如 CN、en';
COMMENT ON COLUMN sys_dict_item.item_name IS '条目的显示名';
COMMENT ON COLUMN sys_dict_item.short_name IS '简称，紧凑的界面用';
COMMENT ON COLUMN sys_dict_item.description IS '语义、边界、兼容性说明';
COMMENT ON COLUMN sys_dict_item.display_order IS '显示顺序，小的在前';
COMMENT ON COLUMN sys_dict_item.is_default IS '是否所属类型的默认条目，每个类型最多一个';
COMMENT ON COLUMN sys_dict_item.enabled IS '是否启用，停用的条目业务里不可选';
COMMENT ON COLUMN sys_dict_item.label_color IS '标签颜色，#AABBCC 或语义名称';
COMMENT ON COLUMN sys_dict_item.icon_name IS '图标名称';
COMMENT ON COLUMN sys_dict_item.attributes_json IS '扩展属性，新加属性不用改表';
COMMENT ON COLUMN sys_dict_item.deleted_at IS '删除时间，空表示未删除';
COMMENT ON COLUMN sys_dict_item.default_key IS '生成列：启用、未删除的默认条目等于所属类型 ID，其余为空，唯一索引靠它限制每个类型一个默认条目';

-- ------------------------------------------------------------
-- 条目的外部编码：把外部标准里的编码或名称解析成字典条目
-- ------------------------------------------------------------
CREATE TABLE sys_dict_item_alias
(
    id              BIGINT       NOT NULL PRIMARY KEY,
    dict_item_id    BIGINT       NOT NULL,
    source_standard VARCHAR(64)  NOT NULL,
    external_code   VARCHAR(128) NOT NULL,
    external_label  VARCHAR(200) NULL,
    notes           VARCHAR(500) NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_sys_dict_item_alias_standard_format CHECK (source_standard ~ '^[a-z0-9_\-]{1,64}$')
);
CREATE UNIQUE INDEX uk_sys_dict_item_alias_standard_code ON sys_dict_item_alias (source_standard, external_code);
CREATE INDEX idx_sys_dict_item_alias_item ON sys_dict_item_alias (dict_item_id);
CREATE TRIGGER trg_sys_dict_item_alias_audit_times
    BEFORE INSERT OR UPDATE ON sys_dict_item_alias
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE sys_dict_item_alias IS '字典条目在外部标准里的编码或名称，用于把外部数据解析成字典条目';
COMMENT ON COLUMN sys_dict_item_alias.dict_item_id IS '对应的字典条目（sys_dict_item.id）';
COMMENT ON COLUMN sys_dict_item_alias.source_standard IS '外部标准，小写，如 name_en、iso_639_3';
COMMENT ON COLUMN sys_dict_item_alias.external_code IS '外部标准里的编码或名称，同一标准内唯一';
COMMENT ON COLUMN sys_dict_item_alias.external_label IS '外部标准里的显示名';
COMMENT ON COLUMN sys_dict_item_alias.notes IS '映射说明，如差异、来源';

-- ------------------------------------------------------------
-- 参考标准：某个字典类型允许使用的外部标准
-- ------------------------------------------------------------
CREATE TABLE sys_reference_standard
(
    id             BIGINT       NOT NULL PRIMARY KEY,
    dict_type_code VARCHAR(64)  NOT NULL,
    standard_code  VARCHAR(64)  NOT NULL,
    standard_name  VARCHAR(200) NOT NULL,
    description    VARCHAR(500) NULL,
    display_order  INTEGER      NOT NULL DEFAULT 100,
    is_canonical   BOOLEAN      NOT NULL DEFAULT false,
    enabled        BOOLEAN      NOT NULL DEFAULT true,
    -- 只有启用的规范标准才等于所属类型编码，配合唯一索引保证每个类型最多一个规范标准
    canonical_key  VARCHAR(64) GENERATED ALWAYS AS
        (CASE WHEN (is_canonical AND enabled) THEN dict_type_code END) STORED,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     BIGINT       NULL,
    updated_by     BIGINT       NULL,
    ip_address     INET         NULL,
    record_remarks JSONB        NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_sys_reference_standard_code_format CHECK (standard_code ~ '^[A-Z0-9_]{1,64}$'),
    CONSTRAINT ck_sys_reference_standard_type_format CHECK (dict_type_code ~ '^[a-z0-9_]{1,64}$')
);
CREATE UNIQUE INDEX uk_sys_reference_standard_type_code ON sys_reference_standard (dict_type_code, standard_code);
CREATE UNIQUE INDEX uk_sys_reference_standard_canonical ON sys_reference_standard (canonical_key);
CREATE TRIGGER trg_sys_reference_standard_audit_times
    BEFORE INSERT OR UPDATE ON sys_reference_standard
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

COMMENT ON TABLE sys_reference_standard IS '某个字典类型允许使用的参考标准，如国家的 ISO 3166-1 alpha-2';
COMMENT ON COLUMN sys_reference_standard.dict_type_code IS '所属字典类型编码（sys_dict_type.type_code）';
COMMENT ON COLUMN sys_reference_standard.standard_code IS '标准编码，大写下划线，如 ISO_3166_1_ALPHA2、NAME_EN';
COMMENT ON COLUMN sys_reference_standard.standard_name IS '标准名称';
COMMENT ON COLUMN sys_reference_standard.description IS '说明';
COMMENT ON COLUMN sys_reference_standard.display_order IS '显示顺序，小的在前';
COMMENT ON COLUMN sys_reference_standard.is_canonical IS '是否所属类型的规范标准：条目编码遵循它，每个类型最多一个';
COMMENT ON COLUMN sys_reference_standard.enabled IS '是否启用，停用的标准解析时会被拒绝';
COMMENT ON COLUMN sys_reference_standard.canonical_key IS '生成列：启用的规范标准等于所属类型编码，其余为空，唯一索引靠它限制每个类型一个规范标准';

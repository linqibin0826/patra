-- =====================================================================
-- Patra Registry — PostgreSQL 17 baseline schema
-- 合并自原 V1.0.0 / V1.0.1 / V1.0.2；已移除 6 条物理 FK、
-- ENGINE/CHARSET/COLLATE、DROP TABLE IF EXISTS、ROW_FORMAT、IF NOT EXISTS
-- =====================================================================

-- 公共触发器函数：仅当 UPDATE 未显式修改 updated_at 时（即非 JPA 路径），自动填充 now()
-- JPA 路径由 Hibernate @LastModifiedDate 显式赋值，会先于触发器执行，触发器跳过
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
    NEW.updated_at = now();
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;


/* ====================================================================
 * 表: sys_dict_type - 字典类型
 * 语义: 字典"类型"的元数据 (例如: http_method, endpoint_usage)
 * 要点:
 *  - type_code 采用小写下划线命名，跨环境稳定
 *  - allow_custom_items 控制业务是否可以扩展该类型下的条目
 *  - is_system 标识系统内置类型
 * ==================================================================== */
CREATE TABLE sys_dict_type
(
    id                 BIGINT         NOT NULL,
    type_code          VARCHAR(64)    NOT NULL,
    type_name          VARCHAR(200)   NOT NULL,
    description        VARCHAR(500)   NULL,
    allow_custom_items BOOLEAN        NOT NULL DEFAULT false,
    is_system          BOOLEAN        NOT NULL DEFAULT true,
    reserved_json      jsonb          NULL,

    -- 审计与治理
    record_remarks     jsonb          NULL,
    created_at         timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         BIGINT         NULL,
    created_by_name    VARCHAR(100)   NULL,
    updated_at         timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         BIGINT         NULL,
    updated_by_name    VARCHAR(100)   NULL,
    version            BIGINT         NOT NULL DEFAULT 0,
    ip_address         bytea          NULL,
    deleted_at         timestamptz(6) NULL     DEFAULT NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_sys_dict_type__code UNIQUE (type_code),
    CONSTRAINT chk_sys_dict_type__code_format CHECK (type_code ~ '^[a-z0-9_]{1,64}$')
);

COMMENT ON TABLE sys_dict_type IS '系统字典 - 类型';
COMMENT ON COLUMN sys_dict_type.id IS '主键；内部唯一标识符(雪花ID)';
COMMENT ON COLUMN sys_dict_type.type_code IS '类型编码: 小写下划线格式，例如 http_method (跨环境稳定键)';
COMMENT ON COLUMN sys_dict_type.type_name IS '类型显示名称 (人类可读)';
COMMENT ON COLUMN sys_dict_type.description IS '描述 (用途、边界、备注)';
COMMENT ON COLUMN sys_dict_type.allow_custom_items IS '是否允许在此类型下自定义条目';
COMMENT ON COLUMN sys_dict_type.is_system IS '系统内置: true=系统, false=业务定义';
COMMENT ON COLUMN sys_dict_type.reserved_json IS '额外元数据 (例如: UI颜色/图标/排序策略)';
COMMENT ON COLUMN sys_dict_type.record_remarks IS '变更备注: JSON数组，记录历史备注';
COMMENT ON COLUMN sys_dict_type.created_at IS '创建时间 (UTC)';
COMMENT ON COLUMN sys_dict_type.created_by IS '创建人ID (逻辑外键)';
COMMENT ON COLUMN sys_dict_type.created_by_name IS '创建人姓名/登录名快照';
COMMENT ON COLUMN sys_dict_type.updated_at IS '最后更新时间 (UTC)';
COMMENT ON COLUMN sys_dict_type.updated_by IS '最后更新人ID (逻辑外键)';
COMMENT ON COLUMN sys_dict_type.updated_by_name IS '最后更新人姓名/登录名快照';
COMMENT ON COLUMN sys_dict_type.version IS '乐观锁版本 (CAS)';
COMMENT ON COLUMN sys_dict_type.ip_address IS '请求者IP (二进制, IPv4/IPv6)';
COMMENT ON COLUMN sys_dict_type.deleted_at IS '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)';

CREATE TRIGGER trg_sys_dict_type_updated_at
    BEFORE UPDATE ON sys_dict_type
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


/* ====================================================================
 * 表: sys_dict_item - 字典条目
 * 语义: 字典类型下的具体值 (例如: http_method 的 GET/POST)
 * 要点:
 *  - item_code 采用大写下划线格式作为稳定键
 *  - default_key 是生成列: 仅当 is_default AND enabled AND deleted_at IS NULL 时等于 type_id
 *  - enabled 控制在业务逻辑中是否可选
 *  - attributes_json 用于扩展性 (新属性无需DDL)
 * ==================================================================== */
CREATE TABLE sys_dict_item
(
    id              BIGINT         NOT NULL,
    type_id         BIGINT         NOT NULL,
    item_code       VARCHAR(64)    NOT NULL,
    item_name       VARCHAR(200)   NOT NULL,
    short_name      VARCHAR(64)    NULL,
    description     VARCHAR(500)   NULL,
    display_order   INTEGER        NOT NULL DEFAULT 100 CHECK (display_order >= 0),
    is_default      BOOLEAN        NOT NULL DEFAULT false,
    enabled         BOOLEAN        NOT NULL DEFAULT true,
    label_color     VARCHAR(32)    NULL,
    icon_name       VARCHAR(64)    NULL,
    attributes_json jsonb          NULL,

    -- 生成列: 仅当 is_default AND enabled AND not deleted 时等于 type_id; 否则为 NULL
    default_key     BIGINT GENERATED ALWAYS AS
        (CASE WHEN (is_default AND enabled AND deleted_at IS NULL) THEN type_id ELSE NULL END) STORED,

    -- 审计与治理
    record_remarks  jsonb          NULL,
    created_at      timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT         NULL,
    created_by_name VARCHAR(100)   NULL,
    updated_at      timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT         NULL,
    updated_by_name VARCHAR(100)   NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    ip_address      bytea          NULL,
    deleted_at      timestamptz(6) NULL     DEFAULT NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_sys_dict_item__type_code UNIQUE (type_id, item_code),
    CONSTRAINT uk_sys_dict_item__default_per_type UNIQUE (default_key),
    CONSTRAINT chk_sys_dict_item__code_format CHECK (item_code ~ '^[A-Za-z0-9_]{1,64}$')
);

COMMENT ON TABLE sys_dict_item IS '系统字典 - 条目';
COMMENT ON COLUMN sys_dict_item.id IS '主键；内部唯一标识符(雪花ID)';
COMMENT ON COLUMN sys_dict_item.type_id IS '父类型ID (逻辑外键 -> sys_dict_type.id)';
COMMENT ON COLUMN sys_dict_item.item_code IS '条目编码: 稳定键，例如 AF/US (国家类型用大写) 或 en/zh (语言类型用小写 BCP 47)';
COMMENT ON COLUMN sys_dict_item.item_name IS '条目显示名称 (默认语言)';
COMMENT ON COLUMN sys_dict_item.short_name IS '简称/缩写名称 (紧凑UI)';
COMMENT ON COLUMN sys_dict_item.description IS '备注/说明 (语义、边界、兼容性)';
COMMENT ON COLUMN sys_dict_item.display_order IS '显示顺序 (数值越小越靠前)';
COMMENT ON COLUMN sys_dict_item.is_default IS '是否默认值 (每类型最多一个; 见 default_key 唯一约束)';
COMMENT ON COLUMN sys_dict_item.enabled IS '是否启用: true=是, false=禁用';
COMMENT ON COLUMN sys_dict_item.label_color IS '标签颜色 (#AABBCC 或语义名称)';
COMMENT ON COLUMN sys_dict_item.icon_name IS '图标名称 (用于UI)';
COMMENT ON COLUMN sys_dict_item.attributes_json IS '扩展属性 (别名/提示/兼容标志等)';
COMMENT ON COLUMN sys_dict_item.default_key IS '生成列，用于唯一键以强制每类型一个默认值';
COMMENT ON COLUMN sys_dict_item.record_remarks IS '变更备注: JSON数组';
COMMENT ON COLUMN sys_dict_item.created_at IS '创建时间 (UTC)';
COMMENT ON COLUMN sys_dict_item.updated_at IS '最后更新时间 (UTC)';
COMMENT ON COLUMN sys_dict_item.version IS '乐观锁版本 (CAS)';
COMMENT ON COLUMN sys_dict_item.ip_address IS '请求者IP (二进制, IPv4/IPv6)';
COMMENT ON COLUMN sys_dict_item.deleted_at IS '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)';

CREATE TRIGGER trg_sys_dict_item_updated_at
    BEFORE UPDATE ON sys_dict_item
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


/* ====================================================================
 * 表: sys_dict_item_alias - 字典条目外部映射
 * 语义: 来自外部标准或遗留系统的条目别名/编码，用于解析与集成
 * 要点:
 *  - (source_standard, external_code) 必须全局唯一以避免冲突
 *  - 可选的 external_label 存储外部显示名称
 *  - 典型 source_standard 示例: iso_3166_1_alpha2、global (推荐小写)
 * 注: 无 updated_at 字段，不添加 BEFORE UPDATE 触发器
 * ==================================================================== */
CREATE TABLE sys_dict_item_alias
(
    id              BIGINT       NOT NULL,
    item_id         BIGINT       NOT NULL,
    source_standard VARCHAR(64)  NOT NULL,
    external_code   VARCHAR(128) NOT NULL,
    external_label  VARCHAR(200) NULL,
    notes           VARCHAR(500) NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_dict_alias__std_code UNIQUE (source_standard, external_code),
    CONSTRAINT chk_dict_alias__std_format CHECK (source_standard ~ '^[a-z0-9_\-]{1,64}$')
);

COMMENT ON TABLE sys_dict_item_alias IS '系统字典 - 外部映射';
COMMENT ON COLUMN sys_dict_item_alias.id IS '主键；内部唯一标识符(雪花ID)';
COMMENT ON COLUMN sys_dict_item_alias.item_id IS '条目ID (逻辑外键 -> sys_dict_item.id)';
COMMENT ON COLUMN sys_dict_item_alias.source_standard IS '来源标准标识符，例如 iso_3166_1_alpha2 或 global (推荐小写下划线或短横线格式)';
COMMENT ON COLUMN sys_dict_item_alias.external_code IS '外部编码/值 (作为映射键)';
COMMENT ON COLUMN sys_dict_item_alias.external_label IS '外部显示名称 (可选)';
COMMENT ON COLUMN sys_dict_item_alias.notes IS '备注/映射说明 (差异、兼容性、来源链接等)';


/* ====================================================================
 * 表: sys_reference_standard - 来源标准目录
 * 语义: 维护某字典类型下允许的来源标准，用于校验与治理
 * 要点:
 *  - 标准代码固定为大写下划线格式
 *  - dict_type_code 关联字典类型
 *  - is_canonical 标记该类型的规范标准（item_code 遵循的格式）
 *  - 每个字典类型只能有一个规范标准（通过生成列约束）
 * ==================================================================== */
CREATE TABLE sys_reference_standard
(
    id              BIGINT         NOT NULL,
    dict_type_code  VARCHAR(64)    NOT NULL,
    standard_code   VARCHAR(64)    NOT NULL,
    standard_name   VARCHAR(200)   NOT NULL,
    description     VARCHAR(500)   NULL,
    display_order   INTEGER        NOT NULL DEFAULT 100,
    is_canonical    BOOLEAN        NOT NULL DEFAULT false,
    enabled         BOOLEAN        NOT NULL DEFAULT true,

    -- 生成列: 仅当 is_canonical AND enabled 时等于 dict_type_code; 否则为 NULL
    canonical_key   VARCHAR(64) GENERATED ALWAYS AS
        (CASE WHEN (is_canonical AND enabled) THEN dict_type_code ELSE NULL END) STORED,

    -- 审计与治理
    record_remarks  jsonb          NULL,
    created_at      timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT         NULL,
    created_by_name VARCHAR(100)   NULL,
    updated_at      timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT         NULL,
    updated_by_name VARCHAR(100)   NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    ip_address      bytea          NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_ref_standard__type_code UNIQUE (dict_type_code, standard_code),
    CONSTRAINT uk_ref_standard__canonical_per_type UNIQUE (canonical_key),
    CONSTRAINT chk_ref_standard__code_format CHECK (standard_code ~ '^[A-Z0-9_]{1,64}$'),
    CONSTRAINT chk_ref_standard__type_format CHECK (dict_type_code ~ '^[a-z0-9_]{1,64}$')
);

COMMENT ON TABLE sys_reference_standard IS '系统参考标准';
COMMENT ON COLUMN sys_reference_standard.id IS '主键；内部唯一标识符(雪花ID)';
COMMENT ON COLUMN sys_reference_standard.dict_type_code IS '所属字典类型代码 (逻辑外键 -> sys_dict_type.type_code)';
COMMENT ON COLUMN sys_reference_standard.standard_code IS '标准代码(如 ISO_3166_1_ALPHA2 或 NAME_EN)';
COMMENT ON COLUMN sys_reference_standard.standard_name IS '标准名称';
COMMENT ON COLUMN sys_reference_standard.description IS '描述';
COMMENT ON COLUMN sys_reference_standard.display_order IS '显示顺序';
COMMENT ON COLUMN sys_reference_standard.is_canonical IS '是否为该类型的规范标准: true=是, false=否 (每类型最多一个)';
COMMENT ON COLUMN sys_reference_standard.enabled IS '是否启用: true=启用, false=禁用';
COMMENT ON COLUMN sys_reference_standard.canonical_key IS '生成列，用于唯一键以强制每类型一个规范标准';
COMMENT ON COLUMN sys_reference_standard.record_remarks IS '变更备注: JSON数组';
COMMENT ON COLUMN sys_reference_standard.created_at IS '创建时间 (UTC)';
COMMENT ON COLUMN sys_reference_standard.updated_at IS '最后更新时间 (UTC)';
COMMENT ON COLUMN sys_reference_standard.version IS '乐观锁版本 (CAS)';
COMMENT ON COLUMN sys_reference_standard.ip_address IS '请求者IP (二进制, IPv4/IPv6)';

CREATE TRIGGER trg_sys_reference_standard_updated_at
    BEFORE UPDATE ON sys_reference_standard
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =====================================================================
-- 结束
-- =====================================================================

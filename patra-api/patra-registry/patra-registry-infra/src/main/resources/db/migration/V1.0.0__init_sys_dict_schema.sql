-- =====================================================================
-- Registry - 系统字典子域
-- 说明:
-- - 仅包含数据库对象和索引;无触发器,无物理外键
-- - 包含通用审计字段 (BaseDO)
-- - 字符集: utf8mb4; 排序规则: utf8mb4_unicode_ci; 引擎: InnoDB
-- - 表: sys_dict_type / sys_dict_item / sys_dict_item_alias
-- =====================================================================

/* ====================================================================
 * 表: sys_dict_type - 字典类型
 * 语义: 字典"类型"的元数据 (例如: http_method, endpoint_usage)
 * 要点:
 *  - type_code 采用小写下划线命名,跨环境稳定
 *  - allow_custom_items 控制业务是否可以扩展该类型下的条目
 *  - is_system 标识系统内置类型
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS sys_dict_type
(
    id                 BIGINT UNSIGNED NOT NULL COMMENT '主键;内部唯一标识符(雪花ID)',
    type_code          VARCHAR(64)     NOT NULL COMMENT '类型编码: 小写下划线格式,例如 http_method (跨环境稳定键)',
    type_name          VARCHAR(200)    NOT NULL COMMENT '类型显示名称 (人类可读)',
    description        VARCHAR(500)    NULL COMMENT '描述 (用途、边界、备注)',
    allow_custom_items TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否允许在此类型下自定义条目: 1=是, 0=否',
    is_system          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '系统内置: 1=系统, 0=业务定义',
    reserved_json      JSON            NULL COMMENT '额外元数据 (例如: UI颜色/图标/排序策略)',

    -- 审计与治理
    record_remarks     JSON            NULL COMMENT '变更备注: JSON数组,记录历史备注',
    created_at         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    created_by         BIGINT UNSIGNED NULL COMMENT '创建人ID (逻辑外键)',
    created_by_name    VARCHAR(100)    NULL COMMENT '创建人姓名/登录名快照',
    updated_at         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间 (UTC)',
    updated_by         BIGINT UNSIGNED NULL COMMENT '最后更新人ID (逻辑外键)',
    updated_by_name    VARCHAR(100)    NULL COMMENT '最后更新人姓名/登录名快照',
    version            BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本 (CAS)',
    ip_address         VARBINARY(16)   NULL COMMENT '请求者IP (二进制, IPv4/IPv6)',
    deleted_at         TIMESTAMP(6)    NULL DEFAULT NULL COMMENT '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)',

    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_type__code (type_code) COMMENT '确保 type_code 唯一',
    CONSTRAINT chk_sys_dict_type__code_format CHECK (REGEXP_LIKE(type_code, '^[a-z0-9_]{1,64}$'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='系统字典 - 类型';


/* ====================================================================
 * 表: sys_dict_item - 字典条目
 * 语义: 字典类型下的具体值 (例如: http_method 的 GET/POST)
 * 要点:
 *  - item_code 采用大写下划线格式作为稳定键
 *  - default_key 是生成列: 仅当 default AND enabled AND deleted_at IS NULL 时等于 type_id; 强制每类型一个默认值
 *  - enabled 控制在业务逻辑中是否可选
 *  - attributes_json 用于扩展性 (新属性无需DDL)
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS sys_dict_item
(
    id              BIGINT UNSIGNED NOT NULL COMMENT '主键;内部唯一标识符(雪花ID)',
    type_id         BIGINT UNSIGNED NOT NULL COMMENT '父类型ID (逻辑外键 -> sys_dict_type.id)',
    item_code       VARCHAR(64)     NOT NULL COMMENT '条目编码: 稳定键 (大写下划线格式), 例如 GET / PAGE_NUMBER',
    item_name       VARCHAR(200)    NOT NULL COMMENT '条目显示名称 (默认语言)',
    short_name      VARCHAR(64)     NULL COMMENT '简称/缩写名称 (紧凑UI)',
    description     VARCHAR(500)    NULL COMMENT '备注/说明 (语义、边界、兼容性)',
    display_order   INT UNSIGNED    NOT NULL DEFAULT 100 COMMENT '显示顺序 (数值越小越靠前)',
    is_default      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否默认值 (每类型最多一个; 见 default_key 唯一约束)',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用: 1=是, 0=禁用 (为0时排除在选择之外)',
    label_color     VARCHAR(32)     NULL COMMENT '标签颜色 (#AABBCC 或语义名称)',
    icon_name       VARCHAR(64)     NULL COMMENT '图标名称 (用于UI)',
    attributes_json JSON            NULL COMMENT '扩展属性 (别名/提示/兼容标志等)',

    -- 生成列: 仅当 default AND enabled AND not deleted 时等于 type_id; 否则为 NULL
    default_key     BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE
             WHEN (is_default = 1 AND enabled = 1 AND deleted_at IS NULL) THEN type_id
             ELSE NULL END) STORED COMMENT '生成列,用于唯一键以强制每类型一个默认值',

    -- 审计与治理
    record_remarks  JSON            NULL COMMENT '变更备注: JSON数组',
    created_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    created_by      BIGINT UNSIGNED NULL COMMENT '创建人ID',
    created_by_name VARCHAR(100)    NULL COMMENT '创建人姓名/登录名快照',
    updated_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间 (UTC)',
    updated_by      BIGINT UNSIGNED NULL COMMENT '最后更新人ID',
    updated_by_name VARCHAR(100)    NULL COMMENT '最后更新人姓名/登录名快照',
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本 (CAS)',
    ip_address      VARBINARY(16)   NULL COMMENT '请求者IP (二进制, IPv4/IPv6)',
    deleted_at      TIMESTAMP(6)    NULL DEFAULT NULL COMMENT '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)',

    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_item__type_code (type_id, item_code) COMMENT 'item_code 在同一类型内必须唯一',
    UNIQUE KEY uk_sys_dict_item__default_per_type (default_key) COMMENT '通过生成列确保每类型一个默认值',
    CONSTRAINT chk_sys_dict_item__code_format CHECK (REGEXP_LIKE(item_code, '^[A-Z0-9_]{1,64}$'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='系统字典 - 条目';


/* ====================================================================
 * 表: sys_dict_item_alias - 字典条目外部映射
 * 语义: 来自外部标准或遗留系统的条目别名/编码,用于解析与集成
 * 要点:
 *  - (source_standard, external_code) 必须全局唯一以避免冲突
 *  - 可选的 external_label 存储外部显示名称
 *  - 典型 source_standard 示例: iso_3166_1_alpha2、global (推荐小写)
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS sys_dict_item_alias
(
    id              BIGINT UNSIGNED NOT NULL COMMENT '主键;内部唯一标识符(雪花ID)',
    item_id         BIGINT UNSIGNED NOT NULL COMMENT '条目ID (逻辑外键 -> sys_dict_item.id)',
    source_standard VARCHAR(64)     NOT NULL COMMENT '来源标准标识符, 例如 iso_3166_1_alpha2 或 global (推荐小写下划线或短横线格式)',
    external_code   VARCHAR(128)    NOT NULL COMMENT '外部编码/值 (作为映射键)',
    external_label  VARCHAR(200)    NULL COMMENT '外部显示名称 (可选)',
    notes           VARCHAR(500)    NULL COMMENT '备注/映射说明 (差异、兼容性、来源链接等)',

    -- 审计与治理
    record_remarks  JSON            NULL COMMENT '变更备注: JSON数组',
    created_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    created_by      BIGINT UNSIGNED NULL COMMENT '创建人ID',
    created_by_name VARCHAR(100)    NULL COMMENT '创建人姓名/登录名快照',
    updated_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间 (UTC)',
    updated_by      BIGINT UNSIGNED NULL COMMENT '最后更新人ID',
    updated_by_name VARCHAR(100)    NULL COMMENT '最后更新人姓名/登录名快照',
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本 (CAS)',
    ip_address      VARBINARY(16)   NULL COMMENT '请求者IP (二进制, IPv4/IPv6)',

    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_alias__std_code (source_standard, external_code) COMMENT 'external_code 在同一 source_standard 内必须唯一',
    CONSTRAINT chk_dict_alias__std_format CHECK (REGEXP_LIKE(source_standard, '^[a-z0-9_\-]{1,64}$'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='系统字典 - 外部映射';

/* ====================================================================
 * 表: sys_reference_standard - 来源标准目录
 * 语义: 维护某字典类型下允许的来源标准,用于校验与治理
 * 要点:
 *  - 标准代码固定为大写下划线格式
 *  - dict_type_code 关联字典类型
 *  - is_canonical 标记该类型的规范标准（item_code 遵循的格式）
 *  - 每个字典类型只能有一个规范标准（通过生成列约束）
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS sys_reference_standard
(
    id              BIGINT UNSIGNED NOT NULL COMMENT '主键;内部唯一标识符(雪花ID)',
    dict_type_code  VARCHAR(64)     NOT NULL COMMENT '所属字典类型代码 (逻辑外键 -> sys_dict_type.type_code)',
    standard_code   VARCHAR(64)     NOT NULL COMMENT '标准代码(如 ISO_3166_1_ALPHA2 或 NAME_EN)',
    standard_name   VARCHAR(200)    NOT NULL COMMENT '标准名称',
    description     VARCHAR(500)    NULL COMMENT '描述',
    display_order   INT UNSIGNED    NOT NULL DEFAULT 100 COMMENT '显示顺序',
    is_canonical    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否为该类型的规范标准: 1=是, 0=否 (每类型最多一个)',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用: 1=启用, 0=禁用',

    -- 生成列: 仅当 is_canonical AND enabled 时等于 dict_type_code; 否则为 NULL
    canonical_key   VARCHAR(64) GENERATED ALWAYS AS
        (CASE
             WHEN (is_canonical = 1 AND enabled = 1) THEN dict_type_code
             ELSE NULL END) STORED COMMENT '生成列,用于唯一键以强制每类型一个规范标准',

    -- 审计与治理
    record_remarks  JSON            NULL COMMENT '变更备注: JSON数组',
    created_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    created_by      BIGINT UNSIGNED NULL COMMENT '创建人ID',
    created_by_name VARCHAR(100)    NULL COMMENT '创建人姓名/登录名快照',
    updated_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间 (UTC)',
    updated_by      BIGINT UNSIGNED NULL COMMENT '最后更新人ID',
    updated_by_name VARCHAR(100)    NULL COMMENT '最后更新人姓名/登录名快照',
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本 (CAS)',
    ip_address      VARBINARY(16)   NULL COMMENT '请求者IP (二进制, IPv4/IPv6)',

    PRIMARY KEY (id),
    UNIQUE KEY uk_ref_standard__type_code (dict_type_code, standard_code) COMMENT '同一字典类型下标准代码唯一',
    UNIQUE KEY uk_ref_standard__canonical_per_type (canonical_key) COMMENT '通过生成列确保每类型一个规范标准',
    CONSTRAINT chk_ref_standard__code_format CHECK (REGEXP_LIKE(standard_code, '^[A-Z0-9_]{1,64}$')),
    CONSTRAINT chk_ref_standard__type_format CHECK (REGEXP_LIKE(dict_type_code, '^[a-z0-9_]{1,64}$'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='系统参考标准';

-- =====================================================================
-- 结束
-- =====================================================================

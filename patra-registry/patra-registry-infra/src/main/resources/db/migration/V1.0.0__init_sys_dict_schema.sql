-- =====================================================================
-- Registry · 系统字典子域（System Dictionary）
-- 说明：
-- - 仅数据库对象与索引；无触发器、无物理外键
-- - 包含通用审计字段（BaseDO）
-- - 字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci；引擎：InnoDB
-- - 表：sys_dict_type / sys_dict_item / sys_dict_item_alias
-- =====================================================================

/* ====================================================================
 * 表：sys_dict_type —— 字典类型
 * 语义：定义一个字典"类型"的元信息（如 http_method / endpoint_usage 等）。
 * 关键点：
 *  - type_code 为小写蛇形，跨环境稳定；
 *  - allow_custom_items 控制是否允许业务在该类型下自定义扩展项；
 *  - is_system 标记系统内置；
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS sys_dict_type
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键；仅用于库内唯一标识',
    type_code          VARCHAR(64)     NOT NULL COMMENT '类型编码：小写蛇形，如 http_method（跨环境稳定键）',
    type_name          VARCHAR(200)    NOT NULL COMMENT '类型名称（人类可读显示名）',
    description        VARCHAR(500)    NULL COMMENT '类型说明（使用场景、边界等）',
    allow_custom_items TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否允许自定义扩展项（1=允许；0=不允许）',
    is_system          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否系统内置（1=内置；0=业务自定义）',
    reserved_json      JSON            NULL COMMENT '扩展元数据（例如 UI 颜色/图标/排序策略等）',

    -- 审计与治理
    record_remarks     JSON            NULL COMMENT '变更备注：JSON 数组，记录历史修改说明',
    created_at         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（UTC）',
    created_by         BIGINT UNSIGNED NULL COMMENT '创建人 ID（逻辑外键）',
    created_by_name    VARCHAR(100)    NULL COMMENT '创建人姓名/登录名快照',
    updated_at         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间（UTC）',
    updated_by         BIGINT UNSIGNED NULL COMMENT '最后更新人 ID（逻辑外键）',
    updated_by_name    VARCHAR(100)    NULL COMMENT '最后更新人姓名/登录名快照',
    version            BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（CAS）',
    ip_address         VARBINARY(16)   NULL COMMENT '请求方 IP（二进制，IPv4/IPv6）',
    deleted            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删，1=已删（读侧统一过滤）',

    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_type__code (type_code) COMMENT '确保类型编码唯一',
    CONSTRAINT chk_sys_dict_type__code_format CHECK (REGEXP_LIKE(type_code, '^[a-z0-9_]{1,64}$'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='系统字典-类型';


/* ====================================================================
 * 表：sys_dict_item —— 字典项目
 * 语义：隶属于某个字典类型的具体取值（例如 http_method 下的 GET/POST）。
 * 关键点：
 *  - item_code 为全大写+下划线，作为稳定键；
 *  - default_key 为生成列：仅当"默认且启用且未删"时等于 type_id；用于强约束"同类型仅一个默认项"；
 *  - enabled 控制是否参与业务选择；
 *  - attributes_json 用于扩展存储（无需 DDL 变更）。
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS sys_dict_item
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键；库内唯一标识',
    type_id         BIGINT UNSIGNED NOT NULL COMMENT '父类型 ID（逻辑外键 → sys_dict_type.id）',
    item_code       VARCHAR(64)     NOT NULL COMMENT '项目编码：稳定键（全大写+下划线），如 GET / PAGE_NUMBER',
    item_name       VARCHAR(200)    NOT NULL COMMENT '项目名称（默认语言显示名）',
    short_name      VARCHAR(64)     NULL COMMENT '短名/缩写（UI 紧凑场景）',
    description     VARCHAR(500)    NULL COMMENT '说明/注释（语义/边界/兼容性）',
    display_order   INT UNSIGNED    NOT NULL DEFAULT 100 COMMENT '显示顺序（越小越靠前）',
    is_default      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否默认取值（同类型最多一条，见 default_key 唯一约束）',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用：1=启用；0=禁用（禁用后不参与业务选择）',
    label_color     VARCHAR(32)     NULL COMMENT '标签颜色（#AABBCC 或语义色名）',
    icon_name       VARCHAR(64)     NULL COMMENT '图标名（UI 展示用）',
    attributes_json JSON            NULL COMMENT '扩展属性（业务自定义键值，如别名/提示/兼容标志等）',

    -- 生成列：仅当默认且启用且未删时等于 type_id；否则为 NULL
    default_key     BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE
             WHEN (is_default = 1 AND enabled = 1 AND deleted = 0) THEN type_id
             ELSE NULL END) STORED COMMENT '生成列：用于唯一约束确保"同类型仅一个默认项"',

    -- 审计与治理
    record_remarks  JSON            NULL COMMENT '变更备注：JSON 数组',
    created_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（UTC）',
    created_by      BIGINT UNSIGNED NULL COMMENT '创建人 ID',
    created_by_name VARCHAR(100)    NULL COMMENT '创建人姓名/登录名快照',
    updated_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间（UTC）',
    updated_by      BIGINT UNSIGNED NULL COMMENT '最后更新人 ID',
    updated_by_name VARCHAR(100)    NULL COMMENT '最后更新人姓名/登录名快照',
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（CAS）',
    ip_address      VARBINARY(16)   NULL COMMENT '请求方 IP（二进制，IPv4/IPv6）',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删，1=已删',

    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_item__type_code (type_id, item_code) COMMENT '同一类型下 item_code 必须唯一',
    UNIQUE KEY uk_sys_dict_item__default_per_type (default_key) COMMENT '通过生成列确保"同类型仅一个默认项"',
    CONSTRAINT chk_sys_dict_item__code_format CHECK (REGEXP_LIKE(item_code, '^[A-Z0-9_]{1,64}$'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='系统字典-项目';


/* ====================================================================
 * 表：sys_dict_item_alias —— 字典项目的外部映射
 * 语义：为某个项目提供来自外部系统/供应商/遗留系统的"别名/编码"，便于对接。
 * 关键点：
 *  - (source_system, external_code) 全局唯一，避免重复映射冲突；
 *  - 可选 external_label 存储外部显示名；
 *  - 常见 source_system 如：pubmed/crossref/legacy_v1 等（全小写建议）。
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS sys_dict_item_alias
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键；库内唯一标识',
    item_id         BIGINT UNSIGNED NOT NULL COMMENT '项目 ID（逻辑外键 → sys_dict_item.id）',
    source_system   VARCHAR(64)     NOT NULL COMMENT '来源系统标识：如 pubmed / crossref / legacy_v1（建议小写蛇形/中划线）',
    external_code   VARCHAR(128)    NOT NULL COMMENT '外部代码/取值（作为映射键）',
    external_label  VARCHAR(200)    NULL COMMENT '外部名称/显示名（可空）',
    notes           VARCHAR(500)    NULL COMMENT '备注/映射说明（差异、兼容性、来源链接等）',

    -- 审计与治理
    record_remarks  JSON            NULL COMMENT '变更备注：JSON 数组',
    created_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（UTC）',
    created_by      BIGINT UNSIGNED NULL COMMENT '创建人 ID',
    created_by_name VARCHAR(100)    NULL COMMENT '创建人姓名/登录名快照',
    updated_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间（UTC）',
    updated_by      BIGINT UNSIGNED NULL COMMENT '最后更新人 ID',
    updated_by_name VARCHAR(100)    NULL COMMENT '最后更新人姓名/登录名快照',
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（CAS）',
    ip_address      VARBINARY(16)   NULL COMMENT '请求方 IP（二进制，IPv4/IPv6）',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删，1=已删',

    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_alias__src_code (source_system, external_code) COMMENT '同一外部系统下 external_code 必须唯一',
    CONSTRAINT chk_dict_alias__src_format CHECK (REGEXP_LIKE(source_system, '^[a-z0-9_\-]{1,64}$'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='系统字典-外部映射';

-- =====================================================================
-- 结束
-- =====================================================================

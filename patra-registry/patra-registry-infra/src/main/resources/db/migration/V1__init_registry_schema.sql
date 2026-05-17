-- =====================================================================
-- Patra Registry — PostgreSQL 17 baseline schema
-- 合并自原 V1.0.0 / V1.0.1 / V1.0.2（MySQL）；已移除 6 条物理 FK、AUTO_INCREMENT、
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


/* ====================================================================
 * 表: reg_provenance - Provenance 注册表 (数据源目录)
 * 领域: Registry - Provenance 配置
 * 语义: 外部数据源的基本信息目录 (例如: PubMed, Crossref)，
 *       作为所有 reg_prov_* 配置表引用的根实体
 * 要点:
 *  - 稳定键: provenance_code (唯一，跨环境稳定)
 *  - 默认值: base_url_default / timezone_default / docs_url
 *  - 生命周期: lifecycle_status_code (字典 lifecycle_status); is_active 是读侧过滤开关
 * 关系: 被所有 reg_prov_* 表通过 provenance_id 引用
 * ==================================================================== */
CREATE TABLE reg_provenance
(
    id                    BIGINT         NOT NULL,
    provenance_code       VARCHAR(64)    NOT NULL,
    provenance_name       VARCHAR(128)   NOT NULL,
    base_url_default      VARCHAR(512)   NULL,
    timezone_default      VARCHAR(64)    NOT NULL DEFAULT 'UTC',
    docs_url              VARCHAR(512)   NULL,
    is_active             BOOLEAN        NOT NULL DEFAULT true,
    lifecycle_status_code VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE',

    -- BaseDO (通用审计字段)
    record_remarks        jsonb          NULL,
    created_at            timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            BIGINT         NULL,
    created_by_name       VARCHAR(100)   NULL,
    updated_at            timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            BIGINT         NULL,
    updated_by_name       VARCHAR(100)   NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    ip_address            bytea          NULL,
    deleted_at            timestamptz(6) NULL     DEFAULT NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_reg_provenance_code UNIQUE (provenance_code)
);

COMMENT ON TABLE reg_provenance IS 'Provenance注册表: 记录外部数据源作为所有 reg_prov_* 配置引用的根实体';
COMMENT ON COLUMN reg_provenance.id IS '主键；唯一数据源标识符；被 reg_prov_* 通过 provenance_id 引用';
COMMENT ON COLUMN reg_provenance.provenance_code IS '数据源编码: 全局唯一，稳定 (例如 pubmed/crossref)；用于查找和约束';
COMMENT ON COLUMN reg_provenance.provenance_name IS '数据源显示名称 (例如 PubMed / Crossref)';
COMMENT ON COLUMN reg_provenance.base_url_default IS '默认基础URL: 未被HTTP策略覆盖时用于与端点路径拼接';
COMMENT ON COLUMN reg_provenance.timezone_default IS '默认时区 (IANA TZ，例如 UTC/Asia/Shanghai): 窗口计算/显示的默认时区';
COMMENT ON COLUMN reg_provenance.docs_url IS '官方文档/参考URL: 帮助故障排查和API验证';
COMMENT ON COLUMN reg_provenance.is_active IS '数据源是否活动: true=活动, false=非活动 (读侧可按此过滤)';
COMMENT ON COLUMN reg_provenance.lifecycle_status_code IS '字典编码(type=lifecycle_status): 读侧仅使用ACTIVE/有效状态';
COMMENT ON COLUMN reg_provenance.record_remarks IS '审计备注: JSON数组';
COMMENT ON COLUMN reg_provenance.created_at IS '创建时间 (UTC)';
COMMENT ON COLUMN reg_provenance.updated_at IS '最后更新时间 (UTC)';
COMMENT ON COLUMN reg_provenance.version IS '乐观锁版本';
COMMENT ON COLUMN reg_provenance.ip_address IS '请求者IP (二进制, IPv4/IPv6)';
COMMENT ON COLUMN reg_provenance.deleted_at IS '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)';

CREATE TRIGGER trg_reg_provenance_updated_at
    BEFORE UPDATE ON reg_provenance
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


/* ====================================================================
 * 表: reg_prov_window_offset_cfg - 窗口与偏移策略
 * 领域: Registry - Provenance 配置
 * 语义: 配置任务如何分割时间窗口和推进增量偏移 (DATE/ID/COMPOSITE)
 * 注: provenance_id 为逻辑外键（不定义物理 FOREIGN KEY 约束）
 * ==================================================================== */
CREATE TABLE reg_prov_window_offset_cfg
(
    id                      BIGINT         NOT NULL,
    -- 逻辑外键 → reg_provenance.id
    provenance_id           BIGINT         NOT NULL,
    operation_type          VARCHAR(32)    NOT NULL DEFAULT 'ALL',

    effective_from          timestamptz(6) NOT NULL,
    effective_to            timestamptz(6) NULL,

    -- 窗口定义
    window_mode_code        VARCHAR(16)    NOT NULL,
    window_size_value       INTEGER        NOT NULL DEFAULT 1,
    window_size_unit_code   VARCHAR(16)    NOT NULL,
    calendar_align_to       VARCHAR(16)    NULL,
    lookback_value          INTEGER        NULL,
    lookback_unit_code      VARCHAR(16)    NULL,
    overlap_value           INTEGER        NULL,
    overlap_unit_code       VARCHAR(16)    NULL,
    watermark_lag_seconds   INTEGER        NULL,

    -- 偏移定义
    offset_type_code        VARCHAR(16)    NOT NULL,
    offset_field_key        VARCHAR(64)    NULL,
    offset_date_format      VARCHAR(64)    NULL,
    window_date_field_key   VARCHAR(64)    NULL,
    max_ids_per_window      INTEGER        NULL,
    max_window_span_seconds INTEGER        NULL,

    lifecycle_status_code   VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE',

    PRIMARY KEY (id),
    CONSTRAINT uk_reg_prov_window_offset_cfg__dim_from UNIQUE (provenance_id, operation_type, effective_from)
);

COMMENT ON TABLE reg_prov_window_offset_cfg IS '窗口与偏移配置: 如何分割窗口和推进偏移 (DATE/ID/COMPOSITE)';
COMMENT ON COLUMN reg_prov_window_offset_cfg.provenance_id IS '逻辑外键 → reg_provenance.id';
COMMENT ON COLUMN reg_prov_window_offset_cfg.operation_type IS '操作类型 (ALL/HARVEST/UPDATE/BACKFILL)';
COMMENT ON COLUMN reg_prov_window_offset_cfg.effective_from IS '生效开始时间 (包含); 应用层确保不重叠';
COMMENT ON COLUMN reg_prov_window_offset_cfg.effective_to IS '生效结束时间 (不包含); NULL表示开放式';
COMMENT ON COLUMN reg_prov_window_offset_cfg.lifecycle_status_code IS '字典编码(type=lifecycle_status): 生命周期';


/* ====================================================================
 * 表: reg_prov_pagination_cfg - 分页与游标
 * 注: provenance_id 为逻辑外键（不定义物理 FOREIGN KEY 约束）
 * ==================================================================== */
CREATE TABLE reg_prov_pagination_cfg
(
    id                      BIGINT         NOT NULL,
    -- 逻辑外键 → reg_provenance.id
    provenance_id           BIGINT         NOT NULL,
    operation_type          VARCHAR(32)    NOT NULL DEFAULT 'ALL',

    effective_from          timestamptz(6) NOT NULL,
    effective_to            timestamptz(6) NULL,

    pagination_mode_code    VARCHAR(32)    NOT NULL,
    page_size_value         INTEGER        NULL,
    max_pages_per_execution INTEGER        NULL,
    sort_field_param_name   VARCHAR(128)   NULL,
    sorting_direction       BOOLEAN        NOT NULL DEFAULT true,

    lifecycle_status_code   VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE',

    PRIMARY KEY (id),
    CONSTRAINT uk_reg_prov_pagination_cfg__dim_from UNIQUE (provenance_id, operation_type, effective_from)
);

COMMENT ON TABLE reg_prov_pagination_cfg IS '分页与游标配置: 参数和响应提取; 支持 SOURCE/TASK 范围';
COMMENT ON COLUMN reg_prov_pagination_cfg.provenance_id IS '逻辑外键 → reg_provenance.id';
COMMENT ON COLUMN reg_prov_pagination_cfg.operation_type IS '操作类型 (ALL/HARVEST/UPDATE/BACKFILL)';
COMMENT ON COLUMN reg_prov_pagination_cfg.sorting_direction IS '排序方向: false=DESC, true=ASC';
COMMENT ON COLUMN reg_prov_pagination_cfg.lifecycle_status_code IS '字典编码(type=lifecycle_status): 生命周期';


/* ====================================================================
 * 表: reg_prov_http_cfg - HTTP 策略
 * 注: provenance_id 为逻辑外键（不定义物理 FOREIGN KEY 约束）
 * ==================================================================== */
CREATE TABLE reg_prov_http_cfg
(
    id                      BIGINT         NOT NULL,
    -- 逻辑外键 → reg_provenance.id
    provenance_id           BIGINT         NOT NULL,
    operation_type          VARCHAR(32)    NOT NULL DEFAULT 'ALL',

    effective_from          timestamptz(6) NOT NULL,
    effective_to            timestamptz(6) NULL,

    default_headers_json    jsonb          NULL,
    timeout_connect_millis  INTEGER        NULL,
    timeout_read_millis     INTEGER        NULL,
    timeout_total_millis    INTEGER        NULL,
    tls_verify_enabled      BOOLEAN        NOT NULL DEFAULT true,
    proxy_url_value         VARCHAR(512)   NULL,
    retry_after_policy_code VARCHAR(32)    NOT NULL,
    retry_after_cap_millis  INTEGER        NULL,
    idempotency_header_name VARCHAR(64)    NULL,
    idempotency_ttl_seconds INTEGER        NULL,

    lifecycle_status_code   VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE',

    PRIMARY KEY (id),
    CONSTRAINT uk_reg_prov_http_cfg__dim_from UNIQUE (provenance_id, operation_type, effective_from)
);

COMMENT ON TABLE reg_prov_http_cfg IS 'HTTP 策略配置: 基础 URL/请求头/超时/代理/Retry-After/幂等性; 支持 SOURCE/TASK 范围';
COMMENT ON COLUMN reg_prov_http_cfg.provenance_id IS '逻辑外键 → reg_provenance.id';
COMMENT ON COLUMN reg_prov_http_cfg.tls_verify_enabled IS '验证 TLS 证书: true=开启, false=关闭 (仅测试)';
COMMENT ON COLUMN reg_prov_http_cfg.lifecycle_status_code IS '字典编码(type=lifecycle_status): 生命周期';


/* ====================================================================
 * 表: reg_prov_batching_cfg - 批处理与请求塑形
 * 注: provenance_id 为逻辑外键（不定义物理 FOREIGN KEY 约束）
 * ==================================================================== */
CREATE TABLE reg_prov_batching_cfg
(
    id                       BIGINT         NOT NULL,
    -- 逻辑外键 → reg_provenance.id
    provenance_id            BIGINT         NOT NULL,
    operation_type           VARCHAR(32)    NOT NULL DEFAULT 'ALL',

    effective_from           timestamptz(6) NOT NULL,
    effective_to             timestamptz(6) NULL,

    detail_fetch_batch_size  INTEGER        NULL,
    ids_param_name           VARCHAR(64)    NULL,
    ids_join_delimiter       VARCHAR(8)     NULL DEFAULT ',',
    max_ids_per_request      INTEGER        NULL,

    lifecycle_status_code    VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE',

    PRIMARY KEY (id),
    CONSTRAINT uk_reg_prov_batching_cfg__dim_from UNIQUE (provenance_id, operation_type, effective_from)
);

COMMENT ON TABLE reg_prov_batching_cfg IS '批处理与塑形配置: 详情批处理、ID 连接、并发/背压; 支持 SOURCE/TASK 范围';
COMMENT ON COLUMN reg_prov_batching_cfg.provenance_id IS '逻辑外键 → reg_provenance.id';
COMMENT ON COLUMN reg_prov_batching_cfg.lifecycle_status_code IS '字典编码(type=lifecycle_status): 生命周期';


/* ====================================================================
 * 表: reg_prov_retry_cfg - 重试与退避
 * 注: provenance_id 为逻辑外键（不定义物理 FOREIGN KEY 约束）
 * ==================================================================== */
CREATE TABLE reg_prov_retry_cfg
(
    id                       BIGINT         NOT NULL,
    -- 逻辑外键 → reg_provenance.id
    provenance_id            BIGINT         NOT NULL,
    operation_type           VARCHAR(32)    NOT NULL DEFAULT 'ALL',

    effective_from           timestamptz(6) NOT NULL,
    effective_to             timestamptz(6) NULL,

    max_retry_times          INTEGER        NULL,
    backoff_policy_type_code VARCHAR(32)    NOT NULL,
    initial_delay_millis     INTEGER        NULL,
    max_delay_millis         INTEGER        NULL,
    exp_multiplier_value     DOUBLE PRECISION NULL,
    jitter_factor_ratio      DOUBLE PRECISION NULL,
    retry_http_status_json   jsonb          NULL,
    giveup_http_status_json  jsonb          NULL,
    retry_on_network_error   BOOLEAN        NOT NULL DEFAULT true,
    circuit_break_threshold  INTEGER        NULL,
    circuit_cooldown_millis  INTEGER        NULL,

    lifecycle_status_code    VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE',

    PRIMARY KEY (id),
    CONSTRAINT uk_reg_prov_retry_cfg__dim_from UNIQUE (provenance_id, operation_type, effective_from)
);

COMMENT ON TABLE reg_prov_retry_cfg IS '重试与退避配置: 次数、退避/抖动、网络策略和熔断器设置; 支持 SOURCE/TASK 范围';
COMMENT ON COLUMN reg_prov_retry_cfg.provenance_id IS '逻辑外键 → reg_provenance.id';
COMMENT ON COLUMN reg_prov_retry_cfg.retry_on_network_error IS '网络错误时重试: true=是, false=否';
COMMENT ON COLUMN reg_prov_retry_cfg.lifecycle_status_code IS '字典编码(type=lifecycle_status): 生命周期';


/* ====================================================================
 * 表: reg_prov_rate_limit_cfg - 限流与并发
 * 注: provenance_id 为逻辑外键（不定义物理 FOREIGN KEY 约束）
 * ==================================================================== */
CREATE TABLE reg_prov_rate_limit_cfg
(
    id                         BIGINT         NOT NULL,
    -- 逻辑外键 → reg_provenance.id
    provenance_id              BIGINT         NOT NULL,
    operation_type             VARCHAR(32)    NOT NULL DEFAULT 'ALL',

    effective_from             timestamptz(6) NOT NULL,
    effective_to               timestamptz(6) NULL,

    max_concurrent_requests    INTEGER        NULL,
    per_credential_qps_limit   INTEGER        NULL,
    lifecycle_status_code      VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE',

    PRIMARY KEY (id),
    CONSTRAINT uk_reg_prov_rate_limit_cfg__dim_from UNIQUE (provenance_id, operation_type, effective_from)
);

COMMENT ON TABLE reg_prov_rate_limit_cfg IS '限流与并发配置: QPS/突发/并发数/粒度; 可适应服务器限流头';
COMMENT ON COLUMN reg_prov_rate_limit_cfg.provenance_id IS '逻辑外键 → reg_provenance.id';
COMMENT ON COLUMN reg_prov_rate_limit_cfg.lifecycle_status_code IS '字典编码(type=lifecycle_status): 生命周期';


/* ====================================================================
 * 1) 全局统一字段字典 (数据源无关)
 *    - 定义内部字段的统一语义; 例如 publish_date / ti / ab / tiab
 *    - 数据源无关; 仅描述字段数据类型/基数/可暴露性等
 * ==================================================================== */
CREATE TABLE reg_expr_field_dict
(
    id               BIGINT       NOT NULL,
    field_key        VARCHAR(64)  NOT NULL,
    display_name     VARCHAR(128) NULL,
    description      VARCHAR(255) NULL,

    data_type_code   VARCHAR(32)  NOT NULL,
    cardinality_code VARCHAR(16)  NOT NULL DEFAULT 'SINGLE',
    exposable        BOOLEAN      NOT NULL DEFAULT true,
    is_date          BOOLEAN      NOT NULL DEFAULT false,

    PRIMARY KEY (id),
    CONSTRAINT uk_expr_field_key UNIQUE (field_key)
);

COMMENT ON TABLE reg_expr_field_dict IS '(Registry - Expr) 统一内部字段字典 (数据源无关; 字段语义的单一事实来源)';
COMMENT ON COLUMN reg_expr_field_dict.id IS '主键 (雪花ID/序列); 仅内部标识符; 迁移不依赖此值';
COMMENT ON COLUMN reg_expr_field_dict.field_key IS '统一内部字段键: 小写下划线或约定缩写，例如 publish_date/ti/ab/tiab; 全局唯一，配置/GitOps 稳定';
COMMENT ON COLUMN reg_expr_field_dict.data_type_code IS '数据类型编码 (字典 reg_data_type): DATE/DATETIME/NUMBER/TEXT/KEYWORD/BOOLEAN/TOKEN';
COMMENT ON COLUMN reg_expr_field_dict.cardinality_code IS '基数编码 (字典 reg_cardinality): SINGLE/MULTI; 是否允许多值';
COMMENT ON COLUMN reg_expr_field_dict.exposable IS '是否允许全局暴露/使用: true=可暴露, false=隐藏';
COMMENT ON COLUMN reg_expr_field_dict.is_date IS '冗余标志: true=类日期 (帮助UI/DateLens)';


/* ====================================================================
 * 2) 数据源相关: API参数名称映射 (std_key -> provider param)
 *    - 将统一语义键 (std_key) 映射到提供商HTTP参数名
 *    - 维度唯一性 + 时间切片: [from,to)
 * ==================================================================== */
CREATE TABLE reg_prov_api_param_map
(
    id                   BIGINT         NOT NULL,
    provenance_id        BIGINT         NOT NULL,

    operation_type       VARCHAR(32)    NOT NULL DEFAULT 'ALL',
    lifecycle_status_code VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',

    endpoint_name        VARCHAR(64)    NULL,
    std_key              VARCHAR(64)    NOT NULL,
    provider_param_name  VARCHAR(64)    NOT NULL,
    transform_code       VARCHAR(64)    NULL,
    notes                jsonb          NULL,

    effective_from       timestamptz(6) NOT NULL,
    effective_to         timestamptz(6) NULL,
    CONSTRAINT ck_param_map_range CHECK (effective_to IS NULL OR effective_to > effective_from),

    PRIMARY KEY (id),
    CONSTRAINT uk_param_map__dim_from UNIQUE
        (provenance_id, operation_type, endpoint_name, std_key, effective_from)
);

COMMENT ON TABLE reg_prov_api_param_map IS '(Registry - Expr) API参数映射: std_key -> provider parameter (键名级别; 时间维度)';
COMMENT ON COLUMN reg_prov_api_param_map.provenance_id IS '数据源ID (逻辑外键 -> reg_provenance.id) 区分提供商';
COMMENT ON COLUMN reg_prov_api_param_map.effective_from IS '生效开始时间 (包含); UTC; 时间切片的起始';
COMMENT ON COLUMN reg_prov_api_param_map.effective_to IS '生效结束时间 (不包含); UTC; NULL表示"仍然有效"';


/* ====================================================================
 * 3) 数据源相关: 字段能力 (允许的操作/约束)
 *    - 声明允许的表达式操作 (ops) 和每个操作的约束
 *    - 维度唯一性 + 时间切片: [from,to)
 * ==================================================================== */
CREATE TABLE reg_prov_expr_capability
(
    id                          BIGINT         NOT NULL,
    provenance_id               BIGINT         NOT NULL,

    operation_type              VARCHAR(32)    NOT NULL DEFAULT 'ALL',
    lifecycle_status_code       VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE',

    field_key                   VARCHAR(64)    NOT NULL,

    effective_from              timestamptz(6) NOT NULL,
    effective_to                timestamptz(6) NULL,
    CONSTRAINT ck_cap_range CHECK (effective_to IS NULL OR effective_to > effective_from),

    ops                         jsonb          NOT NULL,
    negatable_ops               jsonb          NULL,
    supports_not                BOOLEAN        NOT NULL DEFAULT true,

    term_matches                jsonb          NULL,
    term_case_sensitive_allowed BOOLEAN        NOT NULL DEFAULT false,
    term_allow_blank            BOOLEAN        NOT NULL DEFAULT false,
    term_min_len                INTEGER        NOT NULL DEFAULT 0,
    term_max_len                INTEGER        NOT NULL DEFAULT 0,
    term_pattern                VARCHAR(255)   NULL,

    in_max_size                 INTEGER        NOT NULL DEFAULT 0,
    in_case_sensitive_allowed   BOOLEAN        NOT NULL DEFAULT false,

    range_kind_code             VARCHAR(16)    NOT NULL DEFAULT 'NONE',
    range_allow_open_start      BOOLEAN        NOT NULL DEFAULT true,
    range_allow_open_end        BOOLEAN        NOT NULL DEFAULT true,
    range_allow_closed_at_infty BOOLEAN        NOT NULL DEFAULT false,

    date_min                    DATE           NULL,
    date_max                    DATE           NULL,
    datetime_min                timestamptz(6) NULL,
    datetime_max                timestamptz(6) NULL,
    number_min                  NUMERIC(38, 12) NULL,
    number_max                  NUMERIC(38, 12) NULL,

    exists_supported            BOOLEAN        NOT NULL DEFAULT false,
    token_kinds                 jsonb          NULL,
    token_value_pattern         VARCHAR(255)   NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_cap__dim_from UNIQUE
        (provenance_id, operation_type, field_key, effective_from)
);

COMMENT ON TABLE reg_prov_expr_capability IS '(Registry - Expr) 字段能力 (数据源相关): 用于验证/渲染的允许操作和约束';
COMMENT ON COLUMN reg_prov_expr_capability.provenance_id IS '数据源ID (逻辑外键 -> reg_provenance.id)';
COMMENT ON COLUMN reg_prov_expr_capability.field_key IS '统一内部字段键 (逻辑外键 -> reg_expr_field_dict.field_key)';
COMMENT ON COLUMN reg_prov_expr_capability.ops IS '允许的操作集合 (大写编码数组，例如 ["TERM","IN","RANGE","EXISTS","TOKEN"])';
COMMENT ON COLUMN reg_prov_expr_capability.supports_not IS 'NOT是否全局允许: true=允许, false=禁止';
COMMENT ON COLUMN reg_prov_expr_capability.number_min IS '最小 NUMBER 边界 (高精度)';
COMMENT ON COLUMN reg_prov_expr_capability.number_max IS '最大 NUMBER 边界 (高精度)';


/* ====================================================================
 * 4) 数据源相关: 渲染规则 (Expr.Atom -> query fragment or params)
 *    - 将表达式原子 (字段 + 操作 + 匹配/否定 + 值类型) 渲染为查询片段或参数
 *    - 维度唯一性 + 时间切片: [from,to); 规范化生成列消除NULL歧义
 * ==================================================================== */
CREATE TABLE reg_prov_expr_render_rule
(
    id              BIGINT         NOT NULL,
    provenance_id   BIGINT         NOT NULL,

    operation_type        VARCHAR(32)  NOT NULL DEFAULT 'ALL',
    lifecycle_status_code VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',

    field_key       VARCHAR(64)    NOT NULL,
    op_code         VARCHAR(16)    NOT NULL,
    match_type_code VARCHAR(16)    NULL,
    negated         BOOLEAN        NULL,
    value_type_code VARCHAR(16)    NULL,
    emit_type_code  VARCHAR(8)     NOT NULL DEFAULT 'QUERY',

    -- 生成列: 规范化 NULL 为 'ANY'，用于消除唯一键中的 NULL 歧义
    match_type_key  VARCHAR(16) GENERATED ALWAYS AS (COALESCE(match_type_code, 'ANY')) STORED,
    negated_key     CHAR(3)     GENERATED ALWAYS AS (COALESCE(CASE WHEN negated THEN 'T' ELSE 'F' END, 'ANY')) STORED,
    value_type_key  VARCHAR(16) GENERATED ALWAYS AS (COALESCE(value_type_code, 'ANY')) STORED,

    effective_from  timestamptz(6) NOT NULL,
    effective_to    timestamptz(6) NULL,
    CONSTRAINT ck_render_range CHECK (effective_to IS NULL OR effective_to > effective_from),

    template        TEXT           NULL,
    item_template   TEXT           NULL,
    joiner          VARCHAR(32)    NULL,
    wrap_group      BOOLEAN        NOT NULL DEFAULT false,

    params          jsonb          NULL,
    fn_code         VARCHAR(64)    NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_render__dim_from UNIQUE
        (provenance_id, operation_type, field_key, op_code, match_type_key, negated_key,
         value_type_key, emit_type_code, effective_from)
);

COMMENT ON TABLE reg_prov_expr_render_rule IS '(Registry - Expr) 渲染规则 (数据源相关): Expr.Atom -> query fragment or params; 与参数命名解耦; 时间维度';
COMMENT ON COLUMN reg_prov_expr_render_rule.provenance_id IS '数据源ID (逻辑外键 -> reg_provenance.id)';
COMMENT ON COLUMN reg_prov_expr_render_rule.field_key IS '统一内部字段键 (逻辑外键 -> reg_expr_field_dict.field_key)';
COMMENT ON COLUMN reg_prov_expr_render_rule.match_type_code IS '匹配类型编码 (字典 reg_match_type; 仅TERM): PHRASE/EXACT/ANY; NULL=无关';
COMMENT ON COLUMN reg_prov_expr_render_rule.negated IS '否定标志: true=NOT, false=非NOT; NULL=无关 (参与规范化键)';
COMMENT ON COLUMN reg_prov_expr_render_rule.value_type_code IS '值类型编码 (用于RANGE等): STRING/DATE/DATETIME/NUMBER; NULL=无关';
COMMENT ON COLUMN reg_prov_expr_render_rule.match_type_key IS '规范化: NULL -> ANY 用于 match_type_code';
COMMENT ON COLUMN reg_prov_expr_render_rule.negated_key IS '规范化: NULL -> ANY 用于 negated (T/F/ANY)';
COMMENT ON COLUMN reg_prov_expr_render_rule.value_type_key IS '规范化: NULL -> ANY 用于 value_type_code';
COMMENT ON COLUMN reg_prov_expr_render_rule.wrap_group IS '当 emit=QUERY 且 op=IN 时: 是否用括号包裹整个组';
COMMENT ON COLUMN reg_prov_expr_render_rule.params IS '当 emit=PARAMS 时: 标准键/模板变量的 JSON';

-- =====================================================================
-- 结束
-- =====================================================================

-- =====================================================================
-- 注册中心:系统唯一真实数据来源(Registry)—— reg_*
-- - 统一审计字段：record_remarks / created_at/created_by/created_by_name / updated_at / updated_by / updated_by_name / version / ip_address / deleted。
-- - 不创建物理外键
-- - MySQL 8.0 · InnoDB · utf8mb4_0900_ai_ci
-- =====================================================================


-- =====================================================================
-- Registry · 系统字典子域
-- =====================================================================
/* ====================================================================
 * 表：sys_dict_type —— 字典类型
 * 语义：定义一个字典“类型”的元信息（如 http_method / endpoint_usage 等）。
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
    KEY idx_sys_dict_type__deleted_is_system (deleted, is_system),
    CONSTRAINT chk_sys_dict_type__code_format CHECK (REGEXP_LIKE(type_code, '^[a-z0-9_]{1,64}$'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='系统字典-类型';


/* ====================================================================
 * 表：sys_dict_item —— 字典项目
 * 语义：隶属于某个字典类型的具体取值（例如 http_method 下的 GET/POST）。
 * 关键点：
 *  - item_code 为全大写+下划线，作为稳定键；
 *  - default_key 为生成列：仅当“默认且启用且未删”时等于 type_id；用于强约束“同类型仅一个默认项”；
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
             ELSE NULL END) STORED COMMENT '生成列：用于唯一约束确保“同类型仅一个默认项”',

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
    UNIQUE KEY uk_sys_dict_item__default_per_type (default_key) COMMENT '通过生成列确保“同类型仅一个默认项”',
    KEY idx_sys_dict_item__type_enabled (type_id, enabled, deleted, display_order) COMMENT '常用读索引：按类型取启用项并排序',
    CONSTRAINT chk_sys_dict_item__code_format CHECK (REGEXP_LIKE(item_code, '^[A-Z0-9_]{1,64}$'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='系统字典-项目';


/* ====================================================================
 * 表：sys_dict_item_alias —— 字典项目的外部映射
 * 语义：为某个项目提供来自外部系统/供应商/遗留系统的“别名/编码”，便于对接。
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
    KEY idx_dict_alias__item (item_id, source_system) COMMENT '按 item 查找对应外部映射',
    CONSTRAINT chk_dict_alias__src_format CHECK (REGEXP_LIKE(source_system, '^[a-z0-9_\-]{1,64}$'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='系统字典-外部映射';

-- =====================================================================
-- 结尾说明：以上仅包含“表创建语句”。如需视图 v_sys_dict_item_enabled、种子 INSERT、健康检查 SQL，
-- 我可以基于这份文件再生成一个 init/seed 脚本，保证一键拉起与巡检。
-- =====================================================================


/* =====================================================================
 * 医学文献采集配置（MySQL 8.0）
 * 说明：
 * - 仅数据库对象与索引；无触发器、无 CHECK 约束；包含通用审计字段（BaseDO）；校验交由应用层处理。
 * - 字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci；引擎：InnoDB。
 * - 命名规范：
 *   - 主数据表：reg_provenance（数据源/来源登记）。
 *   - 所有配置/定义表均使用前缀 reg_prov_，并用语义化缩写：
 *     reg_prov_window_offset_cfg        —— 时间窗口与增量指针配置
 *     reg_prov_pagination_cfg           —— 分页与游标配置
 *     reg_prov_http_cfg                 —— HTTP 策略配置
 *     reg_prov_endpoint_def             —— 端点定义
 *     reg_prov_batching_cfg             —— 批量抓取与请求成型配置
 *     reg_prov_retry_cfg                —— 重试与退避配置
 *     reg_prov_rate_limit_cfg           —— 限流与并发配置
 *     reg_prov_credential               —— 鉴权/密钥配置（可选绑定端点）
 * - 外键：所有配置表以 provenance_id 关联 reg_provenance(id)；凭证表可选关联端点 reg_prov_endpoint_def(id)。
 * - 生效区间：effective_from/effective_to 采用 [start, end) 语义；不重叠由应用层保证。
 * - 作用域：scope = SOURCE|TASK；当 scope='SOURCE' 时 task_type 置 NULL；当 scope='TASK' 时 task_type 必须设置（由应用层保证）。
 * - 生成列：task_type_key = IFNULL(task_type, 'ALL')，便于维度唯一键与检索。
 * ===================================================================== */

/* ===========================================================
 * 基础主数据：数据来源登记（Provenance Registry）
 * =========================================================== */
DROP TABLE IF EXISTS `reg_provenance`;
CREATE TABLE `reg_provenance`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键：数据来源唯一标识；被所有 reg_prov_* 配置表通过 provenance_id 引用',
    `provenance_code`  VARCHAR(64)     NOT NULL COMMENT '来源编码：全局唯一、稳定（如 pubmed / crossref），用于程序内查找与约束',
    `provenance_name`  VARCHAR(128)    NOT NULL COMMENT '来源名称：人类可读名称（如 PubMed / Crossref）',
    `base_url_default` VARCHAR(512)    NULL COMMENT '默认基础URL：当未在 HTTP 策略中覆盖时，用于端点 path 的拼接',
    `timezone_default` VARCHAR(64)     NOT NULL DEFAULT 'UTC' COMMENT '默认时区（IANA TZ，如 UTC/Asia/Shanghai）：窗口计算/展示的缺省时区',
    `docs_url`         VARCHAR(512)    NULL COMMENT '官方文档/说明链接：便于排障或核对 API 用法',
    `is_active`        TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用该来源：1=启用；0=停用（应用读取时可据此过滤）',
    `lifecycle_status` VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status)：生命周期：读侧仅取 ACTIVE/有效项',

    -- BaseDO（统一审计字段）
    `record_remarks`   JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`       BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`  VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`       BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`  VARCHAR(100)    NULL COMMENT '更新人姓名',
    `version`          BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`       VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    -- 字典以编码关联：lifecycle_status 使用 sys_dict_item.item_code（type=lifecycle_status）
    UNIQUE KEY `uk_reg_provenance_code` (`provenance_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='数据来源登记：记录外部数据源（Provenance）的基础信息，作为所有配置的外键根。';


/* ===========================================================
 * 端点定义（Endpoint Definition）
 * 说明：定义各来源在不同时间区间内可用的搜索/详情/令牌等端点形态与默认参数。
 * =========================================================== */
DROP TABLE IF EXISTS `reg_prov_endpoint_def`;
CREATE TABLE `reg_prov_endpoint_def`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键：端点定义记录ID；可被凭证表 reg_prov_credential.endpoint_id 可选引用',
    `provenance_id`        BIGINT UNSIGNED NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `scope`                VARCHAR(8)      NOT NULL COMMENT 'DICT CODE(type=scope)：配置作用域 SOURCE/TASK',
    `task_type`            VARCHAR(32)     NULL COMMENT '任务类型文本：当 scope=TASK 时需填写；示例 harvest/update/backfill（去 ENUM 化）',
    `endpoint_name`        VARCHAR(64)     NOT NULL COMMENT '端点逻辑名称：如 search / detail / works / token；用于业务侧选择端点',

    `effective_from`       TIMESTAMP(6)    NOT NULL COMMENT '生效起始（含）；不重叠由应用层保证',
    `effective_to`         TIMESTAMP(6)    NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    `endpoint_usage`       VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=endpoint_usage)：用途 SEARCH/DETAIL/BATCH/AUTH/HEALTH',
    `http_method`          VARCHAR(8)      NOT NULL COMMENT 'DICT CODE(type=http_method)：HTTP 方法 GET/POST/PUT/PATCH/DELETE/HEAD/OPTIONS',
    `path_template`        VARCHAR(512)    NOT NULL COMMENT '路径模板：相对路径或绝对路径；可包含占位符（如 /entrez/eutils/esearch.fcgi）',
    `default_query_params` JSON            NULL COMMENT '默认查询参数JSON：作为每次请求的基础 query（运行时可覆盖/合并）',
    `default_body_payload` JSON            NULL COMMENT '默认请求体JSON：POST/PUT/PATCH 的基础 body（运行时可覆盖/合并）',
    `request_content_type` VARCHAR(64)     NULL COMMENT '请求体内容类型：如 application/json / application/x-www-form-urlencoded',
    `is_auth_required`     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否需要鉴权：1=需要；0=不需要（匿名可调用）',
    `credential_hint_name` VARCHAR(64)     NULL COMMENT '凭证提示：偏好使用的凭证标签/名称，辅助在多凭证中挑选',

    /* 端点级分页/批量覆盖（可为空：由 reg_prov_pagination_cfg 或应用决定） */
    `page_param_name`      VARCHAR(64)     NULL COMMENT '分页页码参数名（端点级覆盖项）',
    `page_size_param_name` VARCHAR(64)     NULL COMMENT '分页每页大小参数名（端点级覆盖项）',
    `cursor_param_name`    VARCHAR(64)     NULL COMMENT '游标/令牌参数名（端点级覆盖项）',
    `ids_param_name`       VARCHAR(64)     NULL COMMENT '批量详情请求中，ID列表的参数名（端点级覆盖项）',

    /* 生成列：将 task_type 标准化为 ALL 或实际值，便于唯一键 */
    `task_type_key`        VARCHAR(16) AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：当 task_type 为空取 ALL；用于维度唯一键',
    `lifecycle_status`     VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status)：生命周期',

    -- BaseDO（统一审计字段）
    `record_remarks`       JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`           BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`      VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`           BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`      VARCHAR(100)    NULL COMMENT '更新人姓名',
    `version`              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`           VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`              TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_endpoint_def__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    -- 字典以编码关联：scope/endpoint_usage/http_method/lifecycle_status 使用 sys_dict_item.item_code

    /* 维度唯一：同 (provenance_id, scope, task_type_key, endpoint_name) 下 effective_from 唯一；不重叠由应用保证 */
    UNIQUE KEY `uk_reg_prov_endpoint_def__dim_from` (`provenance_id`, `scope`, `task_type_key`, `endpoint_name`,
                                                     `effective_from`),
    KEY `idx_reg_prov_endpoint_def__dim_to` (`provenance_id`, `scope`, `task_type_key`, `endpoint_name`,
                                             `effective_to`),
    KEY `idx_reg_prov_endpoint_def__usage` (`endpoint_usage`),
    KEY `idx_reg_prov_endpoint_def__active` (`provenance_id`, `scope`, `task_type_key`, `effective_from` DESC, `id`
                                             DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='端点定义：描述来源在特定时间区间内可用的搜索/详情/令牌等端点形态与默认参数；支持 SOURCE/TASK 作用域。';


/* ===========================================================
 * 时间窗口与增量指针配置（Window & Offset Configuration）
 * =========================================================== */
DROP TABLE IF EXISTS `reg_prov_window_offset_cfg`;
CREATE TABLE `reg_prov_window_offset_cfg`
(
    `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键：时间窗口与增量指针配置记录ID',
    `provenance_id`           BIGINT UNSIGNED NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `scope`                   VARCHAR(8)      NOT NULL COMMENT 'DICT CODE(type=scope)：配置作用域 SOURCE/TASK',
    `task_type`               VARCHAR(32)     NULL COMMENT '任务类型文本（去 ENUM 化）',

    `effective_from`          TIMESTAMP(6)    NOT NULL COMMENT '生效起始（含）；不重叠由应用层保证',
    `effective_to`            TIMESTAMP(6)    NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    /* 窗口定义 */
    `window_mode`             VARCHAR(16)     NOT NULL COMMENT 'DICT CODE(type=window_mode)：窗口模式 SLIDING/CALENDAR',
    `window_size_value`       INT             NOT NULL DEFAULT 1 COMMENT '窗口长度的数值部分，如 1/7/30；单位见 window_size_unit',
    `window_size_unit`        VARCHAR(16)     NOT NULL COMMENT 'DICT CODE(type=time_unit)：窗口长度单位 SECOND/MINUTE/HOUR/DAY',
    `calendar_align_to`       VARCHAR(16)     NULL COMMENT 'CALENDAR 模式对齐粒度（去 ENUM 化，示例：HOUR/DAY/WEEK/MONTH）',
    `lookback_value`          INT             NULL COMMENT '回看长度数值：用于补偿延迟数据（与 lookback_unit 搭配）',
    `lookback_unit`           VARCHAR(16)     NULL COMMENT 'DICT CODE(type=time_unit)：回看长度单位 SECOND/MINUTE/HOUR/DAY',
    `overlap_value`           INT             NULL COMMENT '窗口重叠长度数值：相邻窗口之间的重叠（迟到兜底）',
    `overlap_unit`            VARCHAR(16)     NULL COMMENT 'DICT CODE(type=time_unit)：窗口重叠单位 SECOND/MINUTE/HOUR/DAY',
    `watermark_lag_seconds`   INT             NULL COMMENT '水位滞后秒数：处理乱序/迟到数据时允许的最大延迟',

    /* 增量指针定义 */
    `offset_type`             VARCHAR(16)     NOT NULL COMMENT 'DICT CODE(type=offset_type)：指针类型 DATE/ID/COMPOSITE',
    `offset_field_name`       VARCHAR(128)    NULL COMMENT '指针字段名或 JSONPath（如 DATE 字段名/ID 字段名/复合键主维度）',
    `offset_date_format`      VARCHAR(64)     NULL COMMENT 'DATE 指针格式/语义：如 ISO_INSTANT、epochMillis、YYYYMMDD',
    `default_date_field_name` VARCHAR(64)     NULL COMMENT '默认增量日期字段名（如 PubMed: EDAT/PDAT/MHDA；Crossref: indexed-date）',
    `max_ids_per_window`      INT             NULL COMMENT '单窗口最多可处理的ID数量（超过则需二次切窗）',
    `max_window_span_seconds` INT             NULL COMMENT '单窗口最大跨度（秒）：过长窗口将被强制切分',

    /* 生成列 */
    `task_type_key`           VARCHAR(16) AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：task_type 标准化；为空取 ALL',
    `lifecycle_status`        VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status)：生命周期',

    -- BaseDO（统一审计字段）
    `record_remarks`          JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`              BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`         VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`              BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`         VARCHAR(100)    NULL COMMENT '更新人姓名',
    `version`                 BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`              VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`                 TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_window_offset_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    -- 字典以编码关联：scope/window_mode/window_size_unit/lookback_unit/overlap_unit/offset_type/lifecycle_status 使用 item_code
    UNIQUE KEY `uk_reg_prov_window_offset_cfg__dim_from` (`provenance_id`, `scope`, `task_type_key`, `effective_from`),
    KEY `idx_reg_prov_window_offset_cfg__dim_to` (`provenance_id`, `scope`, `task_type_key`, `effective_to`),
    KEY `idx_reg_prov_window_offset_cfg__active` (`provenance_id`, `scope`, `task_type_key`, `effective_from` DESC,
                                                  `id`
                                                  DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='时间窗口与增量指针配置：定义采集任务如何切分时间窗口与推进增量指针（DATE/ID/COMPOSITE）；支持 SOURCE/TASK 作用域。';


/* ===========================================================
 * 分页与游标配置（Pagination Configuration）
 * =========================================================== */
DROP TABLE IF EXISTS `reg_prov_pagination_cfg`;
CREATE TABLE `reg_prov_pagination_cfg`
(
    `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键：分页与游标配置记录ID',
    `provenance_id`           BIGINT UNSIGNED NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `scope`                   VARCHAR(8)      NOT NULL COMMENT 'DICT CODE(type=scope)：配置作用域 SOURCE/TASK',
    `task_type`               VARCHAR(32)     NULL COMMENT '任务类型文本（去 ENUM 化）',

    `effective_from`          TIMESTAMP(6)    NOT NULL COMMENT '生效起始（含）',
    `effective_to`            TIMESTAMP(6)    NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    `pagination_mode`         VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=pagination_mode)：分页模式 PAGE_NUMBER/CURSOR/TOKEN/SCROLL',
    `page_size_value`         INT             NULL COMMENT '每页大小：PAGE_NUMBER/SCROLL 模式常用；空表示使用应用默认',
    `max_pages_per_execution` INT             NULL COMMENT '单次执行最多翻页数：防止深翻造成高成本',
    `page_number_param_name`  VARCHAR(64)     NULL COMMENT '页码参数名（如 page）',
    `page_size_param_name`    VARCHAR(64)     NULL COMMENT '每页大小参数名（如 pageSize/rows）',
    `start_page_number`       INT             NOT NULL DEFAULT 1 COMMENT '起始页码（PAGE_NUMBER 模式的起点）',
    `sort_field_param_name`   VARCHAR(64)     NULL COMMENT '排序字段参数名（如 sort）',
    `sort_direction`          VARCHAR(8)      NULL COMMENT '排序方向文本（ASC/DESC）',

    /* 游标/令牌模式 */
    `cursor_param_name`       VARCHAR(64)     NULL COMMENT '游标参数名（如 cursor/next_token）',
    `initial_cursor_value`    VARCHAR(256)    NULL COMMENT '初始游标值：为空表示由应用从响应或配置中确定',
    `next_cursor_jsonpath`    VARCHAR(512)    NULL COMMENT '如何从响应中提取下一页游标的路径（JSONPath/JMESPath等）',
    `has_more_jsonpath`       VARCHAR(512)    NULL COMMENT '如何判断是否还有下一页的路径（布尔）',
    `total_count_jsonpath`    VARCHAR(512)    NULL COMMENT '如何从响应中提取总条数的路径（可选）',
    `next_cursor_xpath`       VARCHAR(512)    NULL COMMENT '如何从 XML 响应中提取下一页游标的 XPath（可选）',
    `has_more_xpath`          VARCHAR(512)    NULL COMMENT '如何从 XML 响应中判断是否还有下一页的 XPath（布尔，可选）',
    `total_count_xpath`       VARCHAR(512)    NULL COMMENT '如何从 XML 响应中提取总条数的 XPath（可选）',

    `task_type_key`           VARCHAR(16) AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：task_type 标准化；为空取 ALL',
    `lifecycle_status`        VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status)：生命周期：读侧仅取 ACTIVE/有效项',

    -- BaseDO（统一审计字段）
    `record_remarks`          JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`              BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`         VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`              BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`         VARCHAR(100)    NULL COMMENT '更新人姓名',
    `version`                 BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`              VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`                 TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_pagination_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    -- 字典以编码关联：scope/pagination_mode/lifecycle_status 使用 item_code
    UNIQUE KEY `uk_reg_prov_pagination_cfg__dim_from` (`provenance_id`, `scope`, `task_type_key`, `effective_from`),
    KEY `idx_reg_prov_pagination_cfg__dim_to` (`provenance_id`, `scope`, `task_type_key`, `effective_to`),
    KEY `idx_reg_prov_pagination_cfg__active` (`provenance_id`, `scope`, `task_type_key`, `effective_from` DESC, `id`
                                               DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='分页与游标配置：配置页码/游标/令牌式分页的参数与响应提取规则；支持 SOURCE/TASK 作用域。';


/* ===========================================================
 * HTTP 策略配置（HTTP Policy Configuration）
 * =========================================================== */
DROP TABLE IF EXISTS `reg_prov_http_cfg`;
CREATE TABLE `reg_prov_http_cfg`
(
    `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键：HTTP 策略配置记录ID',
    `provenance_id`           BIGINT UNSIGNED NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `scope`                   VARCHAR(8)      NOT NULL COMMENT 'DICT CODE(type=scope)：配置作用域 SOURCE/TASK',
    `task_type`               VARCHAR(32)     NULL COMMENT '任务类型文本（去 ENUM 化）',

    `effective_from`          TIMESTAMP(6)    NOT NULL COMMENT '生效起始（含）',
    `effective_to`            TIMESTAMP(6)    NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    `base_url_override`       VARCHAR(512)    NULL COMMENT '基础URL覆盖：若不为空，将覆盖 reg_provenance.base_url_default',
    `default_headers_json`    JSON            NULL COMMENT '默认HTTP Headers（JSON），运行时与请求头合并',
    `timeout_connect_millis`  INT             NULL COMMENT '连接超时（毫秒）：建立 TCP/SSL 连接的超时时间',
    `timeout_read_millis`     INT             NULL COMMENT '读取超时（毫秒）：读取响应主体的超时时间',
    `timeout_total_millis`    INT             NULL COMMENT '总超时（毫秒）：一次请求从开始到结束的最大耗时',
    `tls_verify_enabled`      TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否校验 TLS 证书：1=开启；0=关闭（仅测试环境）',
    `proxy_url_value`         VARCHAR(512)    NULL COMMENT '代理地址：如 http://user:pass@host:port 或 socks5://host:port',
    `accept_compress_enabled` TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否接受压缩响应：1=接受 gzip/deflate/br 等；0=不接受',
    `prefer_http2_enabled`    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否优先使用 HTTP/2（若客户端/服务端支持）',
    `retry_after_policy`      VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=retry_after_policy)：对服务端 Retry-After 的处理策略',
    `retry_after_cap_millis`  INT             NULL COMMENT '当选择 RESPECT/CLAMP 时的最大等待上限（毫秒）',
    `idempotency_header_name` VARCHAR(64)     NULL COMMENT '幂等性 Header 名称（如 Idempotency-Key），用于避免重复提交',
    `idempotency_ttl_seconds` INT             NULL COMMENT '幂等性键过期时间（秒），仅客户端/服务端支持时有效',

    `task_type_key`           VARCHAR(16) AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：task_type 标准化；为空取 ALL',
    `lifecycle_status`        VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status)：生命周期：读侧仅取 ACTIVE/有效项',

    -- BaseDO（统一审计字段）
    `record_remarks`          JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`              BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`         VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`              BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`         VARCHAR(100)    NULL COMMENT '更新人姓名',
    `version`                 BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`              VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`                 TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_http_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    -- 字典以编码关联：scope/retry_after_policy/lifecycle_status 使用 item_code
    UNIQUE KEY `uk_reg_prov_http_cfg__dim_from` (`provenance_id`, `scope`, `task_type_key`, `effective_from`),
    KEY `idx_reg_prov_http_cfg__dim_to` (`provenance_id`, `scope`, `task_type_key`, `effective_to`),
    KEY `idx_reg_prov_http_cfg__active` (`provenance_id`, `scope`, `task_type_key`, `effective_from` DESC, `id` DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='HTTP 策略配置：基础URL/请求头/超时/代理/Retry-After/幂等等策略；支持 SOURCE/TASK 作用域。';


/* ===========================================================
 * 批量抓取与请求成型配置（Batching & Request Shaping）
 * =========================================================== */
DROP TABLE IF EXISTS `reg_prov_batching_cfg`;
CREATE TABLE `reg_prov_batching_cfg`
(
    `id`                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键：批量抓取与请求成型配置记录ID',
    `provenance_id`              BIGINT UNSIGNED NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `scope`                      VARCHAR(8)      NOT NULL COMMENT 'DICT CODE(type=scope)：配置作用域 SOURCE/TASK',
    `task_type`                  VARCHAR(32)     NULL COMMENT '任务类型文本（去 ENUM 化）',

    `effective_from`             TIMESTAMP(6)    NOT NULL COMMENT '生效起始（含）',
    `effective_to`               TIMESTAMP(6)    NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    `detail_fetch_batch_size`    INT             NULL COMMENT '单次详情抓取的批大小（条数），为空则由应用使用默认',
    `endpoint_id`                BIGINT UNSIGNED NULL COMMENT '可选：关联端点定义 → reg_prov_endpoint_def(id)',
    `credential_name`            VARCHAR(64)     NULL COMMENT '可选：关联凭证逻辑名，用于细化控制',
    `ids_param_name`             VARCHAR(64)     NULL COMMENT '批详情请求中，ID 列表的参数名；为空则由端点或应用决定',
    `ids_join_delimiter`         VARCHAR(8)      NULL     DEFAULT ',' COMMENT 'ID 列表拼接的分隔符（如 , 或 +）',
    `max_ids_per_request`        INT             NULL COMMENT '每个 HTTP 请求允许携带的 ID 最大数量（硬上限）',
    `prefer_compact_payload`     TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否尽量压缩请求体（如去掉冗余空白）',
    `payload_compress_strategy`  VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=payload_compress_strategy)：请求体压缩策略',
    `app_parallelism_degree`     INT             NULL COMMENT '应用层并行请求数：限制整体并发度',
    `per_host_concurrency_limit` INT             NULL COMMENT '每主机并发上限：限制与同一主机的并发连接数',
    `http_conn_pool_size`        INT             NULL COMMENT '连接池大小：HTTP 连接复用池的容量（客户端支持时生效）',
    `backpressure_strategy`      VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=backpressure_strategy)：背压策略',
    `request_template_json`      JSON            NULL COMMENT '请求成型模板：用于将内部字段映射到 query/body 的规则 JSON',

    `task_type_key`              VARCHAR(16) AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：task_type 标准化；为空取 ALL',
    `lifecycle_status`           VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status)：生命周期：读侧仅取 ACTIVE/有效项',

    -- BaseDO（统一审计字段）
    `record_remarks`             JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`                 TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`                 BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`            VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`                 TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`                 BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`            VARCHAR(100)    NULL COMMENT '更新人姓名',
    `version`                    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`                 VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`                    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_batching_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    CONSTRAINT `fk_reg_prov_batching_cfg__endpoint` FOREIGN KEY (`endpoint_id`) REFERENCES `reg_prov_endpoint_def` (`id`),
    -- 字典以编码关联：scope/payload_compress_strategy/backpressure_strategy/lifecycle_status 使用 sys_dict_item.item_code
    UNIQUE KEY `uk_reg_prov_batching_cfg__dim_from` (`provenance_id`, `scope`, `task_type_key`, `effective_from`),
    KEY `idx_reg_prov_batching_cfg__dim_to` (`provenance_id`, `scope`, `task_type_key`, `effective_to`),
    KEY `idx_reg_prov_batching_cfg__by_ep_cred` (`provenance_id`, `scope`, `task_type_key`, `endpoint_id`,
                                                 `credential_name`),
    KEY `idx_reg_prov_batching_cfg__active` (`provenance_id`, `scope`, `task_type_key`, `effective_from` DESC, `id`
                                             DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='批量抓取与请求成型配置：控制详情批量、ID 拼接、并行与背压、请求模板与压缩等；支持 SOURCE/TASK 作用域。';


/* ===========================================================
 * 重试与退避配置（Retry & Backoff Configuration）
 * =========================================================== */
DROP TABLE IF EXISTS `reg_prov_retry_cfg`;
CREATE TABLE `reg_prov_retry_cfg`
(
    `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键：重试与退避配置记录ID',
    `provenance_id`           BIGINT UNSIGNED NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `scope`                   VARCHAR(8)      NOT NULL COMMENT 'DICT CODE(type=scope)：配置作用域 SOURCE/TASK',
    `task_type`               VARCHAR(32)     NULL COMMENT '任务类型文本（去 ENUM 化）',

    `effective_from`          TIMESTAMP(6)    NOT NULL COMMENT '生效起始（含）',
    `effective_to`            TIMESTAMP(6)    NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    `max_retry_times`         INT             NULL COMMENT '最大重试次数：为空则使用应用默认；0 表示不重试',
    `backoff_policy_type`     VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=backoff_policy_type)：退避策略',
    `initial_delay_millis`    INT             NULL COMMENT '首个重试的延迟（毫秒）',
    `max_delay_millis`        INT             NULL COMMENT '单次重试的最大延迟（毫秒）',
    `exp_multiplier_value`    DOUBLE          NULL COMMENT '指数退避的乘数因子（如 2.0）',
    `jitter_factor_ratio`     DOUBLE          NULL COMMENT '抖动系数（0~1）：随机扰动的幅度',
    `retry_http_status_json`  JSON            NULL COMMENT '可重试的 HTTP 状态码列表（JSON 数组，如 [429,500,503]）',
    `giveup_http_status_json` JSON            NULL COMMENT '直接放弃的 HTTP 状态码列表（JSON 数组）',
    `retry_on_network_error`  TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '网络错误是否重试：1=重试；0=不重试',
    `circuit_break_threshold` INT             NULL COMMENT '断路器阈值：连续失败次数达到该值后短路',
    `circuit_cooldown_millis` INT             NULL COMMENT '断路器冷却时间（毫秒）：过后允许半开探测',

    `task_type_key`           VARCHAR(16) AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：task_type 标准化；为空取 ALL',
    `lifecycle_status`        VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status)：生命周期：读侧仅取 ACTIVE/有效项',

    -- BaseDO（统一审计字段）
    `record_remarks`          JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`              BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`         VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`              BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`         VARCHAR(100)    NULL COMMENT '更新人姓名',
    `version`                 BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`              VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`                 TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_retry_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    -- 字典以编码关联：scope/backoff_policy_type/lifecycle_status 使用 item_code
    UNIQUE KEY `uk_reg_prov_retry_cfg__dim_from` (`provenance_id`, `scope`, `task_type_key`, `effective_from`),
    KEY `idx_reg_prov_retry_cfg__dim_to` (`provenance_id`, `scope`, `task_type_key`, `effective_to`),
    KEY `idx_reg_prov_retry_cfg__active` (`provenance_id`, `scope`, `task_type_key`, `effective_from` DESC, `id`
                                          DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='重试与退避配置：定义可重试次数、退避与抖动、网络错误策略以及断路器阈值与冷却时间；支持 SOURCE/TASK 作用域。';


/* ===========================================================
 * 限流与并发配置（Rate Limit & Concurrency）
 * =========================================================== */
DROP TABLE IF EXISTS `reg_prov_rate_limit_cfg`;
CREATE TABLE `reg_prov_rate_limit_cfg`
(
    `id`                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键：限流与并发配置记录ID',
    `provenance_id`              BIGINT UNSIGNED NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `scope`                      VARCHAR(8)      NOT NULL COMMENT 'DICT CODE(type=scope)：配置作用域 SOURCE/TASK',
    `task_type`                  VARCHAR(32)     NULL COMMENT '任务类型文本（去 ENUM 化）',

    `effective_from`             TIMESTAMP(6)    NOT NULL COMMENT '生效起始（含）',
    `effective_to`               TIMESTAMP(6)    NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    `rate_tokens_per_second`     INT             NULL COMMENT '全局 QPS（令牌生成速率，令牌/秒）；为空表示使用应用默认或不限制',
    `burst_bucket_capacity`      INT             NULL COMMENT '突发桶容量（最大瞬时令牌数），用于吸收短时峰值',
    `max_concurrent_requests`    INT             NULL COMMENT '全局并发请求上限（连接/请求数），为空表示默认',
    `per_credential_qps_limit`   INT             NULL COMMENT '按密钥的 QPS 上限：多把密钥时可分摊流量',
    `bucket_granularity_scope`   VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=bucket_granularity_scope)：令牌桶粒度',
    `smoothing_window_millis`    INT             NULL COMMENT '平滑窗口（毫秒）：用于平滑令牌发放与计数',
    `respect_server_rate_header` TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否遵循服务端速率响应头（如 X-RateLimit-*）：1=遵循；0=忽略',
    `endpoint_id`                BIGINT UNSIGNED NULL COMMENT '可选：关联端点定义 → reg_prov_endpoint_def(id)',
    `credential_name`            VARCHAR(64)     NULL COMMENT '可选：关联凭证逻辑名，用于细化限流',

    `task_type_key`              VARCHAR(16) AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：task_type 标准化；为空取 ALL',
    `lifecycle_status`           VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status)：生命周期：读侧仅取 ACTIVE/有效项',

    -- BaseDO（统一审计字段）
    `record_remarks`             JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`                 TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`                 BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`            VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`                 TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`                 BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`            VARCHAR(100)    NULL COMMENT '更新人姓名',
    `version`                    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`                 VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`                    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_rate_limit_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    CONSTRAINT `fk_reg_prov_rate_limit_cfg__endpoint` FOREIGN KEY (`endpoint_id`) REFERENCES `reg_prov_endpoint_def` (`id`),
    -- 字典以编码关联：scope/bucket_granularity_scope/lifecycle_status 使用 sys_dict_item.item_code
    UNIQUE KEY `uk_reg_prov_rate_limit_cfg__dim_from` (`provenance_id`, `scope`, `task_type_key`, `effective_from`),
    KEY `idx_reg_prov_rate_limit_cfg__dim_to` (`provenance_id`, `scope`, `task_type_key`, `effective_to`),
    KEY `idx_reg_prov_rate_limit_cfg__by_ep_cred` (`provenance_id`, `scope`, `task_type_key`, `endpoint_id`,
                                                   `credential_name`),
    KEY `idx_reg_prov_rate_limit_cfg__active` (`provenance_id`, `scope`, `task_type_key`, `effective_from` DESC, `id`
                                               DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='限流与并发配置：配置 QPS/突发/并发与桶粒度（全局/按密钥/按端点），可结合服务端速率响应头进行自适应；支持 SOURCE/TASK 作用域。';


/* ===========================================================
 * 鉴权/密钥配置（Credentials / Authentication）
 * 说明：为来源或任务配置多把凭证；可选绑定到具体端点。允许重叠有效期，以支持轮换与平滑切换。
 * =========================================================== */
DROP TABLE IF EXISTS `reg_prov_credential`;
CREATE TABLE `reg_prov_credential`
(
    `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键：凭证记录ID',
    `provenance_id`           BIGINT UNSIGNED NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `scope`                   VARCHAR(8)      NOT NULL COMMENT 'DICT CODE(type=scope)：凭证作用域 SOURCE/TASK',
    `task_type`               VARCHAR(32)     NULL COMMENT '任务类型文本（去 ENUM 化）',
    `endpoint_id`             BIGINT UNSIGNED NULL COMMENT '可选外键：若该凭证仅用于某个端点，则指定端点配置ID → reg_prov_endpoint_def(id)',

    `credential_name`         VARCHAR(64)     NOT NULL COMMENT '凭证标签：用于人类可读标识与应用优先级选择（可与端点的 credential_hint_name 配合）',
    `auth_type`               VARCHAR(32)     NOT NULL COMMENT '鉴权类型文本（去 ENUM 化）；如 API_KEY/BEARER/BASIC/OAUTH2/CUSTOM',
    `inbound_location`        VARCHAR(16)     NOT NULL COMMENT 'DICT CODE(type=inbound_location)：凭证放置位置',
    `credential_field_name`   VARCHAR(128)    NULL COMMENT '凭证字段名：如 Authorization / api_key / access_token',
    `credential_value_prefix` VARCHAR(64)     NULL COMMENT '凭证值前缀：如 "Bearer "，会拼接在凭证值之前',
    `credential_value_ref`    VARCHAR(256)    NULL COMMENT '凭证值引用（如 KMS 密钥名/路径），不落明文',
    `basic_username_ref`      VARCHAR(256)    NULL COMMENT 'Basic 认证用户名引用',
    `basic_password_ref`      VARCHAR(256)    NULL COMMENT 'Basic 认证密码引用',

    /* OAuth2 客户端凭证（全部使用引用字段） */
    `oauth_token_url`         VARCHAR(512)    NULL COMMENT 'OAuth2 Token 端点 URL（client credentials 等流程）',
    `oauth_client_id_ref`     VARCHAR(256)    NULL COMMENT 'OAuth2 客户端 ID 的引用',
    `oauth_client_secret_ref` VARCHAR(256)    NULL COMMENT 'OAuth2 客户端密钥的引用',
    `oauth_scope`             VARCHAR(512)    NULL COMMENT 'OAuth2 请求的 scope（以空格或逗号分隔）',
    `oauth_audience`          VARCHAR(512)    NULL COMMENT 'OAuth2 audience/资源标识',
    `extra_json`              JSON            NULL COMMENT '自定义扩展字段：如签名算法、HMAC 盐、额外 Header 模板等（由应用解释）',

    `effective_from`          TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '生效起始（含）；允许多把凭证区间重叠以支持轮换',
    `effective_to`            TIMESTAMP(6)    NULL COMMENT '生效结束（不含）；NULL 表示长期有效',
    `is_default_preferred`    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否标记为默认/首选：1=默认（用于同维度多把时的优先选择）；0=普通',

    /* 生成列与弱互斥唯一键支持 */
    `task_type_key`           VARCHAR(16) AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：task_type 标准化；为空取 ALL',
    `endpoint_id_key`         BIGINT AS (IFNULL(`endpoint_id`, 0)) STORED COMMENT '生成列：端点ID的统一键（NULL→0）用于唯一约束',
    `preferred_1`             CHAR(1) AS (CASE WHEN `is_default_preferred` = 1 THEN 'Y' ELSE NULL END) STORED COMMENT '生成列：默认优先=Y（NULL 可重复）',
    `lifecycle_status`        VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status)：生命周期：读侧仅取 ACTIVE/有效项',

    -- BaseDO（统一审计字段）
    `record_remarks`          JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`              BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`         VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`              BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`         VARCHAR(100)    NULL COMMENT '更新人姓名',
    `version`                 BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`              VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`                 TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_credential__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    CONSTRAINT `fk_reg_prov_credential__endpoint` FOREIGN KEY (`endpoint_id`) REFERENCES `reg_prov_endpoint_def` (`id`),
    -- 字典以编码关联：scope/inbound_location/lifecycle_status 使用 item_code

    KEY `idx_reg_prov_credential__dim` (`provenance_id`, `scope`, `task_type_key`, `endpoint_id`, `credential_name`),
    KEY `idx_reg_prov_credential__effective` (`effective_from`, `effective_to`),
    KEY `idx_reg_prov_credential__active` (`provenance_id`, `scope`, `task_type_key`, `effective_from` DESC, `id`
                                           DESC),
    UNIQUE KEY `uk_reg_prov_credential__preferred_one` (`provenance_id`, `scope`, `task_type_key`, `endpoint_id_key`,
                                                        `preferred_1`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='鉴权/密钥配置：凭证以“引用”存储（不落明文）；支持端点绑定与重叠有效期；弱互斥保证同维度默认首选唯一。';


-- =====================================================================
-- Registry · Expr 子域 · 统一命名&必修复项（使用 code，含作用域/任务/时间片，维度唯一键）
--   1) reg_expr_field_dict        —— 全局统一字段字典（无源敏感）
--   2) reg_prov_api_param_map     —— 源敏感：API 参数映射（标准键 → 供应商参数名）
--   3) reg_prov_expr_capability   —— 源敏感：字段能力声明
--   4) reg_prov_expr_render_rule  —— 源敏感：表达式渲染规则（query/params）
--   * 不建物理外键；通过应用层/校验 SQL 保证一致性
--   * 所有“枚举值”均使用 *_code（来源 sys_dict_item.item_code，建议全大写）
--   * 源敏感三表统一采用：scope / task_type / task_type_key / effective_from / effective_to
--   * 时间片语义：[from, to) —— 起点含、终点不含，UTC
-- =====================================================================

/* ====================================================================
 * 1) 全局统一字段字典（无源敏感）
 *    - 统一系统内部字段的语义定义；例如：publish_date / ti / ab / tiab
 *    - 不区分来源；仅描述字段本体的数据类型/基数/是否对外暴露等
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `reg_expr_field_dict`
(
    `id`               BIGINT UNSIGNED NOT NULL COMMENT '主键（雪花/发号器或发号器），仅用于库内标识；跨环境迁移不依赖此值',
    `field_key`        VARCHAR(64)     NOT NULL COMMENT '统一内部字段键：小写蛇形或约定缩写，如 publish_date/ti/ab/tiab；全库唯一，作为配置与 GitOps 的稳定键',
    `display_name`     VARCHAR(128)    NULL COMMENT '字段的人类可读名称，用于控制台/可视化配置展示（可选）',
    `description`      VARCHAR(255)    NULL COMMENT '字段说明/使用约束/对外暴露注意事项等（可选）',

    `data_type_code`   VARCHAR(32)     NOT NULL COMMENT '数据类型 code（字典 reg_data_type）：DATE/DATETIME/NUMBER/TEXT/KEYWORD/BOOLEAN/TOKEN；用于校验与渲染分支',
    `cardinality_code` VARCHAR(16)     NOT NULL DEFAULT 'SINGLE' COMMENT '基数 code（字典 reg_cardinality）：SINGLE=单值，MULTI=多值；决定是否允许一个字段出现多次值',
    `exposable`        TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否允许对外暴露/被使用的全局开关：1=可暴露，0=不可暴露；与来源能力层（capability）解耦',
    `is_date`          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '冗余标记：1=日期类字段（便于 UI 及 DateLens 快速判断）；与 data_type_code=DATE/DATETIME 一般一致',

    `record_remarks`   JSON            NULL COMMENT '审计备注：JSON 数组，记录修订说明/评审意见/运维备注等',
    `version`          BIGINT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号：配合应用层 CAS 更新，避免并发覆盖',
    `ip_address`       VARBINARY(16)   NULL COMMENT '最后一次写入来源 IP（二进制，兼容 IPv4/IPv6），用于审计与风控',
    `created_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（UTC，微秒精度）',
    `created_by`       BIGINT          NULL COMMENT '创建人 ID（逻辑外键，通常指用户/系统账号）',
    `created_by_name`  VARCHAR(64)     NULL COMMENT '创建人名称/登录名快照',
    `updated_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间（UTC，微秒精度）',
    `updated_by`       BIGINT          NULL COMMENT '最后更新人 ID（逻辑外键）',
    `updated_by_name`  VARCHAR(64)     NULL COMMENT '最后更新人名称/登录名快照',
    `deleted`          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除标记：0=未删，1=已删；读侧统一过滤 deleted=0',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_expr_field_key` (`field_key`) COMMENT '确保统一字段键在全局唯一',
    KEY `idx_expr_field__updated` (`updated_at`) COMMENT '便于按更新时间排序/增量同步'
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='（Registry·Expr）统一内部字段字典（无源敏感；统一语义的唯一事实来源）';


/* ====================================================================
 * 2) 源敏感：API 参数名映射（标准键 → 供应商参数名）
 *    - 把统一语义键（std_key，如 from/to/ti）映射为具体平台的 HTTP 参数名（如 mindate/maxdate/term）
 *    - 仅负责“键名层”的映射；不负责 query 模板；值级转换仅通过 transform_code 声明
 *    - 维度唯一 + 时间片：[from,to)；读侧按 NOW 命中，按 from DESC 取一条
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `reg_prov_api_param_map`
(
    `id`                  BIGINT UNSIGNED NOT NULL COMMENT '主键（雪花/发号器），库内标识',
    `provenance_id`       BIGINT UNSIGNED NOT NULL COMMENT '来源 ID（逻辑外键 → reg_provenance.id），区分不同数据源/供应商',

    `scope`               VARCHAR(8)      NOT NULL DEFAULT 'SOURCE' COMMENT '作用域 code：SOURCE=按来源生效；TASK=按任务类型限定（灰度发布/试点用途）',
    `task_type`           VARCHAR(32)     NULL COMMENT '任务类型（可空）：例如 HARVEST/UPDATE/BACKFILL/SANDBOX；用于 TASK 级灰度',
    `task_type_key`       VARCHAR(16) GENERATED ALWAYS AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：将 NULL 归一化为 ALL，稳定维度 Join/唯一键',

    `operation_code`      VARCHAR(32)     NOT NULL COMMENT '端点操作 code（字典 reg_operation）：SEARCH/DETAIL/LOOKUP...，与 reg_prov 的端点执行合同一致',
    `std_key`             VARCHAR(64)     NOT NULL COMMENT '标准键（统一内部语义键）：如 from/to/ti/ab；通常来源于渲染阶段产出的标准参数名',
    `provider_param_name` VARCHAR(64)     NOT NULL COMMENT '供应商参数名：具体平台 HTTP 参数名，如 mindate/maxdate/term/retmax',
    `transform_code`      VARCHAR(64)     NULL COMMENT '可选：值级转换 code（字典 reg_transform），如 TO_EXCLUSIVE_MINUS_1D 表示将结束日闭区间转开区间',
    `notes`               JSON            NULL COMMENT '补充说明：JSON 对象，可记录平台差异/边界条件等',

    `effective_from`      TIMESTAMP(6)    NOT NULL COMMENT '生效起（含）；UTC；时间片开始边界',
    `effective_to`        TIMESTAMP(6)    NULL COMMENT '生效止（不含）；UTC；可空表示“当前仍生效”',
    CONSTRAINT `ck_param_map_range` CHECK (`effective_to` IS NULL OR `effective_to` > `effective_from`),

    `record_remarks`      JSON            NULL COMMENT '审计备注：JSON 数组',
    `version`             BIGINT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`          VARBINARY(16)   NULL COMMENT '最后一次写入来源 IP（二进制），IPv4/IPv6',
    `created_at`          TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（UTC）',
    `created_by`          BIGINT          NULL COMMENT '创建人 ID',
    `created_by_name`     VARCHAR(64)     NULL COMMENT '创建人名称/登录名快照',
    `updated_at`          TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间（UTC）',
    `updated_by`          BIGINT          NULL COMMENT '更新人 ID',
    `updated_by_name`     VARCHAR(64)     NULL COMMENT '更新人名称/登录名快照',
    `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除：0=未删，1=已删；读侧统一过滤',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_param_map__dim_from`
        (`provenance_id`, `scope`, `task_type_key`, `operation_code`, `std_key`,
         `effective_from`) COMMENT '维度唯一 + 起始时间，保证任一时刻命中至多一条',
    KEY `idx_param_map_lookup` (`provenance_id`, `operation_code`, `std_key`) COMMENT '常用查询：按来源+操作+标准键命中当前生效',
    KEY `idx_param_map_rev` (`provenance_id`, `operation_code`, `provider_param_name`) COMMENT '反查：已知平台参数名回溯标准键',
    KEY `idx_param_map_updated` (`updated_at`) COMMENT '按更新时间的增量/审计查询'
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='（Registry·Expr）API 参数映射：标准键 → 供应商参数名（键名层；时间片生效；使用 code）';


/* ====================================================================
 * 3) 源敏感：字段能力（该来源在特定字段上的可用操作/限制）
 *    - 声明允许的表达式操作（ops）及各操作的约束（长度/大小写/范围类型与边界等）
 *    - 为渲染与校验提供“可/不可”的先验信息
 *    - 维度唯一 + 时间片：[from,to)
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `reg_prov_expr_capability`
(
    `id`                          BIGINT UNSIGNED NOT NULL COMMENT '主键（雪花/发号器），库内标识',
    `provenance_id`               BIGINT UNSIGNED NOT NULL COMMENT '来源 ID（逻辑外键 → reg_provenance.id）',

    `scope`                       VARCHAR(8)      NOT NULL DEFAULT 'SOURCE' COMMENT '作用域 code：SOURCE/TASK；用于灰度与试点',
    `task_type`                   VARCHAR(32)     NULL COMMENT '任务类型（可空）：HARVEST/UPDATE/BACKFILL...',
    `task_type_key`               VARCHAR(16) GENERATED ALWAYS AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：将 NULL 归一为 ALL，稳定维度',

    `field_key`                   VARCHAR(64)     NOT NULL COMMENT '统一内部字段键（逻辑外键 → reg_expr_field_dict.field_key）；决定本条能力约束作用对象',

    `effective_from`              TIMESTAMP(6)    NOT NULL COMMENT '生效起（含）；UTC',
    `effective_to`                TIMESTAMP(6)    NULL COMMENT '生效止（不含）；UTC；NULL=当前仍生效',
    CONSTRAINT `ck_cap_range` CHECK (`effective_to` IS NULL OR `effective_to` > `effective_from`),

    `ops`                         JSON            NOT NULL COMMENT '允许的表达式操作集合（大写 code 数组，如 ["TERM","IN","RANGE","EXISTS","TOKEN"]）',
    `negatable_ops`               JSON            NULL COMMENT '允许取反的操作子集；NULL 表示与 ops 相同；例如仅对 TERM 允许 NOT',
    `supports_not`                TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否全局允许 NOT：1=允许，0=不允许（总开关）',

    `term_matches`                JSON            NULL COMMENT 'TERM 可用匹配策略（大写 code 数组）：["PHRASE","EXACT","ANY"]',
    `term_case_sensitive_allowed` TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'TERM 是否允许大小写敏感：1=允许，0=不允许',
    `term_allow_blank`            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'TERM 是否允许空白（例如某些平台无法接受空串）',
    `term_min_len`                INT             NOT NULL DEFAULT 0 COMMENT 'TERM 最小长度；0 表示不限制',
    `term_max_len`                INT             NOT NULL DEFAULT 0 COMMENT 'TERM 最大长度；0 表示不限制',
    `term_pattern`                VARCHAR(255)    NULL COMMENT 'TERM 值正则（可选）：用于限定字符集/格式',

    `in_max_size`                 INT             NOT NULL DEFAULT 0 COMMENT 'IN 集合最大项数；0 表示不限制；为 0 以外值时用于防御过长的 IN 列表',
    `in_case_sensitive_allowed`   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'IN 是否允许大小写敏感：1=允许，0=不允许',

    `range_kind_code`             VARCHAR(16)     NOT NULL DEFAULT 'NONE' COMMENT '范围类型 code（字典 reg_range_kind）：NONE/DATE/DATETIME/NUMBER；决定 RANG E 值的类型',
    `range_allow_open_start`      TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否允许省略起始值（-∞, x]）：1=允许',
    `range_allow_open_end`        TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否允许省略结束值（[x, +∞)）：1=允许',
    `range_allow_closed_at_infty` TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否允许无穷端闭区间（如 (-∞, x]）：1=允许；一般设置为 0',

    `date_min`                    DATE            NULL COMMENT 'DATE 范围的最小日期（UTC）',
    `date_max`                    DATE            NULL COMMENT 'DATE 范围的最大日期（UTC）',
    `datetime_min`                TIMESTAMP(6)    NULL COMMENT 'DATETIME 范围的最小时间（UTC，微秒精度）',
    `datetime_max`                TIMESTAMP(6)    NULL COMMENT 'DATETIME 范围的最大时间（UTC，微秒精度）',
    `number_min`                  DECIMAL(38, 12) NULL COMMENT 'NUMBER 范围的最小数值（高精度）',
    `number_max`                  DECIMAL(38, 12) NULL COMMENT 'NUMBER 范围的最大数值（高精度）',

    `exists_supported`            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否支持 EXISTS 操作：1=支持，0=不支持',
    `token_kinds`                 JSON            NULL COMMENT '允许的 token 种类（小写字符串数组，如 ["owner","pmcid"]）',
    `token_value_pattern`         VARCHAR(255)    NULL COMMENT 'token 值的正则约束（可选）',

    `record_remarks`              JSON            NULL COMMENT '审计备注：JSON 数组',
    `version`                     BIGINT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`                  VARBINARY(16)   NULL COMMENT '最后一次写入来源 IP（二进制）',
    `created_at`                  TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（UTC）',
    `created_by`                  BIGINT          NULL COMMENT '创建人 ID',
    `created_by_name`             VARCHAR(64)     NULL COMMENT '创建人名称',
    `updated_at`                  TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间（UTC）',
    `updated_by`                  BIGINT          NULL COMMENT '更新人 ID',
    `updated_by_name`             VARCHAR(64)     NULL COMMENT '更新人名称',
    `deleted`                     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除：0=未删，1=已删',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cap__dim_from`
        (`provenance_id`, `scope`, `task_type_key`, `field_key`,
         `effective_from`) COMMENT '维度唯一 + 起始时间，保证同一时刻命中唯一配置',
    KEY `idx_cap_updated` (`updated_at`) COMMENT '增量/审计查询',
    KEY `idx_cap_lookup` (`provenance_id`, `field_key`) COMMENT '常用查询：按来源+字段查看能力'
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='（Registry·Expr）字段能力（源敏感）：允许的操作与限制声明；用于校验与渲染前置判断';


/* ====================================================================
 * 4) 源敏感：渲染规则（Expr.Atom → query 片段或 params）
 *    - 将表达式原子（字段 + 操作 + 匹配/取反 + 值类型）渲染为 query 片段或 params
 *    - 与 API 参数名映射（param_map）解耦：此处不写具体的供应商参数名，仅产出“标准键/模板变量”
 *    - 维度唯一 + 时间片：[from,to)；引入归一化生成列消除 NULL 歧义
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `reg_prov_expr_render_rule`
(
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键（雪花/发号器），库内标识',
    `provenance_id`   BIGINT UNSIGNED NOT NULL COMMENT '来源 ID（逻辑外键 → reg_provenance.id）',

    `scope`           VARCHAR(8)      NOT NULL DEFAULT 'SOURCE' COMMENT '作用域 code：SOURCE/TASK；配合 task_type 做灰度',
    `task_type`       VARCHAR(32)     NULL COMMENT '任务类型（可空）：HARVEST/UPDATE/BACKFILL...',
    `task_type_key`   VARCHAR(16) GENERATED ALWAYS AS (IFNULL(CAST(`task_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：将 NULL 归一化为 ALL',

    `field_key`       VARCHAR(64)     NOT NULL COMMENT '统一内部字段键（逻辑外键 → reg_expr_field_dict.field_key）',
    `op_code`         VARCHAR(16)     NOT NULL COMMENT '表达式操作符 code（字典 reg_expr_op）：TERM/IN/RANGE/EXISTS/TOKEN',
    `match_type_code` VARCHAR(16)     NULL COMMENT '匹配策略 code（字典 reg_match_type；TERM 专用）：PHRASE/EXACT/ANY；NULL 表示不区分',
    `negated`         TINYINT(1)      NULL COMMENT '是否取反：1=NOT，0=非 NOT；NULL=不区分（参与归一化生成列）',
    `value_type_code` VARCHAR(16)     NULL COMMENT '值类型 code（用于 RANGE 等）：STRING/DATE/DATETIME/NUMBER；NULL=不区分',
    `emit_type_code`  VARCHAR(8)      NOT NULL DEFAULT 'QUERY' COMMENT '渲染产出类型（字典 reg_emit_type）：QUERY=拼接 query 片段；PARAMS=生成标准参数集合',

    `match_type_key`  VARCHAR(16) GENERATED ALWAYS AS (IFNULL(`match_type_code`, 'ANY')) STORED COMMENT '归一化：将 match_type_code 的 NULL 归一化为 ANY，用于维度唯一与查询',
    `negated_key`     CHAR(3) GENERATED ALWAYS AS (IFNULL(IF(`negated` = 1, 'T', 'F'), 'ANY')) STORED COMMENT '归一化：将 negated 的 NULL 归一化为 ANY（T/F/ANY）',
    `value_type_key`  VARCHAR(16) GENERATED ALWAYS AS (IFNULL(`value_type_code`, 'ANY')) STORED COMMENT '归一化：将 value_type_code 的 NULL 归一化为 ANY',

    `effective_from`  TIMESTAMP(6)    NOT NULL COMMENT '生效起（含）；UTC',
    `effective_to`    TIMESTAMP(6)    NULL COMMENT '生效止（不含）；UTC；NULL=当前仍生效',
    CONSTRAINT `ck_render_range` CHECK (`effective_to` IS NULL OR `effective_to` > `effective_from`),

    `template`        TEXT            NULL COMMENT '当 emit=QUERY：用于渲染 query 片段的模板；支持 helper（如 {{q v}}/{{lower ...}}）',
    `item_template`   TEXT            NULL COMMENT '当 emit=QUERY 且 IN/集合：集合项的单项模板（可选）',
    `joiner`          VARCHAR(32)     NULL COMMENT '当 emit=QUERY 且 IN/集合：集合项连接符（如 " OR " / " AND "）',
    `wrap_group`      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '当 emit=QUERY 且 IN/集合：是否使用括号包裹整个集合表达式',

    `params`          JSON            NULL COMMENT '当 emit=PARAMS：渲染生成的“标准键/模板变量”JSON（不直接写供应商参数名）；例如 {"from":"from","to":"to"}',
    `fn_code`         VARCHAR(64)     NULL COMMENT '模板级渲染函数 code（字典 reg_transform 的子集或扩展）：如 PUBMED_DATETYPE 等（不做值级转换）',

    `record_remarks`  JSON            NULL COMMENT '审计备注：JSON 数组',
    `version`         BIGINT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`      VARBINARY(16)   NULL COMMENT '最后一次写入来源 IP（二进制）',
    `created_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（UTC）',
    `created_by`      BIGINT          NULL COMMENT '创建人 ID',
    `created_by_name` VARCHAR(64)     NULL COMMENT '创建人名称',
    `updated_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间（UTC）',
    `updated_by`      BIGINT          NULL COMMENT '更新人 ID',
    `updated_by_name` VARCHAR(64)     NULL COMMENT '更新人名称',
    `deleted`         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除：0=未删，1=已删',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_render__dim_from`
        (`provenance_id`, `scope`, `task_type_key`, `field_key`, `op_code`, `match_type_key`, `negated_key`,
         `value_type_key`, `emit_type_code`, `effective_from`) COMMENT '维度唯一 + 起始时间；通过归一化列消除 NULL 歧义',
    KEY `idx_render_updated` (`updated_at`) COMMENT '增量/审计查询',
    KEY `idx_render_lookup` (`provenance_id`, `field_key`, `op_code`) COMMENT '常用查询：按来源+字段+操作命中规则'
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='（Registry·Expr）渲染规则（源敏感）：Expr.Atom → query 片段或 params；与参数名映射解耦；时间片生效';


-- =====================================================================
-- 结束
-- =====================================================================

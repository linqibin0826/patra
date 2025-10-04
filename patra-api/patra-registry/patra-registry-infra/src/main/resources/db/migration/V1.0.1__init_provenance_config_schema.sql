/* ====================================================================
 * 表：reg_provenance —— 数据来源登记（Provenance Registry）
 * 领域：Registry · Provenance Config
 * 语义：登记外部数据源（如 PubMed / Crossref）的基础信息，作为所有 reg_prov_* 配置的外键根。
 * 关键点：
 *  - 稳定键：provenance_code（唯一、跨环境稳定）；
 *  - 默认参数：base_url_default / timezone_default / docs_url；
 *  - 生命周期：lifecycle_status_code（字典 lifecycle_status）；is_active 作为读侧过滤开关；
 * 关系：被所有 reg_prov_* 表以 provenance_id 引用。
 * 索引：uk_reg_provenance_code 唯一键；常规按 code 查询。
 * 用法：
 *  - 写侧：新增来源 → 获取 id → 写入 reg_prov_* 维度配置；
 *  - 读侧：按 provenance_code 解析出 id 后，再按维度选择“当前生效”配置。
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_provenance`;
CREATE TABLE `reg_provenance`
(
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键：数据来源唯一标识；被所有 reg_prov_* 配置表通过 provenance_id 引用',
    `provenance_code`       VARCHAR(64)     NOT NULL COMMENT '来源编码：全局唯一、稳定（如 pubmed / crossref），用于程序内查找与约束',
    `provenance_name`       VARCHAR(128)    NOT NULL COMMENT '来源名称：人类可读名称（如 PubMed / Crossref）',
    `base_url_default`      VARCHAR(512)    NULL COMMENT '默认基础URL：当未在 HTTP 策略中覆盖时，用于端点 path 的拼接',
    `timezone_default`      VARCHAR(64)     NOT NULL DEFAULT 'UTC' COMMENT '默认时区（IANA TZ，如 UTC/Asia/Shanghai）：窗口计算/展示的缺省时区',
    `docs_url`              VARCHAR(512)    NULL COMMENT '官方文档/说明链接：便于排障或核对 API 用法',
    `is_active`             TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用该来源：1=启用；0=停用（应用读取时可据此过滤）',
    `lifecycle_status_code` VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status)：生命周期：读侧仅取 ACTIVE/有效项',

    -- BaseDO（统一审计字段）
    `record_remarks`        JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`            TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`            BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`       VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`            TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`            BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`       VARCHAR(100)    NULL COMMENT '更新人姓名',
    `version`               BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`            VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    -- 字典以编码关联：lifecycle_status 使用 sys_dict_item.item_code（type=lifecycle_status）
    UNIQUE KEY `uk_reg_provenance_code` (`provenance_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='数据来源登记：记录外部数据源（Provenance）的基础信息，作为所有配置的外键根。';

/* ====================================================================
 * 表：reg_prov_window_offset_cfg —— 时间窗口与增量指针配置（Window & Offset）
 * 领域：Registry · Provenance Config
 * 语义：配置采集任务如何切分时间窗口与推进增量指针（DATE/ID/COMPOSITE），支持回看/重叠/水位滞后等策略。
 * 维度唯一：uk_reg_prov_window_offset_cfg__dim_from (provenance_id, operation_type_key, effective_from)。
 * 常用查询：同端点定义按生效区间取 0..1 条“当前生效”；产出 SLIDING/CALENDAR 窗口与指针推进规则。
 * 写侧策略：灰度切换“先加新、后关旧”；写前做区间交集预检。
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_prov_window_offset_cfg`;
CREATE TABLE `reg_prov_window_offset_cfg`
(
    `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键：时间窗口与增量指针配置记录ID',
    `provenance_id`           BIGINT UNSIGNED NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `operation_type`          VARCHAR(32)     NULL COMMENT '采集类型（ALL/HARVEST/UPDATE/BACKFILL）',

    `effective_from`          TIMESTAMP(6)    NOT NULL COMMENT '生效起始（含）；不重叠由应用层保证',
    `effective_to`            TIMESTAMP(6)    NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    /* 窗口定义 */
    `window_mode_code`        VARCHAR(16)     NOT NULL COMMENT 'DICT CODE(type=window_mode)：窗口模式 SLIDING/CALENDAR',
    `window_size_value`       INT             NOT NULL DEFAULT 1 COMMENT '窗口长度的数值部分，如 1/7/30；单位见 window_size_unit',
    `window_size_unit_code`   VARCHAR(16)     NOT NULL COMMENT 'DICT CODE(type=time_unit)：窗口长度单位 SECOND/MINUTE/HOUR/DAY',
    `calendar_align_to`       VARCHAR(16)     NULL COMMENT 'CALENDAR 模式对齐粒度（示例：HOUR/DAY/WEEK/MONTH）',
    `lookback_value`          INT             NULL COMMENT '回看长度数值：用于补偿延迟数据（与 lookback_unit 搭配）',
    `lookback_unit_code`      VARCHAR(16)     NULL COMMENT 'DICT CODE(type=time_unit)：回看长度单位 SECOND/MINUTE/HOUR/DAY',
    `overlap_value`           INT             NULL COMMENT '窗口重叠长度数值：相邻窗口之间的重叠（迟到兜底）',
    `overlap_unit_code`       VARCHAR(16)     NULL COMMENT 'DICT CODE(type=time_unit)：窗口重叠单位 SECOND/MINUTE/HOUR/DAY',
    `watermark_lag_seconds`   INT             NULL COMMENT '水位滞后秒数：处理乱序/迟到数据时允许的最大延迟',

    /* 增量指针定义 */
    `offset_type_code`        VARCHAR(16)     NOT NULL COMMENT 'DICT CODE(type=offset_type)：指针类型 DATE/ID/COMPOSITE',
    `offset_field_name`       VARCHAR(128)    NULL COMMENT '指针字段名或 JSONPath（如 DATE 字段名/ID 字段名/复合键主维度）',
    `offset_date_format`      VARCHAR(64)     NULL COMMENT 'DATE 指针格式/语义：如 ISO_INSTANT、epochMillis、YYYYMMDD',
    `default_date_field_name` VARCHAR(64)     NULL COMMENT '默认增量日期字段名（如 PubMed: EDAT/PDAT/MHDA；Crossref: indexed-date）',
    `max_ids_per_window`      INT             NULL COMMENT '单窗口最多可处理的ID数量（超过则需二次切窗）',
    `max_window_span_seconds` INT             NULL COMMENT '单窗口最大跨度（秒）：过长窗口将被强制切分',

    /* 生成列 */
    `operation_type_key`      VARCHAR(16) AS (IFNULL(CAST(`operation_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：operation_type 标准化；为空取 ALL',
    `lifecycle_status_code`   VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status)：生命周期',

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
    UNIQUE KEY `uk_reg_prov_window_offset_cfg__dim_from` (`provenance_id`, `operation_type_key`, `effective_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='时间窗口与增量指针配置：定义采集任务如何切分时间窗口与推进增量指针（DATE/ID/COMPOSITE）；支持 SOURCE/TASK 作用域。';


/* ====================================================================
 * 表：reg_prov_pagination_cfg —— 分页与游标配置（Pagination）
 * 领域：Registry · Provenance Config
 * 语义：配置页码/游标/令牌/滚动分页的参数与响应提取规则（JSONPath/XPath）。
 * 维度唯一：uk_reg_prov_pagination_cfg__dim_from (provenance_id, operation_type_key, effective_from)。
 * 常用查询：同端点定义按生效区间取 0..1 条“当前生效”；端点级覆盖优先生效。
 * 写侧策略：灰度切换“先加新、后关旧”；写前做区间交集预检。
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_prov_pagination_cfg`;
CREATE TABLE `reg_prov_pagination_cfg`
(
    `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键：分页与游标配置记录ID',
    `provenance_id`           BIGINT UNSIGNED NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `operation_type`          VARCHAR(32)     NULL COMMENT '采集类型（ALL/HARVEST/UPDATE/BACKFILL）',

    `effective_from`          TIMESTAMP(6)    NOT NULL COMMENT '生效起始（含）',
    `effective_to`            TIMESTAMP(6)    NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    `pagination_mode_code`    VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=pagination_mode)：分页模式 PAGE_NUMBER/CURSOR/TOKEN/SCROLL',
    `page_size_value`         INT             NULL COMMENT '每页大小：PAGE_NUMBER/SCROLL 模式常用；空表示使用应用默认',
    `max_pages_per_execution` INT             NULL COMMENT '单次执行最多翻页数：防止深翻造成高成本',
    `sort_field_param_name`   VARCHAR(128)    NULL COMMENT '排序字段名',
    `sorting_direction`       tinyint                  default 1 NOT NULL COMMENT '排序顺序：0=DESC 1=ASC',

    `operation_type_key`      VARCHAR(16) AS (IFNULL(CAST(`operation_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：operation_type 标准化；为空取 ALL',
    `lifecycle_status_code`   VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status)：生命周期：读侧仅取 ACTIVE/有效项',

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
    -- 字典以编码关联：pagination_mode_code/lifecycle_status_code 使用 item_code
    UNIQUE KEY `uk_reg_prov_pagination_cfg__dim_from` (`provenance_id`, `operation_type_key`, `effective_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='分页与游标配置：配置页码/游标/令牌式分页的参数与响应提取规则；支持 SOURCE/TASK 作用域。';


/* ====================================================================
 * 表：reg_prov_http_cfg —— HTTP 策略配置（HTTP Policy）
 * 领域：Registry · Provenance Config
 * 语义：配置 base_url 覆盖、默认 Headers、超时、TLS、代理、Retry-After 策略、幂等键等 HTTP 行为参数。
 * 维度唯一：uk_reg_prov_http_cfg__dim_from (provenance_id, operation_type_key, effective_from)。
 * 常用查询：与端点/分页/批量/重试/限流联合，作为执行合同的一部分；尊重服务端 Retry-After（可设上限）。
 * 写侧策略：灰度切换“先加新、后关旧”；写前做区间交集预检。
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_prov_http_cfg`;
CREATE TABLE `reg_prov_http_cfg`
(
    `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键：HTTP 策略配置记录ID',
    `provenance_id`           BIGINT UNSIGNED NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `operation_type`          VARCHAR(32)     NULL COMMENT '采集类型（ALL/HARVEST/UPDATE/BACKFILL）',

    `effective_from`          TIMESTAMP(6)    NOT NULL COMMENT '生效起始（含）',
    `effective_to`            TIMESTAMP(6)    NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    `default_headers_json`    JSON            NULL COMMENT '默认HTTP Headers（JSON），运行时与请求头合并',
    `timeout_connect_millis`  INT             NULL COMMENT '连接超时（毫秒）：建立 TCP/SSL 连接的超时时间',
    `timeout_read_millis`     INT             NULL COMMENT '读取超时（毫秒）：读取响应主体的超时时间',
    `timeout_total_millis`    INT             NULL COMMENT '总超时（毫秒）：一次请求从开始到结束的最大耗时',
    `tls_verify_enabled`      TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否校验 TLS 证书：1=开启；0=关闭（仅测试环境）',
    `proxy_url_value`         VARCHAR(512)    NULL COMMENT '代理地址：如 http://user:pass@host:port 或 socks5://host:port',
    `retry_after_policy_code` VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=retry_after_policy)：对服务端 Retry-After 的处理策略 (IGNORE/RESPECT/CLAMP)',
    `retry_after_cap_millis`  INT             NULL COMMENT '当选择 RESPECT/CLAMP 时的最大等待上限（毫秒）',
    `idempotency_header_name` VARCHAR(64)     NULL COMMENT '幂等性 Header 名称（如 Idempotency-Key），用于避免重复提交',
    `idempotency_ttl_seconds` INT             NULL COMMENT '幂等性键过期时间（秒），仅客户端/服务端支持时有效',

    `operation_type_key`      VARCHAR(16) AS (IFNULL(CAST(`operation_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：operation_type 标准化；为空取 ALL',
    `lifecycle_status_code`   VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status)：生命周期：读侧仅取 ACTIVE/有效项',

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
    -- 字典以编码关联：retry_after_policy_code/lifecycle_status_code 使用 item_code
    UNIQUE KEY `uk_reg_prov_http_cfg__dim_from` (`provenance_id`, `operation_type_key`, `effective_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='HTTP 策略配置：基础URL/请求头/超时/代理/Retry-After/幂等等策略；支持 SOURCE/TASK 作用域。';


/* ====================================================================
 * 表：reg_prov_batching_cfg —— 批量抓取与请求成型（Batching & Shaping）
 * 领域：Registry · Provenance Config
 * 语义：定义详情批量请求的成型方式（ids 参数名、最大批量、并发度、压缩策略、背压等）。
 * 维度唯一：uk_reg_prov_batching_cfg__dim_from (provenance_id, operation_type_key, effective_from)。
 * 用法：与端点定义结合（ids_param_name）以生成批量详情请求；可设应用侧并发与背压策略。
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_prov_batching_cfg`;
CREATE TABLE `reg_prov_batching_cfg`
(
    `id`                             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键：批量抓取与请求成型配置记录ID',
    `provenance_id`                  BIGINT UNSIGNED NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `operation_type`                 VARCHAR(32)     NULL COMMENT '采集类型（ALL/HARVEST/UPDATE/BACKFILL）',

    `effective_from`                 TIMESTAMP(6)    NOT NULL COMMENT '生效起始（含）',
    `effective_to`                   TIMESTAMP(6)    NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    `detail_fetch_batch_size`        INT             NULL COMMENT '单次详情抓取的批大小（条数），为空则由应用使用默认',
    `ids_param_name`                 VARCHAR(64)     NULL COMMENT '批详情请求中，ID 列表的参数名；为空则由端点或应用决定',
    `ids_join_delimiter`             VARCHAR(8)      NULL     DEFAULT ',' COMMENT 'ID 列表拼接的分隔符（如 , 或 +）',
    `max_ids_per_request`            INT             NULL COMMENT '每个 HTTP 请求允许携带的 ID 最大数量（硬上限）',

    `operation_type_key`             VARCHAR(16) AS (IFNULL(CAST(`operation_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：operation_type 标准化；为空取 ALL',
    `lifecycle_status_code`          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status)：生命周期：读侧仅取 ACTIVE/有效项',

    -- BaseDO（统一审计字段）
    `record_remarks`                 JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`                     TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`                     BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`                VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`                     TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`                     BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`                VARCHAR(100)    NULL COMMENT '更新人姓名',
    `version`                        BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`                     VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`                        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_batching_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    -- 字典以编码关联：payload_compress_strategy_code/backpressure_strategy_code/lifecycle_status_code 使用 sys_dict_item.item_code
    UNIQUE KEY `uk_reg_prov_batching_cfg__dim_from` (`provenance_id`, `operation_type_key`, `effective_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='批量抓取与请求成型配置：控制详情批量、ID 拼接、并行与背压、请求模板与压缩等；支持 SOURCE/TASK 作用域。';


/* ====================================================================
 * 表：reg_prov_retry_cfg —— 重试与退避（Retry & Backoff）
 * 领域：Registry · Provenance Config
 * 语义：为来源/TASK 配置重试次数、退避策略（固定/指数+抖动）、熔断阈值与冷却等，细化到 HTTP/网络错误类别。
 * 维度唯一：uk_reg_prov_retry_cfg__dim_from (provenance_id, operation_type_key, effective_from)。
 * 用法：与 HTTP 的 Retry-After 策略共同作用；对 429/5xx/网络错误/客户端异常等进行控制。
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_prov_retry_cfg`;
CREATE TABLE `reg_prov_retry_cfg`
(
    `id`                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键：重试与退避配置记录ID',
    `provenance_id`            BIGINT UNSIGNED NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `operation_type`           VARCHAR(32)     NULL COMMENT '采集类型（ALL/HARVEST/UPDATE/BACKFILL）',

    `effective_from`           TIMESTAMP(6)    NOT NULL COMMENT '生效起始（含）',
    `effective_to`             TIMESTAMP(6)    NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    `max_retry_times`          INT             NULL COMMENT '最大重试次数：为空则使用应用默认；0 表示不重试',
    `backoff_policy_type_code` VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=backoff_policy_type)：退避策略',
    `initial_delay_millis`     INT             NULL COMMENT '首个重试的延迟（毫秒）',
    `max_delay_millis`         INT             NULL COMMENT '单次重试的最大延迟（毫秒）',
    `exp_multiplier_value`     DOUBLE          NULL COMMENT '指数退避的乘数因子（如 2.0）',
    `jitter_factor_ratio`      DOUBLE          NULL COMMENT '抖动系数（0~1）：随机扰动的幅度',
    `retry_http_status_json`   JSON            NULL COMMENT '可重试的 HTTP 状态码列表（JSON 数组，如 [429,500,503]）',
    `giveup_http_status_json`  JSON            NULL COMMENT '直接放弃的 HTTP 状态码列表（JSON 数组）',
    `retry_on_network_error`   TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '网络错误是否重试：1=重试；0=不重试',
    `circuit_break_threshold`  INT             NULL COMMENT '断路器阈值：连续失败次数达到该值后短路',
    `circuit_cooldown_millis`  INT             NULL COMMENT '断路器冷却时间（毫秒）：过后允许半开探测',

    `operation_type_key`       VARCHAR(16) AS (IFNULL(CAST(`operation_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：operation_type 标准化；为空取 ALL',
    `lifecycle_status_code`    VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status)：生命周期：读侧仅取 ACTIVE/有效项',

    -- BaseDO（统一审计字段）
    `record_remarks`           JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`               TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`               BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`          VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`               TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`               BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`          VARCHAR(100)    NULL COMMENT '更新人姓名',
    `version`                  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`               VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`                  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_retry_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    -- 字典以编码关联：backoff_policy_type_code/lifecycle_status_code 使用 item_code
    UNIQUE KEY `uk_reg_prov_retry_cfg__dim_from` (`provenance_id`, `operation_type_key`, `effective_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='重试与退避配置：定义可重试次数、退避与抖动、网络错误策略以及断路器阈值与冷却时间；支持 SOURCE/TASK 作用域。';


/* ====================================================================
 * 表：reg_prov_rate_limit_cfg —— 限流与并发（Rate Limit & Concurrency）
 * 领域：Registry · Provenance Config
 * 语义：配置 QPS/令牌桶、突发容量、最大并发、按密钥/端点/IP/任务等粒度，及平滑/自适应等。
 * 用法：结合重试与 HTTP；可尊重服务端 Rate Header（Retry-After、RateLimit-*）进行平滑。
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_prov_rate_limit_cfg`;
CREATE TABLE `reg_prov_rate_limit_cfg`
(
    `id`                            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键：限流与并发配置记录ID',
    `provenance_id`                 BIGINT UNSIGNED NOT NULL COMMENT '外键：所属来源ID → reg_provenance(id)',
    `operation_type`                VARCHAR(32)     NULL COMMENT '采集类型（ALL/HARVEST/UPDATE/BACKFILL）',

    `effective_from`                TIMESTAMP(6)    NOT NULL COMMENT '生效起始（含）',
    `effective_to`                  TIMESTAMP(6)    NULL COMMENT '生效结束（不含）；NULL 表示长期有效',

    `max_concurrent_requests`       INT             NULL COMMENT '全局并发请求上限（连接/请求数），为空表示默认',
    `per_credential_qps_limit`      INT             NULL COMMENT '按密钥的 QPS 上限：多把密钥时可分摊流量',
    `operation_type_key`            VARCHAR(16) AS (IFNULL(CAST(`operation_type` AS CHAR), 'ALL')) STORED COMMENT '生成列：operation_type 标准化；为空取 ALL',
    `lifecycle_status_code`         VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status)：生命周期：读侧仅取 ACTIVE/有效项',

    -- BaseDO（统一审计字段）
    `record_remarks`                JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`                    TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by`                    BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`               VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`                    TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by`                    BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`               VARCHAR(100)    NULL COMMENT '更新人姓名',
    `version`                       BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`                    VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`                       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_rate_limit_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    -- 字典以编码关联：bucket_granularity_lifecycle_status_code 使用 sys_dict_item.item_code
    UNIQUE KEY `uk_reg_prov_rate_limit_cfg__dim_from` (`provenance_id`, `operation_type_key`, `effective_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='限流与并发配置：配置 QPS/突发/并发与桶粒度（全局/按密钥/按端点），可结合服务端速率响应头进行自适应；支持 SOURCE/TASK 作用域。';

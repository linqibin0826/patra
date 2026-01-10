/* ====================================================================
 * 表: reg_provenance - Provenance 注册表 (数据源目录)
 * 领域: Registry - Provenance 配置
 * 语义: 外部数据源的基本信息目录 (例如: PubMed, Crossref),
 *       作为所有 reg_prov_* 配置表引用的根实体
 * 要点:
 *  - 稳定键: provenance_code (唯一,跨环境稳定)
 *  - 默认值: base_url_default / timezone_default / docs_url
 *  - 生命周期: lifecycle_status_code (字典 lifecycle_status); is_active 是读侧过滤开关
 * 关系: 被所有 reg_prov_* 表通过 provenance_id 引用
 * 索引: uk_reg_provenance_code (唯一); 常按 code 查询
 * 用法:
 *  - 写入: 添加数据源 -> 获取 id -> 写入维度配置到 reg_prov_*
 *  - 读取: 通过 provenance_code 解析 id,然后按维度选择当前有效配置
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_provenance`;
CREATE TABLE `reg_provenance`
(
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键;唯一数据源标识符;被 reg_prov_* 通过 provenance_id 引用',
    `provenance_code`       VARCHAR(64)     NOT NULL COMMENT '数据源编码: 全局唯一,稳定 (例如 pubmed/crossref);用于查找和约束',
    `provenance_name`       VARCHAR(128)    NOT NULL COMMENT '数据源显示名称 (例如 PubMed / Crossref)',
    `base_url_default`      VARCHAR(512)    NULL COMMENT '默认基础URL: 未被HTTP策略覆盖时用于与端点路径拼接',
    `timezone_default`      VARCHAR(64)     NOT NULL DEFAULT 'UTC' COMMENT '默认时区 (IANA TZ, 例如 UTC/Asia/Shanghai): 窗口计算/显示的默认时区',
    `docs_url`              VARCHAR(512)    NULL COMMENT '官方文档/参考URL: 帮助故障排查和API验证',
    `is_active`             TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '数据源是否活动: 1=活动, 0=非活动 (读侧可按此过滤)',
    `lifecycle_status_code` VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT '字典编码(type=lifecycle_status): 读侧仅使用ACTIVE/有效状态',

    -- BaseDO (通用审计字段)
    `record_remarks`        JSON            NULL COMMENT '审计备注: JSON数组, 例如 [{"time":"2025-08-18 15:00:00","by":"操作员","note":"..."}]',
    `created_at`            TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`            BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`       VARCHAR(100)    NULL COMMENT '创建人姓名/登录名快照',
    `updated_at`            TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间 (UTC)',
    `updated_by`            BIGINT UNSIGNED NULL COMMENT '最后更新人ID',
    `updated_by_name`       VARCHAR(100)    NULL COMMENT '最后更新人姓名/登录名快照',
    `version`               BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `ip_address`            VARBINARY(16)   NULL COMMENT '请求者IP (二进制, IPv4/IPv6)',
    `deleted_at`            TIMESTAMP(6)    NULL DEFAULT NULL COMMENT '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)',
    PRIMARY KEY (`id`),
    -- 字典编码: lifecycle_status_code 使用 sys_dict_item.item_code (type=lifecycle_status)
    UNIQUE KEY `uk_reg_provenance_code` (`provenance_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='Provenance注册表: 记录外部数据源作为所有 reg_prov_* 配置引用的根实体';

/* ====================================================================
 * 表: reg_prov_window_offset_cfg - 窗口与偏移策略
 * 领域: Registry - Provenance 配置
 * 语义: 配置任务如何分割时间窗口和推进增量偏移 (DATE/ID/COMPOSITE),
 *       支持回溯/重叠/水位延迟策略
 * 维度唯一性: uk_reg_prov_window_offset_cfg__dim_from (provenance_id, operation_type_key, effective_from)
 * 常见查询: 按有效区间为每个端点定义选择最多一行"当前有效"配置
 * 写入策略: 灰度切换采用"先添加新配置,再关闭旧配置";写入前预检查重叠区间
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_prov_window_offset_cfg`;
CREATE TABLE `reg_prov_window_offset_cfg`
(
    `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键: 窗口与偏移配置ID',
    `provenance_id`           BIGINT UNSIGNED NOT NULL COMMENT '外键: 所属数据源ID -> reg_provenance(id)',
    `operation_type`          VARCHAR(32)     NOT NULL DEFAULT 'ALL' COMMENT '操作类型 (ALL/HARVEST/UPDATE/BACKFILL)',

    `effective_from`          TIMESTAMP(6)    NOT NULL COMMENT '生效开始时间 (包含); 应用层确保不重叠',
    `effective_to`            TIMESTAMP(6)    NULL COMMENT '生效结束时间 (不包含); NULL表示开放式',

    /* 窗口定义 */
    `window_mode_code`        VARCHAR(16)     NOT NULL COMMENT '字典编码(type=window_mode): SLIDING 或 CALENDAR',
    `window_size_value`       INT             NOT NULL DEFAULT 1 COMMENT '窗口长度数值部分, 例如 1/7/30; 单位见 window_size_unit_code',
    `window_size_unit_code`   VARCHAR(16)     NOT NULL COMMENT '字典编码(type=time_unit): SECOND/MINUTE/HOUR/DAY',
    `calendar_align_to`       VARCHAR(16)     NULL COMMENT 'CALENDAR 模式的对齐粒度, 例如 HOUR/DAY/WEEK/MONTH',
    `lookback_value`          INT             NULL COMMENT '回溯长度值: 补偿延迟数据 (与 lookback_unit_code 配对)',
    `lookback_unit_code`      VARCHAR(16)     NULL COMMENT '字典编码(type=time_unit): 回溯长度单位',
    `overlap_value`           INT             NULL COMMENT '相邻窗口间重叠长度值',
    `overlap_unit_code`       VARCHAR(16)     NULL COMMENT '字典编码(type=time_unit): 窗口重叠单位',
    `watermark_lag_seconds`   INT             NULL COMMENT '水位延迟秒数: 乱序数据允许的最大延迟',

    /* 偏移定义 */
    `offset_type_code`        VARCHAR(16)     NOT NULL COMMENT '字典编码(type=offset_type): DATE/ID/COMPOSITE',
    `offset_field_key`        VARCHAR(64)     NULL COMMENT '统一字段键 (std_key, 类FK到 reg_expr_field_dict.field_key) 用于偏移跟踪',
    `offset_date_format`      VARCHAR(64)     NULL COMMENT 'DATE 偏移格式/语义: ISO_INSTANT/epochMillis/YYYYMMDD 等',
    `window_date_field_key`   VARCHAR(64)     NULL COMMENT '统一日期字段键 (std_key) 用于 DATE/COMPOSITE 时的时间切片',
    `max_ids_per_window`      INT             NULL COMMENT '每窗口最大ID数; 超出时分割窗口',
    `max_window_span_seconds` INT             NULL COMMENT '每窗口最大跨度 (秒): 过长窗口将被分割',

    `lifecycle_status_code`   VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT '字典编码(type=lifecycle_status): 生命周期',

    -- BaseDO (通用审计字段)
    `record_remarks`          JSON            NULL COMMENT '审计备注: JSON数组, 例如 [{"time":"2025-08-18 15:00:00","by":"操作员","note":"..."}]',
    `created_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`              BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`         VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间 (UTC)',
    `updated_by`              BIGINT UNSIGNED NULL COMMENT '最后更新人ID',
    `updated_by_name`         VARCHAR(100)    NULL COMMENT '最后更新人姓名',
    `version`                 BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `ip_address`              VARBINARY(16)   NULL COMMENT '请求者IP (二进制, IPv4/IPv6)',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_window_offset_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    UNIQUE KEY `uk_reg_prov_window_offset_cfg__dim_from` (`provenance_id`, `operation_type`, `effective_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='窗口与偏移配置: 如何分割窗口和推进偏移 (DATE/ID/COMPOSITE)';


/* ====================================================================
 * 表: reg_prov_pagination_cfg - 分页与游标
 * 领域: Registry - Provenance 配置
 * 语义: 配置页码/游标/令牌/滚动分页参数和响应提取规则 (JSONPath/XPath)
 * 维度唯一性: uk_reg_prov_pagination_cfg__dim_from (provenance_id, operation_type_key, effective_from)
 * 常见查询: 每个端点定义最多一行当前有效配置; 端点级覆盖优先
 * 写入策略: 灰度切换采用"先添加新配置,再关闭旧配置";写入前预检查重叠区间
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_prov_pagination_cfg`;
CREATE TABLE `reg_prov_pagination_cfg`
(
    `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键: 分页与游标配置ID',
    `provenance_id`           BIGINT UNSIGNED NOT NULL COMMENT '外键: 所属数据源ID -> reg_provenance(id)',
    `operation_type`          VARCHAR(32)     NOT NULL DEFAULT 'ALL' COMMENT '操作类型 (ALL/HARVEST/UPDATE/BACKFILL)',

    `effective_from`          TIMESTAMP(6)    NOT NULL COMMENT '生效开始时间 (包含)',
    `effective_to`            TIMESTAMP(6)    NULL COMMENT '生效结束时间 (不包含); NULL表示开放式',

    `pagination_mode_code`    VARCHAR(32)     NOT NULL COMMENT '字典编码(type=pagination_mode): PAGE_NUMBER/CURSOR/TOKEN/SCROLL',
    `page_size_value`         INT             NULL COMMENT 'PAGE_NUMBER/SCROLL 的页面大小; NULL使用应用默认值',
    `max_pages_per_execution` INT             NULL COMMENT '单次执行最大页数,限制深度分页',
    `sort_field_param_name`   VARCHAR(128)    NULL COMMENT '排序字段名',
    `sorting_direction`       TINYINT                  DEFAULT 1 NOT NULL COMMENT '排序方向: 0=DESC, 1=ASC',

    `lifecycle_status_code`   VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT '字典编码(type=lifecycle_status): 生命周期; 读侧仅使用ACTIVE',

    -- BaseDO (通用审计字段)
    `record_remarks`          JSON            NULL COMMENT '审计备注: JSON数组, 例如 [{"time":"2025-08-18 15:00:00","by":"操作员","note":"..."}]',
    `created_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`              BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`         VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间 (UTC)',
    `updated_by`              BIGINT UNSIGNED NULL COMMENT '最后更新人ID',
    `updated_by_name`         VARCHAR(100)    NULL COMMENT '最后更新人姓名',
    `version`                 BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `ip_address`              VARBINARY(16)   NULL COMMENT '请求者IP (二进制, IPv4/IPv6)',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_pagination_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    -- 字典编码: pagination_mode_code/lifecycle_status_code 使用 sys_dict_item.item_code
    UNIQUE KEY `uk_reg_prov_pagination_cfg__dim_from` (`provenance_id`, `operation_type`, `effective_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='分页与游标配置: 参数和响应提取; 支持 SOURCE/TASK 范围';


/* ====================================================================
 * 表: reg_prov_http_cfg - HTTP 策略
 * 领域: Registry - Provenance 配置
 * 语义: 配置 base_url 覆盖、默认请求头、超时、TLS、代理、Retry-After 处理、幂等性等
 * 维度唯一性: uk_reg_prov_http_cfg__dim_from (provenance_id, operation_type_key, effective_from)
 * 用法: 与端点/分页/批处理/重试/限流组合形成执行契约; 遵守 Retry-After (有上限)
 * 写入策略: 灰度切换采用"先添加新配置,再关闭旧配置";写入前预检查重叠区间
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_prov_http_cfg`;
CREATE TABLE `reg_prov_http_cfg`
(
    `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键: HTTP 策略配置ID',
    `provenance_id`           BIGINT UNSIGNED NOT NULL COMMENT '外键: 所属数据源ID -> reg_provenance(id)',
    `operation_type`          VARCHAR(32)     NOT NULL DEFAULT 'ALL' COMMENT '操作类型 (ALL/HARVEST/UPDATE/BACKFILL)',

    `effective_from`          TIMESTAMP(6)    NOT NULL COMMENT '生效开始时间 (包含)',
    `effective_to`            TIMESTAMP(6)    NULL COMMENT '生效结束时间 (不包含); NULL表示开放式',

    `default_headers_json`    JSON            NULL COMMENT '默认 HTTP 请求头 (JSON); 与运行时请求头合并',
    `timeout_connect_millis`  INT             NULL COMMENT '连接超时 (毫秒): 建立 TCP/SSL 连接',
    `timeout_read_millis`     INT             NULL COMMENT '读取超时 (毫秒): 读取响应体',
    `timeout_total_millis`    INT             NULL COMMENT '总超时 (毫秒): 请求端到端上限',
    `tls_verify_enabled`      TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '验证 TLS 证书: 1=开启, 0=关闭 (仅测试)',
    `proxy_url_value`         VARCHAR(512)    NULL COMMENT '代理 URL: 例如 http://user:pass@host:port 或 socks5://host:port',
    `retry_after_policy_code` VARCHAR(32)     NOT NULL COMMENT '字典编码(type=retry_after_policy): IGNORE/RESPECT/CLAMP',
    `retry_after_cap_millis`  INT             NULL COMMENT '使用 RESPECT/CLAMP 时的最大等待上限 (毫秒)',
    `idempotency_header_name` VARCHAR(64)     NULL COMMENT '幂等性请求头名称 (例如 Idempotency-Key) 以避免重复提交',
    `idempotency_ttl_seconds` INT             NULL COMMENT '幂等性键 TTL (秒); 仅在支持时有效',

    `lifecycle_status_code`   VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT '字典编码(type=lifecycle_status): 生命周期; 读侧仅使用ACTIVE',

    -- BaseDO (通用审计字段)
    `record_remarks`          JSON            NULL COMMENT '审计备注: JSON数组, 例如 [{"time":"2025-08-18 15:00:00","by":"操作员","note":"..."}]',
    `created_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`              BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`         VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间 (UTC)',
    `updated_by`              BIGINT UNSIGNED NULL COMMENT '最后更新人ID',
    `updated_by_name`         VARCHAR(100)    NULL COMMENT '最后更新人姓名',
    `version`                 BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `ip_address`              VARBINARY(16)   NULL COMMENT '请求者IP (二进制, IPv4/IPv6)',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_http_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    -- 字典编码: retry_after_policy_code/lifecycle_status_code 使用 sys_dict_item.item_code
    UNIQUE KEY `uk_reg_prov_http_cfg__dim_from` (`provenance_id`, `operation_type`, `effective_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='HTTP 策略配置: 基础 URL/请求头/超时/代理/Retry-After/幂等性; 支持 SOURCE/TASK 范围';


/* ====================================================================
 * 表: reg_prov_batching_cfg - 批处理与请求塑形
 * 领域: Registry - Provenance 配置
 * 语义: 定义如何塑形批量详情请求 (id 参数名、最大批量大小、并发、
 *       压缩、背压等)
 * 维度唯一性: uk_reg_prov_batching_cfg__dim_from (provenance_id, operation_type_key, effective_from)
 * 用法: 与端点定义 (ids_param_name) 组合生成批量详情请求; 可设置应用侧并发/背压
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_prov_batching_cfg`;
CREATE TABLE `reg_prov_batching_cfg`
(
    `id`                             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键: 批处理与塑形配置ID',
    `provenance_id`                  BIGINT UNSIGNED NOT NULL COMMENT '外键: 所属数据源ID -> reg_provenance(id)',
    `operation_type`                 VARCHAR(32)     NOT NULL DEFAULT 'ALL' COMMENT '操作类型 (ALL/HARVEST/UPDATE/BACKFILL)',

    `effective_from`                 TIMESTAMP(6)    NOT NULL COMMENT '生效开始时间 (包含)',
    `effective_to`                   TIMESTAMP(6)    NULL COMMENT '生效结束时间 (不包含); NULL表示开放式',

    `detail_fetch_batch_size`        INT             NULL COMMENT '每次详情获取的批量大小 (行数); NULL使用应用默认值',
    `ids_param_name`                 VARCHAR(64)     NULL COMMENT '批量详情请求中 ID 列表的参数名; NULL由端点/应用决定',
    `ids_join_delimiter`             VARCHAR(8)      NULL     DEFAULT ',' COMMENT '连接 ID 列表的分隔符 (例如 , 或 +)',
    `max_ids_per_request`            INT             NULL COMMENT '每个 HTTP 请求的 ID 硬上限',

    `lifecycle_status_code`          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT '字典编码(type=lifecycle_status): 生命周期; 读侧仅使用ACTIVE',

    -- BaseDO (通用审计字段)
    `record_remarks`                 JSON            NULL COMMENT '审计备注: JSON数组, 例如 [{"time":"2025-08-18 15:00:00","by":"操作员","note":"..."}]',
    `created_at`                     TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`                     BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`                VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`                     TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间 (UTC)',
    `updated_by`                     BIGINT UNSIGNED NULL COMMENT '最后更新人ID',
    `updated_by_name`                VARCHAR(100)    NULL COMMENT '最后更新人姓名',
    `version`                        BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `ip_address`                     VARBINARY(16)   NULL COMMENT '请求者IP (二进制, IPv4/IPv6)',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_batching_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    -- 字典编码: payload_compress_strategy_code/backpressure_strategy_code/lifecycle_status_code 使用 sys_dict_item.item_code
    UNIQUE KEY `uk_reg_prov_batching_cfg__dim_from` (`provenance_id`, `operation_type`, `effective_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='批处理与塑形配置: 详情批处理、ID 连接、并发/背压; 支持 SOURCE/TASK 范围';


/* ====================================================================
 * 表: reg_prov_retry_cfg - 重试与退避
 * 领域: Registry - Provenance 配置
 * 语义: 配置重试次数、退避策略 (固定/指数 + 抖动)、熔断器阈值/冷却时间,
 *       按错误类别 (HTTP/网络/客户端)
 * 维度唯一性: uk_reg_prov_retry_cfg__dim_from (provenance_id, operation_type_key, effective_from)
 * 用法: 与 HTTP Retry-After 策略配合; 控制 429/5xx/网络/客户端错误
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_prov_retry_cfg`;
CREATE TABLE `reg_prov_retry_cfg`
(
    `id`                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键: 重试与退避配置ID',
    `provenance_id`            BIGINT UNSIGNED NOT NULL COMMENT '外键: 所属数据源ID -> reg_provenance(id)',
    `operation_type`           VARCHAR(32)     NOT NULL DEFAULT 'ALL' COMMENT '操作类型 (ALL/HARVEST/UPDATE/BACKFILL)',

    `effective_from`           TIMESTAMP(6)    NOT NULL COMMENT '生效开始时间 (包含)',
    `effective_to`             TIMESTAMP(6)    NULL COMMENT '生效结束时间 (不包含); NULL表示开放式',

    `max_retry_times`          INT             NULL COMMENT '最大重试次数; NULL使用应用默认值; 0=禁用重试',
    `backoff_policy_type_code` VARCHAR(32)     NOT NULL COMMENT '字典编码(type=backoff_policy_type): 退避策略',
    `initial_delay_millis`     INT             NULL COMMENT '首次重试的初始延迟 (毫秒)',
    `max_delay_millis`         INT             NULL COMMENT '每次重试的最大延迟 (毫秒)',
    `exp_multiplier_value`     DOUBLE          NULL COMMENT '指数退避的乘数 (例如 2.0)',
    `jitter_factor_ratio`      DOUBLE          NULL COMMENT '抖动因子 (0~1): 随机性幅度',
    `retry_http_status_json`   JSON            NULL COMMENT '可重试的 HTTP 状态列表 (JSON数组, 例如 [429,500,503])',
    `giveup_http_status_json`  JSON            NULL COMMENT '放弃重试的 HTTP 状态列表 (JSON数组)',
    `retry_on_network_error`   TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '网络错误时重试: 1=是, 0=否',
    `circuit_break_threshold`  INT             NULL COMMENT '熔断器阈值: 连续失败次数触发熔断',
    `circuit_cooldown_millis`  INT             NULL COMMENT '熔断器冷却时间 (毫秒): 之后允许半开探测',

    `lifecycle_status_code`    VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT '字典编码(type=lifecycle_status): 生命周期; 读侧仅使用ACTIVE',

    -- BaseDO (通用审计字段)
    `record_remarks`           JSON            NULL COMMENT '审计备注: JSON数组, 例如 [{"time":"2025-08-18 15:00:00","by":"操作员","note":"..."}]',
    `created_at`               TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`               BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`          VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`               TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间 (UTC)',
    `updated_by`               BIGINT UNSIGNED NULL COMMENT '最后更新人ID',
    `updated_by_name`          VARCHAR(100)    NULL COMMENT '最后更新人姓名',
    `version`                  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `ip_address`               VARBINARY(16)   NULL COMMENT '请求者IP (二进制, IPv4/IPv6)',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_retry_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    -- 字典编码: backoff_policy_type_code/lifecycle_status_code 使用 sys_dict_item.item_code
    UNIQUE KEY `uk_reg_prov_retry_cfg__dim_from` (`provenance_id`, `operation_type`, `effective_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='重试与退避配置: 次数、退避/抖动、网络策略和熔断器设置; 支持 SOURCE/TASK 范围';


/* ====================================================================
 * 表: reg_prov_rate_limit_cfg - 限流与并发
 * 领域: Registry - Provenance 配置
 * 语义: 配置 QPS/令牌桶、突发容量、最大并发数,按 key/端点/IP/任务粒度,平滑/自适应等
 * 用法: 与重试和 HTTP 配合; 可遵守服务器限流头 (Retry-After, RateLimit-*) 进行平滑
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_prov_rate_limit_cfg`;
CREATE TABLE `reg_prov_rate_limit_cfg`
(
    `id`                            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键: 限流与并发配置ID',
    `provenance_id`                 BIGINT UNSIGNED NOT NULL COMMENT '外键: 所属数据源ID -> reg_provenance(id)',
    `operation_type`                VARCHAR(32)     NOT NULL DEFAULT 'ALL' COMMENT '操作类型 (ALL/HARVEST/UPDATE/BACKFILL)',

    `effective_from`                TIMESTAMP(6)    NOT NULL COMMENT '生效开始时间 (包含)',
    `effective_to`                  TIMESTAMP(6)    NULL COMMENT '生效结束时间 (不包含); NULL表示开放式',

    `max_concurrent_requests`       INT             NULL COMMENT '全局最大并发请求数 (连接数/请求数); NULL使用默认值',
    `per_credential_qps_limit`      INT             NULL COMMENT '每凭证/密钥的 QPS 上限; 跨多个密钥分布负载',
    `lifecycle_status_code`         VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT '字典编码(type=lifecycle_status): 生命周期; 读侧仅使用ACTIVE',

    -- BaseDO (通用审计字段)
    `record_remarks`                JSON            NULL COMMENT '审计备注: JSON数组, 例如 [{"time":"2025-08-18 15:00:00","by":"操作员","note":"..."}]',
    `created_at`                    TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`                    BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`               VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`                    TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间 (UTC)',
    `updated_by`                    BIGINT UNSIGNED NULL COMMENT '最后更新人ID',
    `updated_by_name`               VARCHAR(100)    NULL COMMENT '最后更新人姓名',
    `version`                       BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `ip_address`                    VARBINARY(16)   NULL COMMENT '请求者IP (二进制, IPv4/IPv6)',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_rate_limit_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    -- 字典编码: bucket_granularity_lifecycle_status_code 使用 sys_dict_item.item_code
    UNIQUE KEY `uk_reg_prov_rate_limit_cfg__dim_from` (`provenance_id`, `operation_type`, `effective_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='限流与并发配置: QPS/突发/并发数/粒度; 可适应服务器限流头';

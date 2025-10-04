/* ====================================================================
 * Table: reg_provenance  -  Provenance Registry (Source Catalog)
 * Domain: Registry  -  Provenance Config
 * Semantics: Catalog basic information for external data sources (e.g., PubMed, Crossref),
 *            serving as the root referenced by all reg_prov_* configuration tables.
 * Key points:
 *  - Stable key: provenance_code (unique, stable across environments)
 *  - Defaults: base_url_default / timezone_default / docs_url
 *  - Lifecycle: lifecycle_status_code (dict lifecycle_status); is_active is the read-side filter switch
 * Relations: Referenced by all reg_prov_* tables via provenance_id.
 * Indexes: uk_reg_provenance_code (unique); commonly queried by code.
 * Usage:
 *  - Write: add source -> obtain id -> write dimension configs into reg_prov_*
 *  - Read: resolve id by provenance_code, then pick the currently effective config per dimension.
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_provenance`;
CREATE TABLE `reg_provenance`
(
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key; unique source identifier; referenced by reg_prov_* via provenance_id',
    `provenance_code`       VARCHAR(64)     NOT NULL COMMENT 'Source code: global unique, stable (e.g., pubmed/crossref); used for lookups and constraints',
    `provenance_name`       VARCHAR(128)    NOT NULL COMMENT 'Source display name (e.g., PubMed / Crossref)',
    `base_url_default`      VARCHAR(512)    NULL COMMENT 'Default base URL: used when not overridden by HTTP policy to join with endpoint paths',
    `timezone_default`      VARCHAR(64)     NOT NULL DEFAULT 'UTC' COMMENT 'Default timezone (IANA TZ, e.g., UTC/Asia/Shanghai): default for window calc/display',
    `docs_url`              VARCHAR(512)    NULL COMMENT 'Official docs/reference URL: helps troubleshooting and API verification',
    `is_active`             TINYINT(1)      NOT NULL DEFAULT 1 COMMENT 'Whether this source is active: 1=active, 0=inactive (read side may filter by this)',
    `lifecycle_status_code` VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status): read side uses ACTIVE/valid only',

    -- BaseDO (common auditing fields)
    `record_remarks`        JSON            NULL COMMENT 'Audit notes: JSON array, e.g. [{"time":"2025-08-18 15:00:00","by":"Operator","note":"..."}]',
    `created_at`            TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Created at (UTC)',
    `created_by`            BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    `created_by_name`       VARCHAR(100)    NULL COMMENT 'Creator name/login snapshot',
    `updated_at`            TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last updated at (UTC)',
    `updated_by`            BIGINT UNSIGNED NULL COMMENT 'Last updater ID',
    `updated_by_name`       VARCHAR(100)    NULL COMMENT 'Last updater name/login snapshot',
    `version`               BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    `ip_address`            VARBINARY(16)   NULL COMMENT 'Requester IP (binary, IPv4/IPv6)',
    `deleted`               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete flag',
    PRIMARY KEY (`id`),
    -- Dictionary by code: lifecycle_status_code uses sys_dict_item.item_code (type=lifecycle_status)
    UNIQUE KEY `uk_reg_provenance_code` (`provenance_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='Provenance Registry: records external data sources as the root entity referenced by all reg_prov_* configurations';

/* ====================================================================
 * Table: reg_prov_window_offset_cfg  -  Windowing & Offset Policy
 * Domain: Registry  -  Provenance Config
 * Semantics: Configure how tasks segment time windows and advance incremental offsets (DATE/ID/COMPOSITE),
 *            supporting lookback/overlap/watermark lag strategies.
 * Dimension uniqueness: uk_reg_prov_window_offset_cfg__dim_from (provenance_id, operation_type_key, effective_from).
 * Common queries: choose at most one "currently effective" row per endpoint definition by the effective interval.
 * Write strategy: gray switch with "add new first, then close old"; pre-check overlapping intervals before write.
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_prov_window_offset_cfg`;
CREATE TABLE `reg_prov_window_offset_cfg`
(
    `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key: windowing & offset config id',
    `provenance_id`           BIGINT UNSIGNED NOT NULL COMMENT 'FK: owning source id -> reg_provenance(id)',
    `operation_type`          VARCHAR(32)     NULL COMMENT 'Operation type (ALL/HARVEST/UPDATE/BACKFILL)',

    `effective_from`          TIMESTAMP(6)    NOT NULL COMMENT 'Effective from (inclusive); non-overlap ensured by application',
    `effective_to`            TIMESTAMP(6)    NULL COMMENT 'Effective to (exclusive); NULL means open-ended',

    /* Window definition */
    `window_mode_code`        VARCHAR(16)     NOT NULL COMMENT 'DICT CODE(type=window_mode): SLIDING or CALENDAR',
    `window_size_value`       INT             NOT NULL DEFAULT 1 COMMENT 'Numeric part of window length, e.g., 1/7/30; unit in window_size_unit_code',
    `window_size_unit_code`   VARCHAR(16)     NOT NULL COMMENT 'DICT CODE(type=time_unit): SECOND/MINUTE/HOUR/DAY',
    `calendar_align_to`       VARCHAR(16)     NULL COMMENT 'Alignment granularity for CALENDAR mode, e.g., HOUR/DAY/WEEK/MONTH',
    `lookback_value`          INT             NULL COMMENT 'Lookback length value: compensate for late data (paired with lookback_unit_code)',
    `lookback_unit_code`      VARCHAR(16)     NULL COMMENT 'DICT CODE(type=time_unit): unit for lookback length',
    `overlap_value`           INT             NULL COMMENT 'Overlap length value between adjacent windows',
    `overlap_unit_code`       VARCHAR(16)     NULL COMMENT 'DICT CODE(type=time_unit): unit for window overlap',
    `watermark_lag_seconds`   INT             NULL COMMENT 'Watermark lag in seconds: max allowed lateness for out-of-order data',

    /* Offset definition */
    `offset_type_code`        VARCHAR(16)     NOT NULL COMMENT 'DICT CODE(type=offset_type): DATE/ID/COMPOSITE',
    `offset_field_name`       VARCHAR(128)    NULL COMMENT 'Offset field name or JSONPath (DATE/ID field or composite key primary dimension)',
    `offset_date_format`      VARCHAR(64)     NULL COMMENT 'DATE offset format/semantics: ISO_INSTANT/epochMillis/YYYYMMDD, etc.',
    `default_date_field_name` VARCHAR(64)     NULL COMMENT 'Default incremental date field (e.g., PubMed: EDAT/PDAT/MHDA; Crossref: indexed-date)',
    `max_ids_per_window`      INT             NULL COMMENT 'Max IDs per window; split window when exceeded',
    `max_window_span_seconds` INT             NULL COMMENT 'Max span per window (seconds): overly long windows will be split',

    /* Generated columns */
    `operation_type_key`      VARCHAR(16) AS (IFNULL(CAST(`operation_type` AS CHAR), 'ALL')) STORED COMMENT 'Normalization: treat NULL operation_type as ALL',
    `lifecycle_status_code`   VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status): lifecycle',

    -- BaseDO (common auditing fields)
    `record_remarks`          JSON            NULL COMMENT 'Audit notes: JSON array, e.g. [{"time":"2025-08-18 15:00:00","by":"Operator","note":"..."}]',
    `created_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Created at (UTC)',
    `created_by`              BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    `created_by_name`         VARCHAR(100)    NULL COMMENT 'Creator name',
    `updated_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last updated at (UTC)',
    `updated_by`              BIGINT UNSIGNED NULL COMMENT 'Last updater ID',
    `updated_by_name`         VARCHAR(100)    NULL COMMENT 'Last updater name',
    `version`                 BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    `ip_address`              VARBINARY(16)   NULL COMMENT 'Requester IP (binary, IPv4/IPv6)',
    `deleted`                 TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete flag',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_window_offset_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    UNIQUE KEY `uk_reg_prov_window_offset_cfg__dim_from` (`provenance_id`, `operation_type_key`, `effective_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='Windowing & Offset configuration: how to segment windows and advance offsets (DATE/ID/COMPOSITE)';


/* ====================================================================
 * Table: reg_prov_pagination_cfg  -  Pagination & Cursor
 * Domain: Registry  -  Provenance Config
 * Semantics: Configure page/cursor/token/scroll pagination params and response extraction rules (JSONPath/XPath).
 * Dimension uniqueness: uk_reg_prov_pagination_cfg__dim_from (provenance_id, operation_type_key, effective_from).
 * Common queries: at most one currently effective row per endpoint definition; endpoint-level overrides take precedence.
 * Write strategy: gray switch with "add new first, then close old"; pre-check overlapping intervals before write.
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_prov_pagination_cfg`;
CREATE TABLE `reg_prov_pagination_cfg`
(
    `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key: pagination & cursor config id',
    `provenance_id`           BIGINT UNSIGNED NOT NULL COMMENT 'FK: owning source id -> reg_provenance(id)',
    `operation_type`          VARCHAR(32)     NULL COMMENT 'Operation type (ALL/HARVEST/UPDATE/BACKFILL)',

    `effective_from`          TIMESTAMP(6)    NOT NULL COMMENT 'Effective from (inclusive)',
    `effective_to`            TIMESTAMP(6)    NULL COMMENT 'Effective to (exclusive); NULL means open-ended',

    `pagination_mode_code`    VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=pagination_mode): PAGE_NUMBER/CURSOR/TOKEN/SCROLL',
    `page_size_value`         INT             NULL COMMENT 'Page size for PAGE_NUMBER/SCROLL; NULL uses application default',
    `max_pages_per_execution` INT             NULL COMMENT 'Max pages per single execution to cap deep pagination',
    `sort_field_param_name`   VARCHAR(128)    NULL COMMENT 'Sort field name',
    `sorting_direction`       TINYINT                  DEFAULT 1 NOT NULL COMMENT 'Sort order: 0=DESC, 1=ASC',

    `operation_type_key`      VARCHAR(16) AS (IFNULL(CAST(`operation_type` AS CHAR), 'ALL')) STORED COMMENT 'Normalization: treat NULL operation_type as ALL',
    `lifecycle_status_code`   VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status): lifecycle; read side uses ACTIVE only',

    -- BaseDO (common auditing fields)
    `record_remarks`          JSON            NULL COMMENT 'Audit notes: JSON array, e.g. [{"time":"2025-08-18 15:00:00","by":"Operator","note":"..."}]',
    `created_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Created at (UTC)',
    `created_by`              BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    `created_by_name`         VARCHAR(100)    NULL COMMENT 'Creator name',
    `updated_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last updated at (UTC)',
    `updated_by`              BIGINT UNSIGNED NULL COMMENT 'Last updater ID',
    `updated_by_name`         VARCHAR(100)    NULL COMMENT 'Last updater name',
    `version`                 BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    `ip_address`              VARBINARY(16)   NULL COMMENT 'Requester IP (binary, IPv4/IPv6)',
    `deleted`                 TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete flag',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_pagination_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    -- Dictionaries by code: pagination_mode_code/lifecycle_status_code use sys_dict_item.item_code
    UNIQUE KEY `uk_reg_prov_pagination_cfg__dim_from` (`provenance_id`, `operation_type_key`, `effective_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='Pagination & cursor configuration: parameters and response extraction; supports SOURCE/TASK scope';


/* ====================================================================
 * Table: reg_prov_http_cfg  -  HTTP Policy
 * Domain: Registry  -  Provenance Config
 * Semantics: Configure base_url override, default headers, timeouts, TLS, proxy, Retry-After handling, idempotency, etc.
 * Dimension uniqueness: uk_reg_prov_http_cfg__dim_from (provenance_id, operation_type_key, effective_from).
 * Usage: combined with endpoint/pagination/batching/retry/rate-limit to form the execution contract; respect Retry-After (with cap).
 * Write strategy: gray switch with "add new first, then close old"; pre-check overlapping intervals before write.
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_prov_http_cfg`;
CREATE TABLE `reg_prov_http_cfg`
(
    `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key: HTTP policy config id',
    `provenance_id`           BIGINT UNSIGNED NOT NULL COMMENT 'FK: owning source id -> reg_provenance(id)',
    `operation_type`          VARCHAR(32)     NULL COMMENT 'Operation type (ALL/HARVEST/UPDATE/BACKFILL)',

    `effective_from`          TIMESTAMP(6)    NOT NULL COMMENT 'Effective from (inclusive)',
    `effective_to`            TIMESTAMP(6)    NULL COMMENT 'Effective to (exclusive); NULL means open-ended',

    `default_headers_json`    JSON            NULL COMMENT 'Default HTTP headers (JSON); merged with runtime request headers',
    `timeout_connect_millis`  INT             NULL COMMENT 'Connect timeout (ms): establishing TCP/SSL',
    `timeout_read_millis`     INT             NULL COMMENT 'Read timeout (ms): reading response body',
    `timeout_total_millis`    INT             NULL COMMENT 'Total timeout (ms): request end-to-end cap',
    `tls_verify_enabled`      TINYINT(1)      NOT NULL DEFAULT 1 COMMENT 'Verify TLS certificates: 1=on, 0=off (test only)',
    `proxy_url_value`         VARCHAR(512)    NULL COMMENT 'Proxy URL: e.g., http://user:pass@host:port or socks5://host:port',
    `retry_after_policy_code` VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=retry_after_policy): IGNORE/RESPECT/CLAMP',
    `retry_after_cap_millis`  INT             NULL COMMENT 'Max wait cap (ms) when RESPECT/CLAMP is used',
    `idempotency_header_name` VARCHAR(64)     NULL COMMENT 'Idempotency header name (e.g., Idempotency-Key) to avoid duplicate submissions',
    `idempotency_ttl_seconds` INT             NULL COMMENT 'Idempotency key TTL (seconds); effective only when supported',

    `operation_type_key`      VARCHAR(16) AS (IFNULL(CAST(`operation_type` AS CHAR), 'ALL')) STORED COMMENT 'Normalization: treat NULL operation_type as ALL',
    `lifecycle_status_code`   VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status): lifecycle; read side uses ACTIVE only',

    -- BaseDO (common auditing fields)
    `record_remarks`          JSON            NULL COMMENT 'Audit notes: JSON array, e.g. [{"time":"2025-08-18 15:00:00","by":"Operator","note":"..."}]',
    `created_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Created at (UTC)',
    `created_by`              BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    `created_by_name`         VARCHAR(100)    NULL COMMENT 'Creator name',
    `updated_at`              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last updated at (UTC)',
    `updated_by`              BIGINT UNSIGNED NULL COMMENT 'Last updater ID',
    `updated_by_name`         VARCHAR(100)    NULL COMMENT 'Last updater name',
    `version`                 BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    `ip_address`              VARBINARY(16)   NULL COMMENT 'Requester IP (binary, IPv4/IPv6)',
    `deleted`                 TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete flag',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_http_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    -- Dictionaries by code: retry_after_policy_code/lifecycle_status_code use sys_dict_item.item_code
    UNIQUE KEY `uk_reg_prov_http_cfg__dim_from` (`provenance_id`, `operation_type_key`, `effective_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='HTTP policy configuration: base URL/headers/timeouts/proxy/Retry-After/idempotency; supports SOURCE/TASK scope';


/* ====================================================================
 * Table: reg_prov_batching_cfg  -  Batching & Request Shaping
 * Domain: Registry  -  Provenance Config
 * Semantics: Define how to shape batched detail requests (ids parameter name, max batch size, concurrency,
 *            compression, backpressure, etc.).
 * Dimension uniqueness: uk_reg_prov_batching_cfg__dim_from (provenance_id, operation_type_key, effective_from).
 * Usage: Combined with endpoint definition (ids_param_name) to generate batched detail requests; may set app-side concurrency/backpressure.
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_prov_batching_cfg`;
CREATE TABLE `reg_prov_batching_cfg`
(
    `id`                             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key: batching & shaping config id',
    `provenance_id`                  BIGINT UNSIGNED NOT NULL COMMENT 'FK: owning source id -> reg_provenance(id)',
    `operation_type`                 VARCHAR(32)     NULL COMMENT 'Operation type (ALL/HARVEST/UPDATE/BACKFILL)',

    `effective_from`                 TIMESTAMP(6)    NOT NULL COMMENT 'Effective from (inclusive)',
    `effective_to`                   TIMESTAMP(6)    NULL COMMENT 'Effective to (exclusive); NULL means open-ended',

    `detail_fetch_batch_size`        INT             NULL COMMENT 'Batch size per detail fetch (rows); NULL uses application default',
    `ids_param_name`                 VARCHAR(64)     NULL COMMENT 'Parameter name for ID list in batch detail requests; NULL decided by endpoint/app',
    `ids_join_delimiter`             VARCHAR(8)      NULL     DEFAULT ',' COMMENT 'Delimiter to join ID list (e.g., , or +)',
    `max_ids_per_request`            INT             NULL COMMENT 'Hard cap of IDs per HTTP request',

    `operation_type_key`             VARCHAR(16) AS (IFNULL(CAST(`operation_type` AS CHAR), 'ALL')) STORED COMMENT 'Normalization: treat NULL operation_type as ALL',
    `lifecycle_status_code`          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status): lifecycle; read side uses ACTIVE only',

    -- BaseDO (common auditing fields)
    `record_remarks`                 JSON            NULL COMMENT 'Audit notes: JSON array, e.g. [{"time":"2025-08-18 15:00:00","by":"Operator","note":"..."}]',
    `created_at`                     TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Created at (UTC)',
    `created_by`                     BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    `created_by_name`                VARCHAR(100)    NULL COMMENT 'Creator name',
    `updated_at`                     TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last updated at (UTC)',
    `updated_by`                     BIGINT UNSIGNED NULL COMMENT 'Last updater ID',
    `updated_by_name`                VARCHAR(100)    NULL COMMENT 'Last updater name',
    `version`                        BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    `ip_address`                     VARBINARY(16)   NULL COMMENT 'Requester IP (binary, IPv4/IPv6)',
    `deleted`                        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete flag',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_batching_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    -- Dictionaries by code: payload_compress_strategy_code/backpressure_strategy_code/lifecycle_status_code use sys_dict_item.item_code
    UNIQUE KEY `uk_reg_prov_batching_cfg__dim_from` (`provenance_id`, `operation_type_key`, `effective_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='Batching & shaping configuration: detail batching, ID joining, concurrency/backpressure; supports SOURCE/TASK scope';


/* ====================================================================
 * Table: reg_prov_retry_cfg  -  Retry & Backoff
 * Domain: Registry  -  Provenance Config
 * Semantics: Configure retry attempts, backoff policy (fixed/exponential + jitter), circuit breaker threshold/cooldown,
 *            per error category (HTTP/network/client).
 * Dimension uniqueness: uk_reg_prov_retry_cfg__dim_from (provenance_id, operation_type_key, effective_from).
 * Usage: Works with HTTP Retry-After policy; controls 429/5xx/network/client errors.
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_prov_retry_cfg`;
CREATE TABLE `reg_prov_retry_cfg`
(
    `id`                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key: retry & backoff config id',
    `provenance_id`            BIGINT UNSIGNED NOT NULL COMMENT 'FK: owning source id -> reg_provenance(id)',
    `operation_type`           VARCHAR(32)     NULL COMMENT 'Operation type (ALL/HARVEST/UPDATE/BACKFILL)',

    `effective_from`           TIMESTAMP(6)    NOT NULL COMMENT 'Effective from (inclusive)',
    `effective_to`             TIMESTAMP(6)    NULL COMMENT 'Effective to (exclusive); NULL means open-ended',

    `max_retry_times`          INT             NULL COMMENT 'Max retry attempts; NULL uses application default; 0=disable retry',
    `backoff_policy_type_code` VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=backoff_policy_type): backoff strategy',
    `initial_delay_millis`     INT             NULL COMMENT 'Initial delay (ms) for the first retry',
    `max_delay_millis`         INT             NULL COMMENT 'Max delay (ms) per retry',
    `exp_multiplier_value`     DOUBLE          NULL COMMENT 'Multiplier for exponential backoff (e.g., 2.0)',
    `jitter_factor_ratio`      DOUBLE          NULL COMMENT 'Jitter factor (0~1): randomness amplitude',
    `retry_http_status_json`   JSON            NULL COMMENT 'Retryable HTTP status list (JSON array, e.g., [429,500,503])',
    `giveup_http_status_json`  JSON            NULL COMMENT 'Give-up HTTP status list (JSON array)',
    `retry_on_network_error`   TINYINT(1)      NOT NULL DEFAULT 1 COMMENT 'Retry on network errors: 1=yes, 0=no',
    `circuit_break_threshold`  INT             NULL COMMENT 'Circuit breaker threshold: consecutive failures to trip',
    `circuit_cooldown_millis`  INT             NULL COMMENT 'Circuit breaker cooldown (ms): allow half-open probe after',

    `operation_type_key`       VARCHAR(16) AS (IFNULL(CAST(`operation_type` AS CHAR), 'ALL')) STORED COMMENT 'Normalization: treat NULL operation_type as ALL',
    `lifecycle_status_code`    VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status): lifecycle; read side uses ACTIVE only',

    -- BaseDO (common auditing fields)
    `record_remarks`           JSON            NULL COMMENT 'Audit notes: JSON array, e.g. [{"time":"2025-08-18 15:00:00","by":"Operator","note":"..."}]',
    `created_at`               TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Created at (UTC)',
    `created_by`               BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    `created_by_name`          VARCHAR(100)    NULL COMMENT 'Creator name',
    `updated_at`               TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last updated at (UTC)',
    `updated_by`               BIGINT UNSIGNED NULL COMMENT 'Last updater ID',
    `updated_by_name`          VARCHAR(100)    NULL COMMENT 'Last updater name',
    `version`                  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    `ip_address`               VARBINARY(16)   NULL COMMENT 'Requester IP (binary, IPv4/IPv6)',
    `deleted`                  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete flag',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_retry_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    -- Dictionaries by code: backoff_policy_type_code/lifecycle_status_code use sys_dict_item.item_code
    UNIQUE KEY `uk_reg_prov_retry_cfg__dim_from` (`provenance_id`, `operation_type_key`, `effective_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='Retry & backoff configuration: attempts, backoff/jitter, network policy, and circuit breaker settings; supports SOURCE/TASK scope';


/* ====================================================================
 * Table: reg_prov_rate_limit_cfg  -  Rate Limiting & Concurrency
 * Domain: Registry  -  Provenance Config
 * Semantics: Configure QPS/token-bucket, burst capacity, max concurrency, by key/endpoint/IP/task granularity, smoothing/adaptive, etc.
 * Usage: combine with retry and HTTP; may respect server rate headers (Retry-After, RateLimit-*) for smoothing.
 * ==================================================================== */
DROP TABLE IF EXISTS `reg_prov_rate_limit_cfg`;
CREATE TABLE `reg_prov_rate_limit_cfg`
(
    `id`                            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key: rate limit & concurrency config id',
    `provenance_id`                 BIGINT UNSIGNED NOT NULL COMMENT 'FK: owning source id -> reg_provenance(id)',
    `operation_type`                VARCHAR(32)     NULL COMMENT 'Operation type (ALL/HARVEST/UPDATE/BACKFILL)',

    `effective_from`                TIMESTAMP(6)    NOT NULL COMMENT 'Effective from (inclusive)',
    `effective_to`                  TIMESTAMP(6)    NULL COMMENT 'Effective to (exclusive); NULL means open-ended',

    `max_concurrent_requests`       INT             NULL COMMENT 'Global max concurrent requests (connections/requests); NULL uses default',
    `per_credential_qps_limit`      INT             NULL COMMENT 'QPS cap per credential/key; distribute load across multiple keys',
    `operation_type_key`            VARCHAR(16) AS (IFNULL(CAST(`operation_type` AS CHAR), 'ALL')) STORED COMMENT 'Normalization: treat NULL operation_type as ALL',
    `lifecycle_status_code`         VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE(type=lifecycle_status): lifecycle; read side uses ACTIVE only',

    -- BaseDO (common auditing fields)
    `record_remarks`                JSON            NULL COMMENT 'Audit notes: JSON array, e.g. [{"time":"2025-08-18 15:00:00","by":"Operator","note":"..."}]',
    `created_at`                    TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Created at (UTC)',
    `created_by`                    BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    `created_by_name`               VARCHAR(100)    NULL COMMENT 'Creator name',
    `updated_at`                    TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last updated at (UTC)',
    `updated_by`                    BIGINT UNSIGNED NULL COMMENT 'Last updater ID',
    `updated_by_name`               VARCHAR(100)    NULL COMMENT 'Last updater name',
    `version`                       BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    `ip_address`                    VARBINARY(16)   NULL COMMENT 'Requester IP (binary, IPv4/IPv6)',
    `deleted`                       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete flag',

    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reg_prov_rate_limit_cfg__provenance` FOREIGN KEY (`provenance_id`) REFERENCES `reg_provenance` (`id`),
    -- Dictionaries by code: bucket_granularity_lifecycle_status_code use sys_dict_item.item_code
    UNIQUE KEY `uk_reg_prov_rate_limit_cfg__dim_from` (`provenance_id`, `operation_type_key`, `effective_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='Rate limiting & concurrency configuration: QPS/burst/concurrency/granularity; may adapt to server rate headers';

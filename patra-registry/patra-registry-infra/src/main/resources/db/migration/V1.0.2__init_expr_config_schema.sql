/* ====================================================================
 * 1) Global Unified Field Dictionary (source-agnostic)
 *    - Define unified semantics for internal fields; e.g., publish_date / ti / ab / tiab
 *    - Source-agnostic; only describes field data type/cardinality/exposability, etc.
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `reg_expr_field_dict`
(
    `id`               BIGINT UNSIGNED NOT NULL COMMENT 'Primary key (snowflake/sequence); internal identifier only; migrations do not rely on this value',
    `field_key`        VARCHAR(64)     NOT NULL COMMENT 'Unified internal field key: lowercase snake_case or agreed abbreviations, e.g., publish_date/ti/ab/tiab; globally unique, stable for configs/GitOps',
    `display_name`     VARCHAR(128)    NULL COMMENT 'Human-readable field name for console/visual config (optional)',
    `description`      VARCHAR(255)    NULL COMMENT 'Field description/constraints/exposure notes (optional)',

    `data_type_code`   VARCHAR(32)     NOT NULL COMMENT 'Data type code (dict reg_data_type): DATE/DATETIME/NUMBER/TEXT/KEYWORD/BOOLEAN/TOKEN; used for validation/rendering branches',
    `cardinality_code` VARCHAR(16)     NOT NULL DEFAULT 'SINGLE' COMMENT 'Cardinality code (dict reg_cardinality): SINGLE/MULTI; whether multiple values allowed',
    `exposable`        TINYINT(1)      NOT NULL DEFAULT 1 COMMENT 'Whether allowed to be exposed/used globally: 1=exposable, 0=hidden; decoupled from source-level capability',
    `is_date`          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Redundant flag: 1=date-like (helps UI/DateLens); typically consistent with DATE/DATETIME type',

    `record_remarks`   JSON            NULL COMMENT 'Audit notes: JSON array for change notes/reviews/Ops remarks',
    `version`          BIGINT          NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version (CAS) to avoid concurrent overwrite',
    `ip_address`       VARBINARY(16)   NULL COMMENT 'Last write source IP (binary, IPv4/IPv6) for audit/risk control',
    `created_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Created at (UTC, microsecond precision)',
    `created_by`       BIGINT          NULL COMMENT 'Creator ID (logical FK; user/system account)',
    `created_by_name`  VARCHAR(64)     NULL COMMENT 'Creator name/login snapshot',
    `updated_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last updated at (UTC, microsecond precision)',
    `updated_by`       BIGINT          NULL COMMENT 'Last updater ID (logical FK)',
    `updated_by_name`  VARCHAR(64)     NULL COMMENT 'Last updater name/login snapshot',
    `deleted`          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=active, 1=deleted; read side filters deleted=0',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_expr_field_key` (`field_key`) COMMENT 'Ensure global uniqueness of unified field key'
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='(Registry - Expr) Unified internal field dictionary (source-agnostic; SSOT for field semantics)';


/* ====================================================================
 * 2) Source-aware: API Parameter Name Mapping (std_key -> provider param)
 *    - Map unified semantic key (std_key, e.g., from/to/ti) to provider HTTP parameter names (e.g., mindate/maxdate/term)
 *    - Only responsible for key-name mapping; not request templates; value-level transform is declared via transform_code only
 *    - Dimension uniqueness + temporal slice: [from,to); read picks by NOW, taking one row by from DESC
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `reg_prov_api_param_map`
(
    `id`                  BIGINT UNSIGNED NOT NULL COMMENT 'Primary key (snowflake/sequence); internal identifier',
    `provenance_id`       BIGINT UNSIGNED NOT NULL COMMENT 'Source ID (logical FK -> reg_provenance.id) to distinguish providers',

    `operation_type`      VARCHAR(32)     NOT NULL DEFAULT 'ALL' COMMENT 'Task type: HARVEST/UPDATE/BACKFILL/SANDBOX/ALL; for task-level gray rollout',

    `operation_code`      VARCHAR(32)     NOT NULL COMMENT 'Endpoint operation code (dict reg_operation): SEARCH/DETAIL/LOOKUP..., consistent with endpoint execution contract',
    `std_key`             VARCHAR(64)     NOT NULL COMMENT 'Standard key (unified internal semantic key): e.g., from/to/ti/ab; typically produced during rendering',
    `provider_param_name` VARCHAR(64)     NOT NULL COMMENT 'Provider parameter name: concrete HTTP parameter, e.g., mindate/maxdate/term/retmax',
    `transform_code`      VARCHAR(64)     NULL COMMENT 'Optional: value-level transform code (dict reg_transform), e.g., TO_EXCLUSIVE_MINUS_1D',
    `notes`               JSON            NULL COMMENT 'Additional notes: JSON object for platform differences/boundaries',

    `effective_from`      TIMESTAMP(6)    NOT NULL COMMENT 'Effective from (inclusive); UTC; start of the temporal slice',
    `effective_to`        TIMESTAMP(6)    NULL COMMENT 'Effective to (exclusive); UTC; NULL means "still effective"',
    CONSTRAINT `ck_param_map_range` CHECK (`effective_to` IS NULL OR `effective_to` > `effective_from`),

    `record_remarks`      JSON            NULL COMMENT 'Audit notes: JSON array',
    `version`             BIGINT          NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    `ip_address`          VARBINARY(16)   NULL COMMENT 'Last write source IP (binary), IPv4/IPv6',
    `created_at`          TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Created at (UTC)',
    `created_by`          BIGINT          NULL COMMENT 'Creator ID',
    `created_by_name`     VARCHAR(64)     NULL COMMENT 'Creator name/login snapshot',
    `updated_at`          TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last updated at (UTC)',
    `updated_by`          BIGINT          NULL COMMENT 'Last updater ID',
    `updated_by_name`     VARCHAR(64)     NULL COMMENT 'Last updater name/login snapshot',
    `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete: 0=active, 1=deleted; read side filters',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_param_map__dim_from`
        (`provenance_id`, `operation_type`, `operation_code`, `std_key`,
         `effective_from`) COMMENT 'Dimension uniqueness + start time to ensure at most one match at any time'
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='(Registry - Expr) API parameter mapping: std_key -> provider parameter (key-name level; temporal)';


/* ====================================================================
 * 3) Source-aware: Field Capability (allowed operations/constraints)
 *    - Declare allowed expression operations (ops) and constraints per operation (length/case/range kind/bounds)
 *    - Provide prior knowledge for rendering and validation
 *    - Dimension uniqueness + temporal slice: [from,to)
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `reg_prov_expr_capability`
(
    `id`                          BIGINT UNSIGNED NOT NULL COMMENT 'Primary key (snowflake/sequence); internal identifier',
    `provenance_id`               BIGINT UNSIGNED NOT NULL COMMENT 'Source ID (logical FK -> reg_provenance.id)',

    `operation_type`              VARCHAR(32)     NOT NULL DEFAULT 'ALL' COMMENT 'Task type: HARVEST/UPDATE/BACKFILL/ALL',

    `field_key`                   VARCHAR(64)     NOT NULL COMMENT 'Unified internal field key (logical FK -> reg_expr_field_dict.field_key)',

    `effective_from`              TIMESTAMP(6)    NOT NULL COMMENT 'Effective from (inclusive); UTC',
    `effective_to`                TIMESTAMP(6)    NULL COMMENT 'Effective to (exclusive); UTC; NULL=open-ended',
    CONSTRAINT `ck_cap_range` CHECK (`effective_to` IS NULL OR `effective_to` > `effective_from`),

    `ops`                         JSON            NOT NULL COMMENT 'Allowed operation set (uppercase code array, e.g., ["TERM","IN","RANGE","EXISTS","TOKEN"])',
    `negatable_ops`               JSON            NULL COMMENT 'Subset of operations that allow NOT; NULL means same as ops; e.g., only TERM allows NOT',
    `supports_not`                TINYINT(1)      NOT NULL DEFAULT 1 COMMENT 'Whether NOT is globally allowed: 1=allowed, 0=forbidden',

    `term_matches`                JSON            NULL COMMENT 'TERM match strategies (uppercase codes): ["PHRASE","EXACT","ANY"]',
    `term_case_sensitive_allowed` TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'TERM case sensitivity supported: 1=yes, 0=no',
    `term_allow_blank`            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Whether TERM allows blanks/empty string',
    `term_min_len`                INT             NOT NULL DEFAULT 0 COMMENT 'TERM minimal length; 0 means unlimited',
    `term_max_len`                INT             NOT NULL DEFAULT 0 COMMENT 'TERM maximal length; 0 means unlimited',
    `term_pattern`                VARCHAR(255)    NULL COMMENT 'TERM value regex (optional) to constrain charset/format',

    `in_max_size`                 INT             NOT NULL DEFAULT 0 COMMENT 'Max element count for IN set; 0 means unlimited',
    `in_case_sensitive_allowed`   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'IN case sensitivity supported: 1=yes, 0=no',

    `range_kind_code`             VARCHAR(16)     NOT NULL DEFAULT 'NONE' COMMENT 'Range kind (dict reg_range_kind): NONE/DATE/DATETIME/NUMBER; determines RANGE value type',
    `range_allow_open_start`      TINYINT(1)      NOT NULL DEFAULT 1 COMMENT 'Allow open start (-inf, x]: 1=allowed',
    `range_allow_open_end`        TINYINT(1)      NOT NULL DEFAULT 1 COMMENT 'Allow open end [x, +inf): 1=allowed',
    `range_allow_closed_at_infty` TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Allow closed interval at infinity (e.g., (-inf, x]): 1=allowed; usually 0',

    `date_min`                    DATE            NULL COMMENT 'Minimum DATE bound (UTC)',
    `date_max`                    DATE            NULL COMMENT 'Maximum DATE bound (UTC)',
    `datetime_min`                TIMESTAMP(6)    NULL COMMENT 'Minimum DATETIME bound (UTC, microsecond precision)',
    `datetime_max`                TIMESTAMP(6)    NULL COMMENT 'Maximum DATETIME bound (UTC, microsecond precision)',
    `number_min`                  DECIMAL(38, 12) NULL COMMENT 'Minimum NUMBER bound (high precision)',
    `number_max`                  DECIMAL(38, 12) NULL COMMENT 'Maximum NUMBER bound (high precision)',

    `exists_supported`            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Whether EXISTS operator is supported: 1=supported, 0=not',
    `token_kinds`                 JSON            NULL COMMENT 'Allowed token kinds (lowercase string array, e.g., ["owner","pmcid"])',
    `token_value_pattern`         VARCHAR(255)    NULL COMMENT 'Regex constraint for token values (optional)',

    `record_remarks`              JSON            NULL COMMENT 'Audit notes: JSON array',
    `version`                     BIGINT          NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    `ip_address`                  VARBINARY(16)   NULL COMMENT 'Last write source IP (binary)',
    `created_at`                  TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Created at (UTC)',
    `created_by`                  BIGINT          NULL COMMENT 'Creator ID',
    `created_by_name`             VARCHAR(64)     NULL COMMENT 'Creator name',
    `updated_at`                  TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last updated at (UTC)',
    `updated_by`                  BIGINT          NULL COMMENT 'Last updater ID',
    `updated_by_name`             VARCHAR(64)     NULL COMMENT 'Last updater name',
    `deleted`                     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete: 0=active, 1=deleted',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cap__dim_from`
        (`provenance_id`, `operation_type`, `field_key`,
         `effective_from`) COMMENT 'Dimension uniqueness + start time to ensure a unique match at any time'
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='(Registry - Expr) Field capability (source-aware): allowed operations & constraints for validation/rendering';


/* ====================================================================
 * 4) Source-aware: Rendering Rules (Expr.Atom -> query fragment or params)
 *    - Render an expression atom (field + op + match/negation + value type) to a query fragment or params
 *    - Decoupled from API parameter naming (param_map): produce only standard keys/template variables here
 *    - Dimension uniqueness + temporal slice: [from,to); normalized generated columns eliminate NULL ambiguity
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `reg_prov_expr_render_rule`
(
    `id`              BIGINT UNSIGNED NOT NULL COMMENT 'Primary key (snowflake/sequence); internal identifier',
    `provenance_id`   BIGINT UNSIGNED NOT NULL COMMENT 'Source ID (logical FK -> reg_provenance.id)',

    `operation_type`  VARCHAR(32)     NOT NULL DEFAULT 'ALL' COMMENT 'Task type: HARVEST/UPDATE/BACKFILL/ALL',

    `field_key`       VARCHAR(64)     NOT NULL COMMENT 'Unified internal field key (logical FK -> reg_expr_field_dict.field_key)',
    `op_code`         VARCHAR(16)     NOT NULL COMMENT 'Expression operator code (dict reg_expr_op): TERM/IN/RANGE/EXISTS/TOKEN',
    `match_type_code` VARCHAR(16)     NULL COMMENT 'Match type code (dict reg_match_type; TERM only): PHRASE/EXACT/ANY; NULL=agnostic',
    `negated`         TINYINT(1)      NULL COMMENT 'Negation flag: 1=NOT, 0=non-NOT; NULL=agnostic (participates in normalized keys)',
    `value_type_code` VARCHAR(16)     NULL COMMENT 'Value type code (for RANGE etc.): STRING/DATE/DATETIME/NUMBER; NULL=agnostic',
    `emit_type_code`  VARCHAR(8)      NOT NULL DEFAULT 'QUERY' COMMENT 'Emission type (dict reg_emit_type): QUERY=emit query fragment; PARAMS=emit standard params',

    `match_type_key`  VARCHAR(16) GENERATED ALWAYS AS (IFNULL(`match_type_code`, 'ANY')) STORED COMMENT 'Normalization: NULL -> ANY for match_type_code',
    `negated_key`     CHAR(3) GENERATED ALWAYS AS (IFNULL(IF(`negated` = 1, 'T', 'F'), 'ANY')) STORED COMMENT 'Normalization: NULL -> ANY for negated (T/F/ANY)',
    `value_type_key`  VARCHAR(16) GENERATED ALWAYS AS (IFNULL(`value_type_code`, 'ANY')) STORED COMMENT 'Normalization: NULL -> ANY for value_type_code',

    `effective_from`  TIMESTAMP(6)    NOT NULL COMMENT 'Effective from (inclusive); UTC',
    `effective_to`    TIMESTAMP(6)    NULL COMMENT 'Effective to (exclusive); UTC; NULL=open-ended',
    CONSTRAINT `ck_render_range` CHECK (`effective_to` IS NULL OR `effective_to` > `effective_from`),

    `template`        TEXT            NULL COMMENT 'When emit=QUERY: template to render query fragment; supports helpers (e.g., {{q v}}/{{lower ...}})',
    `item_template`   TEXT            NULL COMMENT 'When emit=QUERY and op=IN: template for each item (optional)',
    `joiner`          VARCHAR(32)     NULL COMMENT 'When emit=QUERY and op=IN: joiner for items (e.g., " OR " / " AND ")',
    `wrap_group`      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'When emit=QUERY and op=IN: wrap entire group in parentheses',

    `params`          JSON            NULL COMMENT 'When emit=PARAMS: JSON of standard keys/template variables (do not use provider param names); e.g., {"from":"from","to":"to"}',
    `fn_code`         VARCHAR(64)     NULL COMMENT 'Template-level render function code (subset/extension of reg_transform); e.g., PUBMED_DATETYPE (not value-level transform)',

    `record_remarks`  JSON            NULL COMMENT 'Audit notes: JSON array',
    `version`         BIGINT          NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    `ip_address`      VARBINARY(16)   NULL COMMENT 'Last write source IP (binary)',
    `created_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Created at (UTC)',
    `created_by`      BIGINT          NULL COMMENT 'Creator ID',
    `created_by_name` VARCHAR(64)     NULL COMMENT 'Creator name',
    `updated_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last updated at (UTC)',
    `updated_by`      BIGINT          NULL COMMENT 'Last updater ID',
    `updated_by_name` VARCHAR(64)     NULL COMMENT 'Last updater name',
    `deleted`         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete: 0=active, 1=deleted',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_render__dim_from`
        (`provenance_id`, `operation_type`, `field_key`, `op_code`, `match_type_key`, `negated_key`,
         `value_type_key`, `emit_type_code`,
         `effective_from`) COMMENT 'Dimension uniqueness + start time; normalized keys eliminate NULL ambiguity'
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='(Registry - Expr) Rendering rules (source-aware): Expr.Atom -> query fragment or params; decoupled from param naming; temporal'

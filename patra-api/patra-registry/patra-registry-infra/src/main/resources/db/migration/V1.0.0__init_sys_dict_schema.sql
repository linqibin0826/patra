-- =====================================================================
-- Registry  -  System Dictionary Subdomain
-- Notes:
-- - DB objects and indexes only; no triggers, no physical foreign keys
-- - Includes common audit fields (BaseDO)
-- - Charset: utf8mb4; Collation: utf8mb4_0900_ai_ci; Engine: InnoDB
-- - Tables: sys_dict_type / sys_dict_item / sys_dict_item_alias
-- =====================================================================

/* ====================================================================
 * Table: sys_dict_type  -  Dictionary Types
 * Semantics: Metadata for a dictionary "type" (e.g., http_method, endpoint_usage).
 * Key points:
 *  - type_code is lowercase snake_case, stable across environments
 *  - allow_custom_items controls whether business may extend items under this type
 *  - is_system marks system built-in types
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS sys_dict_type
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key; internal unique identifier',
    type_code          VARCHAR(64)     NOT NULL COMMENT 'Type code: lowercase snake_case, e.g., http_method (stable key across envs)',
    type_name          VARCHAR(200)    NOT NULL COMMENT 'Type display name (human readable)',
    description        VARCHAR(500)    NULL COMMENT 'Description (usage, boundaries, notes)',
    allow_custom_items TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Allow custom items under this type: 1=yes, 0=no',
    is_system          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT 'System built-in: 1=system, 0=business-defined',
    reserved_json      JSON            NULL COMMENT 'Extra metadata (e.g., UI color/icon/sort strategy)',

    -- Auditing & Governance
    record_remarks     JSON            NULL COMMENT 'Change remarks: JSON array of history notes',
    created_at         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Created at (UTC)',
    created_by         BIGINT UNSIGNED NULL COMMENT 'Creator ID (logical FK)',
    created_by_name    VARCHAR(100)    NULL COMMENT 'Creator name/login snapshot',
    updated_at         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last updated at (UTC)',
    updated_by         BIGINT UNSIGNED NULL COMMENT 'Last updater ID (logical FK)',
    updated_by_name    VARCHAR(100)    NULL COMMENT 'Last updater name/login snapshot',
    version            BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version (CAS)',
    ip_address         VARBINARY(16)   NULL COMMENT 'Requester IP (binary, IPv4/IPv6)',
    deleted            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete: 0=active, 1=deleted (read side filters)',

    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_type__code (type_code) COMMENT 'Ensure type_code unique',
    CONSTRAINT chk_sys_dict_type__code_format CHECK (REGEXP_LIKE(type_code, '^[a-z0-9_]{1,64}$'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='System Dictionary - Type';


/* ====================================================================
 * Table: sys_dict_item  -  Dictionary Items
 * Semantics: Concrete values under a dictionary type (e.g., GET/POST for http_method).
 * Key points:
 *  - item_code is UPPER_SNAKE_CASE as a stable key
 *  - default_key is a generated column: equals type_id only when default AND enabled AND not deleted; enforces one default per type
 *  - enabled controls whether selectable in business logic
 *  - attributes_json for extensibility (no DDL needed for new attrs)
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS sys_dict_item
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key; internal unique identifier',
    type_id         BIGINT UNSIGNED NOT NULL COMMENT 'Parent type ID (logical FK -> sys_dict_type.id)',
    item_code       VARCHAR(64)     NOT NULL COMMENT 'Item code: stable key (UPPER_SNAKE_CASE), e.g., GET / PAGE_NUMBER',
    item_name       VARCHAR(200)    NOT NULL COMMENT 'Item display name (default language)',
    short_name      VARCHAR(64)     NULL COMMENT 'Short/abbreviated name (compact UI)',
    description     VARCHAR(500)    NULL COMMENT 'Notes/comments (semantics, boundaries, compatibility)',
    display_order   INT UNSIGNED    NOT NULL DEFAULT 100 COMMENT 'Display order (smaller shows first)',
    is_default      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Is default value (at most one per type; see unique on default_key)',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT 'Enabled: 1=yes, 0=disabled (excluded from selection when 0)',
    label_color     VARCHAR(32)     NULL COMMENT 'Label color (#AABBCC or semantic name)',
    icon_name       VARCHAR(64)     NULL COMMENT 'Icon name (for UI)',
    attributes_json JSON            NULL COMMENT 'Extended attributes (aliases/hints/compat flags, etc.)',

    -- Generated column: equals type_id only when default AND enabled AND not deleted; otherwise NULL
    default_key     BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE
             WHEN (is_default = 1 AND enabled = 1 AND deleted = 0) THEN type_id
             ELSE NULL END) STORED COMMENT 'Generated column used by unique key to enforce one default per type',

    -- Auditing & Governance
    record_remarks  JSON            NULL COMMENT 'Change remarks: JSON array',
    created_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Created at (UTC)',
    created_by      BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    created_by_name VARCHAR(100)    NULL COMMENT 'Creator name/login snapshot',
    updated_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last updated at (UTC)',
    updated_by      BIGINT UNSIGNED NULL COMMENT 'Last updater ID',
    updated_by_name VARCHAR(100)    NULL COMMENT 'Last updater name/login snapshot',
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version (CAS)',
    ip_address      VARBINARY(16)   NULL COMMENT 'Requester IP (binary, IPv4/IPv6)',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete: 0=active, 1=deleted',

    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_item__type_code (type_id, item_code) COMMENT 'item_code must be unique within the same type',
    UNIQUE KEY uk_sys_dict_item__default_per_type (default_key) COMMENT 'Via generated column to ensure one default per type',
    CONSTRAINT chk_sys_dict_item__code_format CHECK (REGEXP_LIKE(item_code, '^[A-Z0-9_]{1,64}$'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='System Dictionary - Item';


/* ====================================================================
 * Table: sys_dict_item_alias  -  External Mapping for Dictionary Items
 * Semantics: External aliases/codes for an item from other systems/vendors/legacy, for integration.
 * Key points:
 *  - (source_system, external_code) must be globally unique to avoid conflicts
 *  - Optional external_label stores the external display name
 *  - Typical source_system examples: pubmed/crossref/legacy_v1 (lowercase recommended)
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS sys_dict_item_alias
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key; internal unique identifier',
    item_id         BIGINT UNSIGNED NOT NULL COMMENT 'Item ID (logical FK -> sys_dict_item.id)',
    source_system   VARCHAR(64)     NOT NULL COMMENT 'Source system identifier, e.g., pubmed/crossref/legacy_v1 (lowercase snake-case or kebab-case recommended)',
    external_code   VARCHAR(128)    NOT NULL COMMENT 'External code/value (as mapping key)',
    external_label  VARCHAR(200)    NULL COMMENT 'External display name (optional)',
    notes           VARCHAR(500)    NULL COMMENT 'Notes/mapping explanation (differences, compatibility, source links, etc.)',

    -- Auditing & Governance
    record_remarks  JSON            NULL COMMENT 'Change remarks: JSON array',
    created_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Created at (UTC)',
    created_by      BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    created_by_name VARCHAR(100)    NULL COMMENT 'Creator name/login snapshot',
    updated_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last updated at (UTC)',
    updated_by      BIGINT UNSIGNED NULL COMMENT 'Last updater ID',
    updated_by_name VARCHAR(100)    NULL COMMENT 'Last updater name/login snapshot',
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version (CAS)',
    ip_address      VARBINARY(16)   NULL COMMENT 'Requester IP (binary, IPv4/IPv6)',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete: 0=active, 1=deleted',

    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_alias__src_code (source_system, external_code) COMMENT 'external_code must be unique within the same source_system',
    CONSTRAINT chk_dict_alias__src_format CHECK (REGEXP_LIKE(source_system, '^[a-z0-9_\-]{1,64}$'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='System Dictionary - External Mapping';

-- =====================================================================
-- End
-- =====================================================================

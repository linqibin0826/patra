/* ====================================================================
 * 迁移: V2 - Registry 完整种子数据
 * ====================================================================
 * 目的: 为所有支持的数据源 (PubMed, EPMC, CrossRef) 及其表达式配置
 *       提供统一的初始化数据
 *
 * 重写自: V1.1.0__seed_all_data.sql（MySQL）→ PostgreSQL 17
 * 主要变更:
 *   - INET6_ATON('192.168.1.10') → '\xC0A8010A'::bytea
 *   - JSON_ARRAY(...) → jsonb_build_array(...)
 *   - JSON_OBJECT(...) → jsonb_build_object(...)
 *   - NOW(6) → CURRENT_TIMESTAMP
 *   - TIMESTAMP('...') → TIMESTAMP '...'
 *   - boolean 字面量 0/1 → false/true
 *   - INSERT ... WHERE NOT EXISTS → INSERT ... ON CONFLICT DO NOTHING
 *   - 移除 schema 前缀 patra_registry.
 *
 * 结构:
 *   第0部分: 来源标准目录 (sys_reference_standard)
 *   第1部分: Provenance 基础数据 (reg_provenance, 窗口, 分页配置)
 *   第2部分: 统一字段字典 (reg_expr_field_dict)
 *   第3部分: PubMed 表达式配置
 *   第4部分: EPMC 表达式配置
 *   第5部分: CrossRef 表达式配置
 * ==================================================================== */


/* ====================================================================
 * 第0部分: 来源标准目录
 * 说明:
 *   - dict_type_code: 所属字典类型
 *   - is_canonical: 是否为该类型的规范标准（item_code 遵循的格式）
 *   - 每个字典类型只能有一个规范标准（通过 canonical_key 生成列约束）
 * ==================================================================== */

INSERT INTO sys_reference_standard
(id, dict_type_code, standard_code, standard_name, description, display_order, is_canonical, enabled,
 created_at, updated_at, version)
VALUES (
  900000000000000002,
  'country',
  'ISO_3166_1_ALPHA2',
  'ISO 3166-1 alpha-2',
  '国家代码两字母标准（平台规范标准，item_code 采用此格式）',
  10,
  true,
  true,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  0
)
ON CONFLICT (dict_type_code, standard_code) DO NOTHING;

INSERT INTO sys_reference_standard
(id, dict_type_code, standard_code, standard_name, description, display_order, is_canonical, enabled,
 created_at, updated_at, version)
VALUES (
  900000000000000003,
  'country',
  'NAME_EN',
  'English Name',
  '英文名称标准（用于名称型输入，需转换为 ISO 代码）',
  20,
  false,
  true,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  0
)
ON CONFLICT (dict_type_code, standard_code) DO NOTHING;


/* ====================================================================
 * 第1部分: Provenance 基础数据
 * ==================================================================== */

-- 1.1) 注册三个数据源: PubMed, EPMC, CrossRef
-- 注: 使用预分配的雪花 ID 格式，便于后续表引用
-- PUBMED_ID = 800000000000000001, EPMC_ID = 800000000000000002, CROSSREF_ID = 800000000000000003
INSERT INTO reg_provenance
(id, provenance_code, provenance_name, base_url_default, timezone_default,
 docs_url, is_active, lifecycle_status_code, record_remarks,
 created_at, created_by, created_by_name,
 updated_at, updated_by, updated_by_name, version, ip_address, deleted_at)
VALUES
    (800000000000000001, 'PUBMED', 'PubMed', 'https://eutils.ncbi.nlm.nih.gov/entrez/eutils/', 'UTC',
     'https://www.ncbi.nlm.nih.gov/books/NBK25501/', true, 'ACTIVE',
     jsonb_build_array(jsonb_build_object('time', '2025-09-01 10:30:00', 'by', '系统管理员', 'note', '初始化注册 PubMed 来源')),
     CURRENT_TIMESTAMP, 1001, '系统管理员', CURRENT_TIMESTAMP, 1001, '系统管理员', 0, '\xC0A8010A'::bytea, NULL),

    (800000000000000002, 'EPMC', 'Europe PMC', 'https://www.ebi.ac.uk/europepmc/webservices/rest/', 'UTC',
     'https://europepmc.org/RestfulWebService', true, 'ACTIVE',
     jsonb_build_array(jsonb_build_object('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', '初始化注册 Europe PMC 来源')),
     CURRENT_TIMESTAMP, 1001, '系统管理员', CURRENT_TIMESTAMP, 1001, '系统管理员', 0, '\xC0A8010A'::bytea, NULL),

    (800000000000000003, 'CROSSREF', 'Crossref', 'https://api.crossref.org/', 'UTC',
     'https://api.crossref.org/swagger-ui/index.html', true, 'ACTIVE',
     jsonb_build_array(jsonb_build_object('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', '初始化注册 Crossref 来源')),
     CURRENT_TIMESTAMP, 1001, '系统管理员', CURRENT_TIMESTAMP, 1001, '系统管理员', 0, '\xC0A8010A'::bytea, NULL)
ON CONFLICT (provenance_code) DO NOTHING;


-- 1.2) PubMed 窗口与偏移配置 (ValueObjectJpaEntity: 仅 id 字段)
INSERT INTO reg_prov_window_offset_cfg
(id, provenance_id, operation_type, effective_from, effective_to,
 window_mode_code, window_size_value, window_size_unit_code, calendar_align_to,
 lookback_value, lookback_unit_code, overlap_value, overlap_unit_code, watermark_lag_seconds,
 offset_type_code, offset_field_key, offset_date_format, window_date_field_key,
 max_ids_per_window, max_window_span_seconds, lifecycle_status_code)
VALUES
    (1, 800000000000000001, 'ALL', TIMESTAMP '2025-09-01 00:00:00.000000', NULL,
     'CALENDAR', 1, 'DAY', 'DAY',
     2, 'HOUR', 1, 'HOUR', 3600,
     'DATE', 'entrez_date', 'yyyy-MM-dd', 'entrez_date',
     50000, 2592000, 'ACTIVE')
ON CONFLICT (provenance_id, operation_type, effective_from) DO NOTHING;


-- 1.3) PubMed 分页配置 (ValueObjectJpaEntity: 仅 id 字段)
INSERT INTO reg_prov_pagination_cfg
(id, provenance_id, operation_type, effective_from, effective_to,
 pagination_mode_code, page_size_value, max_pages_per_execution,
 sort_field_param_name, sorting_direction, lifecycle_status_code)
VALUES
    (1, 800000000000000001, 'ALL', TIMESTAMP '2025-09-01 00:00:00.000000', NULL,
     'PAGE_NUMBER', 100, 1000,
     NULL, true, 'ACTIVE')
ON CONFLICT (provenance_id, operation_type, effective_from) DO NOTHING;


/* ====================================================================
 * 第2部分: 统一字段字典 (数据源无关)
 * ==================================================================== */

-- 2.1) PubMed 特定字段 (ValueObjectJpaEntity: 仅 id 字段)
INSERT INTO reg_expr_field_dict
(id, field_key, display_name, description, data_type_code, cardinality_code,
 exposable, is_date)
VALUES
    -- Entrez date field (PubMed)
    (900001, 'entrez_date', 'Entrez Date', 'PubMed Entrez date for temporal filtering',
     'DATE', 'SINGLE', true, true),

    -- Title/Abstract text field (PubMed)
    (900002, 'tiab', 'Title/Abstract', 'Unified text field mapped to PubMed TIAB (Title/Abstract) for basic keyword search',
     'TEXT', 'SINGLE', true, false)
ON CONFLICT (field_key) DO NOTHING;


-- 2.2) 共享字段 (EPMC & CrossRef & PubMed) (ValueObjectJpaEntity: 仅 id 字段)
INSERT INTO reg_expr_field_dict
(id, field_key, display_name, description, data_type_code, cardinality_code,
 exposable, is_date)
VALUES
    -- Publication date field (shared by EPMC, CrossRef, PubMed)
    (910001, 'publication_date', 'Publication Date', 'Publication date for article filtering (shared field)',
     'DATE', 'SINGLE', true, true),

    -- Free text field (shared by EPMC & CrossRef)
    (910002, 'text', 'Free Text', 'Free text search field (shared by EPMC and CrossRef)',
     'TEXT', 'SINGLE', true, false)
ON CONFLICT (field_key) DO NOTHING;


/* ====================================================================
 * 第3部分: PubMed 表达式配置
 * ==================================================================== */

-- 3.1) PubMed 字段能力 (ValueObjectJpaEntity: 仅 id 字段)
INSERT INTO reg_prov_expr_capability
(id, provenance_id, operation_type, lifecycle_status_code,
 field_key, effective_from, effective_to,
 ops, negatable_ops, supports_not,
 term_matches, term_case_sensitive_allowed, term_allow_blank, term_min_len, term_max_len, term_pattern,
 in_max_size, in_case_sensitive_allowed,
 range_kind_code, range_allow_open_start, range_allow_open_end, range_allow_closed_at_infty,
 date_min, date_max, datetime_min, datetime_max, number_min, number_max,
 exists_supported, token_kinds, token_value_pattern)
VALUES
    -- entrez_date RANGE capability (DATE type, fixed from V1.1.4)
    (900101, 800000000000000001, 'ALL', 'ACTIVE',
     'entrez_date', TIMESTAMP '2025-10-14 00:00:00.000000', NULL,
     jsonb_build_array('RANGE'), NULL, false,
     NULL, false, false, 0, 0, NULL,
     0, false,
     'DATE', true, true, false,
     DATE '1900-01-01', NULL, NULL, NULL, NULL, NULL,
     false, NULL, NULL),

    -- tiab TERM/IN capability
    (900102, 800000000000000001, 'ALL', 'ACTIVE',
     'tiab', TIMESTAMP '2025-10-14 00:00:00.000000', NULL,
     jsonb_build_array('TERM', 'IN'), jsonb_build_array('TERM'), true,
     jsonb_build_array('PHRASE', 'ANY'), false, false, 1, 0, NULL,
     1000, false,
     'NONE', false, false, false,
     NULL, NULL, NULL, NULL, NULL, NULL,
     false, NULL, NULL),

    -- publication_date RANGE capability (merged from V1.1.5)
    (900103, 800000000000000001, 'ALL', 'ACTIVE',
     'publication_date', TIMESTAMP '2025-10-18 00:00:00.000000', NULL,
     jsonb_build_array('RANGE'), NULL, false,
     NULL, false, false, 0, 0, NULL,
     0, false,
     'DATE', true, true, false,
     DATE '1900-01-01', NULL, NULL, NULL, NULL, NULL,
     false, NULL, NULL)
ON CONFLICT (provenance_id, operation_type, field_key, effective_from) DO NOTHING;


-- 3.2) PubMed 渲染规则 (ValueObjectJpaEntity: 仅 id 字段)
INSERT INTO reg_prov_expr_render_rule
(id, provenance_id, operation_type, lifecycle_status_code,
 field_key, op_code, match_type_code, negated, value_type_code, emit_type_code,
 effective_from, effective_to,
 template, item_template, joiner, wrap_group,
 params, fn_code)
VALUES
    -- entrez_date RANGE -> PARAMS
    (900201, 800000000000000001, 'ALL', 'ACTIVE',
     'entrez_date', 'RANGE', NULL, NULL, 'DATE', 'PARAMS',
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL,
     NULL, NULL, NULL, false,
     jsonb_build_object('from', '{{from}}', 'to', '{{to}}', 'datetype', '{{datetype}}'), 'PUBMED_DATETYPE'),

    -- tiab TERM (ANY match) -> QUERY
    (900202, 800000000000000001, 'ALL', 'ACTIVE',
     'tiab', 'TERM', NULL, NULL, 'STRING', 'QUERY',
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL,
     '{{v}}[TIAB]', NULL, NULL, false,
     NULL, NULL),

    -- tiab IN -> QUERY (OR-joined)
    (900203, 800000000000000001, 'ALL', 'ACTIVE',
     'tiab', 'IN', NULL, NULL, 'STRING', 'QUERY',
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL,
     NULL, '"{{item}}"[TIAB]', ' OR ', true,
     NULL, NULL),

    -- tiab TERM (PHRASE match) -> QUERY
    (900204, 800000000000000001, 'ALL', 'ACTIVE',
     'tiab', 'TERM', 'PHRASE', NULL, 'STRING', 'QUERY',
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL,
     '"{{v}}"[TIAB]', NULL, NULL, false,
     NULL, NULL),

    -- publication_date RANGE -> PARAMS (merged from V1.1.5)
    (900205, 800000000000000001, 'ALL', 'ACTIVE',
     'publication_date', 'RANGE', NULL, NULL, 'DATE', 'PARAMS',
     TIMESTAMP '2025-10-18 00:00:00.000000', NULL,
     NULL, NULL, NULL, false,
     jsonb_build_object('from', '{{from}}', 'to', '{{to}}', 'datetype', '{{datetype}}'), 'PUBMED_DATETYPE')
ON CONFLICT (provenance_id, operation_type, field_key, op_code, match_type_key, negated_key, value_type_key, emit_type_code, effective_from) DO NOTHING;


-- 3.3) PubMed 全局 API 参数映射 (ValueObjectJpaEntity: 仅 id 字段)
INSERT INTO reg_prov_api_param_map
(id, provenance_id, operation_type, lifecycle_status_code,
 endpoint_name, std_key, provider_param_name, transform_code, notes,
 effective_from, effective_to)
VALUES
    -- Boolean query bridging: std 'query' -> provider 'term'
    (900310, 800000000000000001, 'ALL', 'ACTIVE',
     NULL, 'query', 'term', NULL, NULL,
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL),

    -- Date window lower bound
    (900301, 800000000000000001, 'ALL', 'ACTIVE',
     NULL, 'from', 'mindate', NULL, NULL,
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL),

    -- Date window upper bound (exclusive -> inclusive transform)
    (900302, 800000000000000001, 'ALL', 'ACTIVE',
     NULL, 'to', 'maxdate', 'TO_EXCLUSIVE_MINUS_1D', NULL,
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL),

    -- Date type discriminator (PDAT/EDAT)
    (900303, 800000000000000001, 'ALL', 'ACTIVE',
     NULL, 'datetype', 'datetype', NULL, NULL,
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL),

    -- Pagination: limit -> retmax
    (900304, 800000000000000001, 'ALL', 'ACTIVE',
     NULL, 'limit', 'retmax', NULL, NULL,
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL),

    -- Pagination: offset -> retstart
    (900305, 800000000000000001, 'ALL', 'ACTIVE',
     NULL, 'offset', 'retstart', NULL, NULL,
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL)
ON CONFLICT (provenance_id, operation_type, endpoint_name, std_key, effective_from) DO NOTHING;


/* ====================================================================
 * 第4部分: EPMC 表达式配置
 * ==================================================================== */

-- 4.1) EPMC 字段能力 (ValueObjectJpaEntity: 仅 id 字段)
INSERT INTO reg_prov_expr_capability
(id, provenance_id, operation_type, lifecycle_status_code,
 field_key, effective_from, effective_to,
 ops, negatable_ops, supports_not,
 term_matches, term_case_sensitive_allowed, term_allow_blank, term_min_len, term_max_len, term_pattern,
 in_max_size, in_case_sensitive_allowed,
 range_kind_code, range_allow_open_start, range_allow_open_end, range_allow_closed_at_infty,
 date_min, date_max, datetime_min, datetime_max, number_min, number_max,
 exists_supported, token_kinds, token_value_pattern)
VALUES
    -- publication_date RANGE capability
    (910101, (SELECT id FROM reg_provenance WHERE provenance_code='EPMC'), 'ALL', 'ACTIVE',
     'publication_date', TIMESTAMP '2025-10-14 00:00:00.000000', NULL,
     jsonb_build_array('RANGE'), NULL, false,
     NULL, false, false, 0, 0, NULL,
     0, false,
     'DATE', true, true, false,
     DATE '1900-01-01', NULL, NULL, NULL, NULL, NULL,
     false, NULL, NULL),

    -- text TERM/IN capability
    (910102, (SELECT id FROM reg_provenance WHERE provenance_code='EPMC'), 'ALL', 'ACTIVE',
     'text', TIMESTAMP '2025-10-14 00:00:00.000000', NULL,
     jsonb_build_array('TERM', 'IN'), jsonb_build_array('TERM'), true,
     jsonb_build_array('PHRASE', 'ANY'), false, false, 1, 0, NULL,
     1000, false,
     'NONE', false, false, false,
     NULL, NULL, NULL, NULL, NULL, NULL,
     false, NULL, NULL)
ON CONFLICT (provenance_id, operation_type, field_key, effective_from) DO NOTHING;


-- 4.2) EPMC 渲染规则 (ValueObjectJpaEntity: 仅 id 字段)
INSERT INTO reg_prov_expr_render_rule
(id, provenance_id, operation_type, lifecycle_status_code,
 field_key, op_code, match_type_code, negated, value_type_code, emit_type_code,
 effective_from, effective_to,
 template, item_template, joiner, wrap_group,
 params, fn_code)
VALUES
    -- publication_date RANGE -> QUERY
    (910201, (SELECT id FROM reg_provenance WHERE provenance_code='EPMC'), 'ALL', 'ACTIVE',
     'publication_date', 'RANGE', NULL, NULL, 'DATE', 'QUERY',
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL,
     'FIRST_PDATE:[{{from}} TO {{to}}]', NULL, NULL, false,
     NULL, NULL),

    -- text TERM ANY -> QUERY (no quotes)
    (910202, (SELECT id FROM reg_provenance WHERE provenance_code='EPMC'), 'ALL', 'ACTIVE',
     'text', 'TERM', 'ANY', NULL, 'STRING', 'QUERY',
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL,
     '{{v}}', NULL, NULL, false,
     NULL, NULL),

    -- text TERM PHRASE -> QUERY (quoted)
    (910203, (SELECT id FROM reg_provenance WHERE provenance_code='EPMC'), 'ALL', 'ACTIVE',
     'text', 'TERM', 'PHRASE', NULL, 'STRING', 'QUERY',
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL,
     '"{{v}}"', NULL, NULL, false,
     NULL, NULL),

    -- text IN -> QUERY (OR-joined)
    (910204, (SELECT id FROM reg_provenance WHERE provenance_code='EPMC'), 'ALL', 'ACTIVE',
     'text', 'IN', NULL, NULL, 'STRING', 'QUERY',
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL,
     NULL, '"{{item}}"', ' OR ', true,
     NULL, NULL)
ON CONFLICT (provenance_id, operation_type, field_key, op_code, match_type_key, negated_key, value_type_key, emit_type_code, effective_from) DO NOTHING;


-- 4.3) EPMC 全局 API 参数映射 (ValueObjectJpaEntity: 仅 id 字段)
INSERT INTO reg_prov_api_param_map
(id, provenance_id, operation_type, lifecycle_status_code,
 endpoint_name, std_key, provider_param_name, transform_code, notes,
 effective_from, effective_to)
VALUES
    -- Boolean query bridging: std 'query' -> provider 'query'
    (910301, (SELECT id FROM reg_provenance WHERE provenance_code='EPMC'), 'ALL', 'ACTIVE',
     NULL, 'query', 'query', NULL, NULL,
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL),

    -- Pagination: limit -> pageSize
    (910302, (SELECT id FROM reg_provenance WHERE provenance_code='EPMC'), 'ALL', 'ACTIVE',
     NULL, 'limit', 'pageSize', NULL, NULL,
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL)
ON CONFLICT (provenance_id, operation_type, endpoint_name, std_key, effective_from) DO NOTHING;


/* ====================================================================
 * 第5部分: CrossRef 表达式配置
 * ==================================================================== */

-- 5.1) CrossRef 字段能力 (ValueObjectJpaEntity: 仅 id 字段)
INSERT INTO reg_prov_expr_capability
(id, provenance_id, operation_type, lifecycle_status_code,
 field_key, effective_from, effective_to,
 ops, negatable_ops, supports_not,
 term_matches, term_case_sensitive_allowed, term_allow_blank, term_min_len, term_max_len, term_pattern,
 in_max_size, in_case_sensitive_allowed,
 range_kind_code, range_allow_open_start, range_allow_open_end, range_allow_closed_at_infty,
 date_min, date_max, datetime_min, datetime_max, number_min, number_max,
 exists_supported, token_kinds, token_value_pattern)
VALUES
    -- publication_date RANGE capability
    (920101, (SELECT id FROM reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     'publication_date', TIMESTAMP '2025-10-14 00:00:00.000000', NULL,
     jsonb_build_array('RANGE'), NULL, false,
     NULL, false, false, 0, 0, NULL,
     0, false,
     'DATE', true, true, false,
     DATE '1900-01-01', NULL, NULL, NULL, NULL, NULL,
     false, NULL, NULL),

    -- text TERM/IN capability
    (920102, (SELECT id FROM reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     'text', TIMESTAMP '2025-10-14 00:00:00.000000', NULL,
     jsonb_build_array('TERM', 'IN'), jsonb_build_array('TERM'), true,
     jsonb_build_array('PHRASE', 'ANY'), false, false, 1, 0, NULL,
     1000, false,
     'NONE', false, false, false,
     NULL, NULL, NULL, NULL, NULL, NULL,
     false, NULL, NULL)
ON CONFLICT (provenance_id, operation_type, field_key, effective_from) DO NOTHING;


-- 5.2) CrossRef 渲染规则 (ValueObjectJpaEntity: 仅 id 字段)
INSERT INTO reg_prov_expr_render_rule
(id, provenance_id, operation_type, lifecycle_status_code,
 field_key, op_code, match_type_code, negated, value_type_code, emit_type_code,
 effective_from, effective_to,
 template, item_template, joiner, wrap_group,
 params, fn_code)
VALUES
    -- publication_date RANGE -> PARAMS
    (920201, (SELECT id FROM reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     'publication_date', 'RANGE', NULL, NULL, 'DATE', 'PARAMS',
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL,
     NULL, NULL, NULL, false,
     jsonb_build_object('filter', 'from-pub-date:{{from}},until-pub-date:{{to}}'), NULL),

    -- text TERM ANY -> QUERY (no quotes)
    (920202, (SELECT id FROM reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     'text', 'TERM', 'ANY', NULL, 'STRING', 'QUERY',
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL,
     '{{v}}', NULL, NULL, false,
     NULL, NULL),

    -- text TERM PHRASE -> QUERY (quoted)
    (920203, (SELECT id FROM reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     'text', 'TERM', 'PHRASE', NULL, 'STRING', 'QUERY',
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL,
     '"{{v}}"', NULL, NULL, false,
     NULL, NULL),

    -- text IN -> QUERY (OR-joined)
    (920204, (SELECT id FROM reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     'text', 'IN', NULL, NULL, 'STRING', 'QUERY',
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL,
     NULL, '"{{item}}"', ' OR ', true,
     NULL, NULL)
ON CONFLICT (provenance_id, operation_type, field_key, op_code, match_type_key, negated_key, value_type_key, emit_type_code, effective_from) DO NOTHING;


-- 5.3) CrossRef 全局 API 参数映射 (ValueObjectJpaEntity: 仅 id 字段)
INSERT INTO reg_prov_api_param_map
(id, provenance_id, operation_type, lifecycle_status_code,
 endpoint_name, std_key, provider_param_name, transform_code, notes,
 effective_from, effective_to)
VALUES
    -- Boolean query bridging: std 'query' -> provider 'query'
    (920301, (SELECT id FROM reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     NULL, 'query', 'query', NULL, NULL,
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL),

    -- Filter mapping: std 'filter' -> provider 'filter'
    (920302, (SELECT id FROM reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     NULL, 'filter', 'filter', NULL, NULL,
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL),

    -- Pagination: limit -> rows
    (920303, (SELECT id FROM reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     NULL, 'limit', 'rows', NULL, NULL,
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL),

    -- Pagination: offset -> offset
    (920304, (SELECT id FROM reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     NULL, 'offset', 'offset', NULL, NULL,
     TIMESTAMP '2025-10-14 00:00:00.000000', NULL)
ON CONFLICT (provenance_id, operation_type, endpoint_name, std_key, effective_from) DO NOTHING;


/* ====================================================================
 * V2 种子数据结束
 * ==================================================================== */

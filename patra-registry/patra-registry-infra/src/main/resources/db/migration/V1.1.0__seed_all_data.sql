/* ====================================================================
 * Migration: V1.1.0 - Complete Seed Data for Registry
 * ====================================================================
 * Purpose: Consolidated initialization data for all supported provenances
 *          (PubMed, EPMC, CrossRef) and their expression configurations.
 *
 * Consolidation History:
 *   - Merged from: V1.1.0~V1.1.3 (original seed scripts)
 *   - Applied fixes from: V1.1.4 (entrez_date DATE type)
 *   - Integrated additions from: V1.1.5 (publication_date support)
 *   - Integrated additions from: V1.1.6 (pagination configuration)
 *
 * Structure:
 *   Part 1: Provenance base data (reg_provenance, window, pagination configs)
 *   Part 2: Unified field dictionary (reg_expr_field_dict)
 *   Part 3: PubMed expression configuration
 *   Part 4: EPMC expression configuration
 *   Part 5: CrossRef expression configuration
 *
 * Key Assumptions:
 *   - Operation scope = ALL
 *   - Global scope = endpoint_name IS NULL
 *   - Effective from = 2025-09-01 / 2025-10-14 (UTC)
 *   - lifecycle_status_code = 'ACTIVE'
 * ==================================================================== */


/* ====================================================================
 * Part 1: Provenance Base Data
 * ==================================================================== */

-- 1.1) Register three provenances: PubMed, EPMC, CrossRef
INSERT INTO patra_registry.`reg_provenance`
(provenance_code, provenance_name, base_url_default, timezone_default,
 docs_url, is_active, lifecycle_status_code, record_remarks,
 created_at, created_by, created_by_name,
 updated_at, updated_by, updated_by_name, version, ip_address, deleted)
VALUES
    ('PUBMED', 'PubMed', 'https://eutils.ncbi.nlm.nih.gov/entrez/eutils/', 'UTC',
     'https://www.ncbi.nlm.nih.gov/books/NBK25501/', 1, 'ACTIVE',
     JSON_ARRAY(JSON_OBJECT('time', '2025-09-01 10:30:00', 'by', '系统管理员', 'note', '初始化注册 PubMed 来源')),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0, INET6_ATON('192.168.1.10'), 0),

    ('EPMC', 'Europe PMC', 'https://www.ebi.ac.uk/europepmc/webservices/rest/', 'UTC',
     'https://europepmc.org/RestfulWebService', 1, 'ACTIVE',
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', '初始化注册 Europe PMC 来源')),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0, INET6_ATON('192.168.1.10'), 0),

    ('CROSSREF', 'Crossref', 'https://api.crossref.org/', 'UTC',
     'https://api.crossref.org/swagger-ui/index.html', 1, 'ACTIVE',
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', '初始化注册 Crossref 来源')),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0, INET6_ATON('192.168.1.10'), 0);


-- 1.2) PubMed window & offset configuration
INSERT INTO patra_registry.reg_prov_window_offset_cfg
(id, provenance_id, operation_type, effective_from, effective_to,
 window_mode_code, window_size_value, window_size_unit_code, calendar_align_to,
 lookback_value, lookback_unit_code, overlap_value, overlap_unit_code, watermark_lag_seconds,
 offset_type_code, offset_field_key, offset_date_format, window_date_field_key,
 max_ids_per_window, max_window_span_seconds, lifecycle_status_code, record_remarks,
 created_at, created_by, created_by_name, updated_at, updated_by, updated_by_name,
 version, ip_address, deleted)
VALUES
    (1, 1, 'ALL', '2025-09-01 00:00:00.000000', NULL,
     'CALENDAR', 1, 'DAY', 'DAY',
     2, 'HOUR', 1, 'HOUR', 3600,
     'DATE', 'entrez_date', 'yyyy-MM-dd', 'entrez_date',
     50000, 2592000, 'ACTIVE',
     JSON_ARRAY(JSON_OBJECT('time', '2025-09-01 12:00:00', 'by', '系统管理员',
                            'note', 'PubMed Harvest 任务窗口配置 - 仅支持日期级别查询（无时间组件）')),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0, INET6_ATON('192.168.1.20'), 0);


-- 1.3) PubMed pagination configuration (merged from V1.1.6)
INSERT INTO patra_registry.reg_prov_pagination_cfg
(provenance_id, operation_type, effective_from, effective_to,
 pagination_mode_code, page_size_value, max_pages_per_execution,
 sort_field_param_name, sorting_direction, lifecycle_status_code,
 record_remarks, created_at, created_by, created_by_name,
 updated_at, updated_by, updated_by_name, version, ip_address, deleted)
VALUES
    (1, 'ALL', '2025-09-01 00:00:00.000000', NULL,
     'PAGE_NUMBER', 100, 1000,
     NULL, 1, 'ACTIVE',
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-19 11:30:00', 'by', '系统管理员',
                            'note', 'PubMed 分页配置初始化 - 默认每页500条记录，最大支持10000条（API限制）')),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0, INET6_ATON('192.168.1.10'), 0);


/* ====================================================================
 * Part 2: Unified Field Dictionary (Source-Agnostic)
 * ==================================================================== */

-- 2.1) PubMed-specific fields
INSERT INTO patra_registry.reg_expr_field_dict
(id, field_key, display_name, description, data_type_code, cardinality_code,
 exposable, is_date, record_remarks, version, ip_address,
 created_at, created_by, created_by_name, updated_at, updated_by, updated_by_name, deleted)
VALUES
    -- Entrez date field (PubMed)
    (900001, 'entrez_date', 'Entrez Date', 'PubMed Entrez date for temporal filtering',
     'DATE', 'SINGLE', 1, 1,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note', 'Seed for PubMed test')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- Title/Abstract text field (PubMed)
    (900002, 'tiab', 'Title/Abstract', 'Unified text field mapped to PubMed TIAB (Title/Abstract) for basic keyword search',
     'TEXT', 'SINGLE', 1, 0,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note', 'Seed for PubMed TIAB text search')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);


-- 2.2) Shared fields (EPMC & CrossRef & PubMed)
INSERT INTO patra_registry.reg_expr_field_dict
(id, field_key, display_name, description, data_type_code, cardinality_code,
 exposable, is_date, record_remarks, version, ip_address,
 created_at, created_by, created_by_name, updated_at, updated_by, updated_by_name, deleted)
VALUES
    -- Publication date field (shared by EPMC, CrossRef, PubMed)
    (910001, 'publication_date', 'Publication Date', 'Publication date for article filtering (shared field)',
     'DATE', 'SINGLE', 1, 1,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'Shared field for EPMC/CrossRef/PubMed')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- Free text field (shared by EPMC & CrossRef)
    (910002, 'text', 'Free Text', 'Free text search field (shared by EPMC and CrossRef)',
     'TEXT', 'SINGLE', 1, 0,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'Shared text search field')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);


/* ====================================================================
 * Part 3: PubMed Expression Configuration
 * ==================================================================== */

-- 3.1) Field capabilities for PubMed
INSERT INTO patra_registry.reg_prov_expr_capability
(id, provenance_id, operation_type, lifecycle_status_code,
 field_key, effective_from, effective_to,
 ops, negatable_ops, supports_not,
 term_matches, term_case_sensitive_allowed, term_allow_blank, term_min_len, term_max_len, term_pattern,
 in_max_size, in_case_sensitive_allowed,
 range_kind_code, range_allow_open_start, range_allow_open_end, range_allow_closed_at_infty,
 date_min, date_max, datetime_min, datetime_max, number_min, number_max,
 exists_supported, token_kinds, token_value_pattern,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name, updated_at, updated_by, updated_by_name, deleted)
VALUES
    -- entrez_date RANGE capability (DATE type, fixed from V1.1.4)
    (900101, 1, 'ALL', 'ACTIVE',
     'entrez_date', TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY('RANGE'), NULL, 0,
     NULL, 0, 0, 0, 0, NULL,
     0, 0,
     'DATE', 1, 1, 0,
     DATE '1900-01-01', NULL, NULL, NULL, NULL, NULL,
     0, NULL, NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note',
                            'PubMed: entrez_date supports RANGE[DATE] (fixed from DATETIME)')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- tiab TERM/IN capability
    (900102, 1, 'ALL', 'ACTIVE',
     'tiab', TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY('TERM', 'IN'), JSON_ARRAY('TERM'), 1,
     JSON_ARRAY('PHRASE', 'ANY'), 0, 0, 1, 0, NULL,
     1000, 0,
     'NONE', 0, 0, 0,
     NULL, NULL, NULL, NULL, NULL, NULL,
     0, NULL, NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note',
                            'PubMed: tiab supports TERM/IN; NOT allowed for TERM')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- publication_date RANGE capability (merged from V1.1.5)
    (900103, 1, 'ALL', 'ACTIVE',
     'publication_date', TIMESTAMP('2025-10-18 00:00:00.000000'), NULL,
     JSON_ARRAY('RANGE'), NULL, 0,
     NULL, 0, 0, 0, 0, NULL,
     0, 0,
     'DATE', 1, 1, 0,
     DATE '1900-01-01', NULL, NULL, NULL, NULL, NULL,
     0, NULL, NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-18 00:00:00', 'by', '系统管理员', 'note',
                            'PubMed: publication_date supports RANGE[DATE] with pdat datetype')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);


-- 3.2) Render rules for PubMed
INSERT INTO patra_registry.reg_prov_expr_render_rule
(id, provenance_id, operation_type, lifecycle_status_code,
 field_key, op_code, match_type_code, negated, value_type_code, emit_type_code,
 effective_from, effective_to,
 template, item_template, joiner, wrap_group,
 params, fn_code,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name, updated_at, updated_by, updated_by_name, deleted)
VALUES
    -- entrez_date RANGE -> PARAMS
    (900201, 1, 'ALL', 'ACTIVE',
     'entrez_date', 'RANGE', NULL, NULL, 'DATE', 'PARAMS',
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     NULL, NULL, NULL, 0,
     JSON_OBJECT('from', '{{from}}', 'to', '{{to}}', 'datetype', '{{datetype}}'), 'PUBMED_DATETYPE',
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note',
                            'Emit std params for PubMed date filtering with {{...}} placeholders')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- tiab TERM (ANY match) -> QUERY
    (900202, 1, 'ALL', 'ACTIVE',
     'tiab', 'TERM', NULL, NULL, 'STRING', 'QUERY',
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     '{{v}}[TIAB]', NULL, NULL, 0,
     NULL, NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note',
                            'TERM template for TIAB: ANY match (no quotes)')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- tiab IN -> QUERY (OR-joined)
    (900203, 1, 'ALL', 'ACTIVE',
     'tiab', 'IN', NULL, NULL, 'STRING', 'QUERY',
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     NULL, '"{{item}}"[TIAB]', ' OR ', 1,
     NULL, NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note',
                            'IN template for TIAB: OR-joined quoted items with wrap')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- tiab TERM (PHRASE match) -> QUERY
    (900204, 1, 'ALL', 'ACTIVE',
     'tiab', 'TERM', 'PHRASE', NULL, 'STRING', 'QUERY',
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     '"{{v}}"[TIAB]', NULL, NULL, 0,
     NULL, NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note',
                            'TERM template for TIAB: PHRASE match (quoted)')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- publication_date RANGE -> PARAMS (merged from V1.1.5)
    (900205, 1, 'ALL', 'ACTIVE',
     'publication_date', 'RANGE', NULL, NULL, 'DATE', 'PARAMS',
     TIMESTAMP('2025-10-18 00:00:00.000000'), NULL,
     NULL, NULL, NULL, 0,
     JSON_OBJECT('from', '{{from}}', 'to', '{{to}}', 'datetype', '{{datetype}}'), 'PUBMED_DATETYPE',
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-18 00:00:00', 'by', '系统管理员', 'note',
                            'Emit std params for PubMed publication_date filtering; PUBMED_DATETYPE returns pdat')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);


-- 3.3) Global API parameter mappings for PubMed
INSERT INTO patra_registry.reg_prov_api_param_map
(id, provenance_id, operation_type, lifecycle_status_code,
 endpoint_name, std_key, provider_param_name, transform_code, notes,
 effective_from, effective_to,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name, updated_at, updated_by, updated_by_name, deleted)
VALUES
    -- Boolean query bridging: std 'query' -> provider 'term'
    (900310, 1, 'ALL', 'ACTIVE',
     NULL, 'query', 'term', NULL, NULL,
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note', 'std query -> term (query bridging)')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- Date window lower bound
    (900301, 1, 'ALL', 'ACTIVE',
     NULL, 'from', 'mindate', NULL, NULL,
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note', 'std from -> mindate')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- Date window upper bound (exclusive -> inclusive transform)
    (900302, 1, 'ALL', 'ACTIVE',
     NULL, 'to', 'maxdate', 'TO_EXCLUSIVE_MINUS_1D', NULL,
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note',
                            'std to -> maxdate (exclusive minus 1 day)')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- Date type discriminator (PDAT/EDAT)
    (900303, 1, 'ALL', 'ACTIVE',
     NULL, 'datetype', 'datetype', NULL, NULL,
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note', 'std datetype -> datetype')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- Pagination: limit -> retmax
    (900304, 1, 'ALL', 'ACTIVE',
     NULL, 'limit', 'retmax', NULL, NULL,
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note', 'std limit -> retmax')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- Pagination: offset -> retstart
    (900305, 1, 'ALL', 'ACTIVE',
     NULL, 'offset', 'retstart', NULL, NULL,
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note', 'std offset -> retstart')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);


/* ====================================================================
 * Part 4: EPMC Expression Configuration
 * ==================================================================== */

-- 4.1) Field capabilities for EPMC
INSERT INTO patra_registry.reg_prov_expr_capability
(id, provenance_id, operation_type, lifecycle_status_code,
 field_key, effective_from, effective_to,
 ops, negatable_ops, supports_not,
 term_matches, term_case_sensitive_allowed, term_allow_blank, term_min_len, term_max_len, term_pattern,
 in_max_size, in_case_sensitive_allowed,
 range_kind_code, range_allow_open_start, range_allow_open_end, range_allow_closed_at_infty,
 date_min, date_max, datetime_min, datetime_max, number_min, number_max,
 exists_supported, token_kinds, token_value_pattern,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name, updated_at, updated_by, updated_by_name, deleted)
VALUES
    -- publication_date RANGE capability
    (910101, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='EPMC'), 'ALL', 'ACTIVE',
     'publication_date', TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY('RANGE'), NULL, 0,
     NULL, 0, 0, 0, 0, NULL,
     0, 0,
     'DATE', 1, 1, 0,
     DATE '1900-01-01', NULL, NULL, NULL, NULL, NULL,
     0, NULL, NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'EPMC: publication_date supports RANGE[DATE]')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- text TERM/IN capability
    (910102, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='EPMC'), 'ALL', 'ACTIVE',
     'text', TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY('TERM', 'IN'), JSON_ARRAY('TERM'), 1,
     JSON_ARRAY('PHRASE', 'ANY'), 0, 0, 1, 0, NULL,
     1000, 0,
     'NONE', 0, 0, 0,
     NULL, NULL, NULL, NULL, NULL, NULL,
     0, NULL, NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'EPMC: text supports TERM/IN; NOT allowed for TERM')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);


-- 4.2) Render rules for EPMC
INSERT INTO patra_registry.reg_prov_expr_render_rule
(id, provenance_id, operation_type, lifecycle_status_code,
 field_key, op_code, match_type_code, negated, value_type_code, emit_type_code,
 effective_from, effective_to,
 template, item_template, joiner, wrap_group,
 params, fn_code,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name, updated_at, updated_by, updated_by_name, deleted)
VALUES
    -- publication_date RANGE -> QUERY
    (910201, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='EPMC'), 'ALL', 'ACTIVE',
     'publication_date', 'RANGE', NULL, NULL, 'DATE', 'QUERY',
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     'FIRST_PDATE:[{{from}} TO {{to}}]', NULL, NULL, 0,
     NULL, NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'EPMC date range as QUERY fragment')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- text TERM ANY -> QUERY (no quotes)
    (910202, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='EPMC'), 'ALL', 'ACTIVE',
     'text', 'TERM', 'ANY', NULL, 'STRING', 'QUERY',
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     '{{v}}', NULL, NULL, 0,
     NULL, NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'EPMC text TERM ANY: no quotes')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- text TERM PHRASE -> QUERY (quoted)
    (910203, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='EPMC'), 'ALL', 'ACTIVE',
     'text', 'TERM', 'PHRASE', NULL, 'STRING', 'QUERY',
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     '"{{v}}"', NULL, NULL, 0,
     NULL, NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'EPMC text TERM PHRASE: quoted')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- text IN -> QUERY (OR-joined)
    (910204, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='EPMC'), 'ALL', 'ACTIVE',
     'text', 'IN', NULL, NULL, 'STRING', 'QUERY',
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     NULL, '"{{item}}"', ' OR ', 1,
     NULL, NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'EPMC text IN: OR-joined quoted items with wrap')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);


-- 4.3) Global API parameter mappings for EPMC
INSERT INTO patra_registry.reg_prov_api_param_map
(id, provenance_id, operation_type, lifecycle_status_code,
 endpoint_name, std_key, provider_param_name, transform_code, notes,
 effective_from, effective_to,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name, updated_at, updated_by, updated_by_name, deleted)
VALUES
    -- Boolean query bridging: std 'query' -> provider 'query'
    (910301, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='EPMC'), 'ALL', 'ACTIVE',
     NULL, 'query', 'query', NULL, NULL,
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'EPMC: std query -> query (query bridging)')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- Pagination: limit -> pageSize
    (910302, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='EPMC'), 'ALL', 'ACTIVE',
     NULL, 'limit', 'pageSize', NULL, NULL,
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'EPMC: std limit -> pageSize')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);


/* ====================================================================
 * Part 5: CrossRef Expression Configuration
 * ==================================================================== */

-- 5.1) Field capabilities for CrossRef
INSERT INTO patra_registry.reg_prov_expr_capability
(id, provenance_id, operation_type, lifecycle_status_code,
 field_key, effective_from, effective_to,
 ops, negatable_ops, supports_not,
 term_matches, term_case_sensitive_allowed, term_allow_blank, term_min_len, term_max_len, term_pattern,
 in_max_size, in_case_sensitive_allowed,
 range_kind_code, range_allow_open_start, range_allow_open_end, range_allow_closed_at_infty,
 date_min, date_max, datetime_min, datetime_max, number_min, number_max,
 exists_supported, token_kinds, token_value_pattern,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name, updated_at, updated_by, updated_by_name, deleted)
VALUES
    -- publication_date RANGE capability
    (920101, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     'publication_date', TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY('RANGE'), NULL, 0,
     NULL, 0, 0, 0, 0, NULL,
     0, 0,
     'DATE', 1, 1, 0,
     DATE '1900-01-01', NULL, NULL, NULL, NULL, NULL,
     0, NULL, NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'Crossref: publication_date supports RANGE[DATE]')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- text TERM/IN capability
    (920102, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     'text', TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY('TERM', 'IN'), JSON_ARRAY('TERM'), 1,
     JSON_ARRAY('PHRASE', 'ANY'), 0, 0, 1, 0, NULL,
     1000, 0,
     'NONE', 0, 0, 0,
     NULL, NULL, NULL, NULL, NULL, NULL,
     0, NULL, NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'Crossref: text supports TERM/IN; NOT allowed for TERM')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);


-- 5.2) Render rules for CrossRef
INSERT INTO patra_registry.reg_prov_expr_render_rule
(id, provenance_id, operation_type, lifecycle_status_code,
 field_key, op_code, match_type_code, negated, value_type_code, emit_type_code,
 effective_from, effective_to,
 template, item_template, joiner, wrap_group,
 params, fn_code,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name, updated_at, updated_by, updated_by_name, deleted)
VALUES
    -- publication_date RANGE -> PARAMS
    (920201, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     'publication_date', 'RANGE', NULL, NULL, 'DATE', 'PARAMS',
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     NULL, NULL, NULL, 0,
     JSON_OBJECT('filter', 'from-pub-date:{{from}},until-pub-date:{{to}}'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'Crossref date range via filter param (MULTI std_key)')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- text TERM ANY -> QUERY (no quotes)
    (920202, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     'text', 'TERM', 'ANY', NULL, 'STRING', 'QUERY',
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     '{{v}}', NULL, NULL, 0,
     NULL, NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'Crossref text TERM ANY: no quotes')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- text TERM PHRASE -> QUERY (quoted)
    (920203, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     'text', 'TERM', 'PHRASE', NULL, 'STRING', 'QUERY',
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     '"{{v}}"', NULL, NULL, 0,
     NULL, NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'Crossref text TERM PHRASE: quoted')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- text IN -> QUERY (OR-joined)
    (920204, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     'text', 'IN', NULL, NULL, 'STRING', 'QUERY',
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     NULL, '"{{item}}"', ' OR ', 1,
     NULL, NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'Crossref text IN: OR-joined quoted items with wrap')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);


-- 5.3) Global API parameter mappings for CrossRef
INSERT INTO patra_registry.reg_prov_api_param_map
(id, provenance_id, operation_type, lifecycle_status_code,
 endpoint_name, std_key, provider_param_name, transform_code, notes,
 effective_from, effective_to,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name, updated_at, updated_by, updated_by_name, deleted)
VALUES
    -- Boolean query bridging: std 'query' -> provider 'query'
    (920301, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     NULL, 'query', 'query', NULL, NULL,
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'Crossref: std query -> query (query bridging)')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- Filter mapping: std 'filter' -> provider 'filter'
    (920302, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     NULL, 'filter', 'filter', NULL, NULL,
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'Crossref: std filter -> filter (MULTI cardinality)')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- Pagination: limit -> rows
    (920303, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     NULL, 'limit', 'rows', NULL, NULL,
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'Crossref: std limit -> rows')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- Pagination: offset -> offset
    (920304, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='CROSSREF'), 'ALL', 'ACTIVE',
     NULL, 'offset', 'offset', NULL, NULL,
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'Crossref: std offset -> offset')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);


/* ====================================================================
 * End of V1.1.0 Consolidated Seed Data
 * ==================================================================== */

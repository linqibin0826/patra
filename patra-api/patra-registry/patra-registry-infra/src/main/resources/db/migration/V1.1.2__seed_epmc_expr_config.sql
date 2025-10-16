/* ====================================================================
 * Seed: Europe PMC Expression Config (Provenance: EPMC)
 * Scope:
 *  - reg_expr_field_dict: add unified fields 'publication_date' and 'text'
 *  - reg_prov_expr_capability: declare RANGE (DATE) and TERM/IN capabilities
 *  - reg_prov_expr_render_rule: emit QUERY fragments for text and date
 *  - reg_prov_api_param_map: global mappings for query/limit/cursor
 *    NOTE: Boolean query aggregation is bridged to 'query' via std_key mapping.
 * Assumptions:
 *  - Operation scope = ALL
 *  - Global scope = endpoint_name IS NULL
 *  - Effective from = 2025-10-14 00:00:00 (UTC)
 *  - lifecycle_status_code = 'ACTIVE'
 * ==================================================================== */

-- 1) Unified field dictionary (source-agnostic)
INSERT INTO patra_registry.reg_expr_field_dict
(id, field_key, display_name, description,
 data_type_code, cardinality_code, exposable, is_date,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name,
 updated_at, updated_by, updated_by_name, deleted)
VALUES
    -- Date field for EPMC publication date filtering
    (910001, 'publication_date', 'Publication Date', 'Publication date for EPMC articles',
     'DATE', 'SINGLE', 1, 1,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'Seed for EPMC publication_date')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- Text field for EPMC free text search
    (910002, 'text', 'Free Text', 'Free text search field for EPMC',
     'TEXT', 'SINGLE', 1, 0,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'Seed for EPMC text search')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);


-- 2) Field capabilities for EPMC (source-aware)
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
 created_at, created_by, created_by_name,
 updated_at, updated_by, updated_by_name, deleted)
VALUES
    -- publication_date supports RANGE (DATE)
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

    -- text supports TERM (ANY/PHRASE) and IN
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


-- 3) Render rules for EPMC
INSERT INTO patra_registry.reg_prov_expr_render_rule
(id, provenance_id, operation_type, lifecycle_status_code,
 field_key, op_code, match_type_code, negated, value_type_code, emit_type_code,
 effective_from, effective_to,
 template, item_template, joiner, wrap_group,
 params, fn_code,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name,
 updated_at, updated_by, updated_by_name, deleted)
VALUES
    -- publication_date RANGE -> QUERY with FIRST_PDATE syntax
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

    -- text IN -> QUERY with OR joining and wrap
    (910204, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='EPMC'), 'ALL', 'ACTIVE',
     'text', 'IN', NULL, NULL, 'STRING', 'QUERY',
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     NULL, '"{{item}}"', ' OR ', 1,
     NULL, NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'EPMC text IN: OR-joined quoted items with wrap')),
     0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);


-- 4) Global API parameter mappings (endpoint_name = NULL, operation_type = ALL)
INSERT INTO patra_registry.reg_prov_api_param_map
(id, provenance_id, operation_type, lifecycle_status_code,
 endpoint_name, std_key, provider_param_name, transform_code, notes,
 effective_from, effective_to,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name,
 updated_at, updated_by, updated_by_name, deleted)
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
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- Optional cursor support (uncomment if needed)
    -- (910303, (SELECT id FROM patra_registry.reg_provenance WHERE provenance_code='EPMC'), 'ALL', 'ACTIVE',
    --  NULL, 'cursor', 'cursorMark', NULL, NULL,
    --  TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
    --  JSON_ARRAY(JSON_OBJECT('time', '2025-10-16 00:00:00', 'by', '系统管理员', 'note', 'EPMC: std cursor -> cursorMark')),
    --  0, INET6_ATON('0.0.0.0'),
    --  NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

-- End of EPMC seed

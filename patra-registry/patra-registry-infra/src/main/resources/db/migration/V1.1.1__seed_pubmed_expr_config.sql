/* ====================================================================
 * Seed: PubMed Expression Config (Provenance ID = 1)
 * Scope:
 *  - reg_expr_field_dict: add unified field 'entrez_date'
 *  - reg_prov_expr_capability: declare RANGE capability for entrez_date
 *  - reg_prov_expr_render_rule: emit standard params for PubMed date filtering
 *  - reg_prov_api_param_map: global (endpoint=NULL) mappings for std keys
 *    from/to/datetype/limit/offset -> mindate/maxdate/datetype/retmax/retstart
 *    NOTE: Boolean query aggregation (TIAB TERM/IN) is emitted as QUERY fragments
 *          and bound to PubMed 'term' in code, not via std_key mapping.
 * Assumptions:
 *  - Operation scope = ALL
 *  - Global scope = endpoint_name IS NULL
 *  - Effective from = 2025-10-14 00:00:00 (UTC)
 *  - lifecycle_status_code = 'ACTIVE'
 * ==================================================================== */

-- 1) Unified field dictionary (source-agnostic)
insert into patra_registry.reg_expr_field_dict (id, field_key, display_name, description, data_type_code,
                                                cardinality_code, exposable, is_date, record_remarks, version,
                                                ip_address, created_at, created_by, created_by_name, updated_at,
                                                updated_by, updated_by_name, deleted)
values (900001, 'entrez_date', 'Entrez Date', '', 'DATE', 'SINGLE', 1, 1, '[
  {
    "by": "系统管理员",
    "note": "Seed for PubMed test",
    "time": "2025-10-14 00:00:00"
  }
]', 0, 0x00000000, '2025-10-15 18:05:16.987294', 1001, '系统管理员', '2025-10-15 18:10:18.435546', 1001, '系统管理员',
        0);

-- Add minimal text field for keyword search across Title/Abstract (tiab)
INSERT INTO patra_registry.reg_expr_field_dict
(id, field_key, display_name, description,
 data_type_code, cardinality_code, exposable, is_date,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name,
 updated_at, updated_by, updated_by_name, deleted)
VALUES (900002,
        'tiab',
        'Title/Abstract',
        'Unified text field mapped to PubMed TIAB (Title/Abstract) for basic keyword search',
        'TEXT', 'SINGLE', 1, 0,
        JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note',
                               'Seed for PubMed TIAB text search')),
        0, INET6_ATON('0.0.0.0'),
        NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);


-- 2) Field capability for PubMed (source-aware)
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
VALUES (900101, 1, 'ALL', 'ACTIVE',
        'entrez_date', TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
        JSON_ARRAY('RANGE'), NULL, 0,
        NULL, 0, 0, 0, 0, NULL,
        0, 0,
        'DATETIME', 1, 1, 0,
        DATE '1900-01-01', NULL, NULL, NULL, NULL, NULL,
        0, NULL, NULL,
        JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note',
                               'PubMed: entrez_date supports RANGE[DATETIME]')),
        0, INET6_ATON('0.0.0.0'),
        NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);

-- Capability for TIAB text search (TERM/IN)
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
VALUES (900102, 1, 'ALL', 'ACTIVE',
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
        NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);


-- 3) Render rule for entrez_date RANGE -> standard params
INSERT INTO patra_registry.reg_prov_expr_render_rule
(id, provenance_id, operation_type, lifecycle_status_code,
 field_key, op_code, match_type_code, negated, value_type_code, emit_type_code,
 effective_from, effective_to,
 template, item_template, joiner, wrap_group,
 params, fn_code,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name,
 updated_at, updated_by, updated_by_name, deleted)
VALUES (900201, 1, 'ALL', 'ACTIVE',
        'entrez_date', 'RANGE', NULL, NULL, 'DATE', 'PARAMS',
        TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
        NULL, NULL, NULL, 0,
        JSON_OBJECT('from', '{{from}}', 'to', '{{to}}', 'datetype', '{{datetype}}'), 'PUBMED_DATETYPE',
        JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note',
                               'Emit std params for PubMed date filtering with {{...}} placeholders')),
        0, INET6_ATON('0.0.0.0'),
        NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);

-- Render TIAB TERM as PubMed fielded fragment; emit=QUERY (aggregated to std 'query')
INSERT INTO patra_registry.reg_prov_expr_render_rule
(id, provenance_id, operation_type, lifecycle_status_code,
 field_key, op_code, match_type_code, negated, value_type_code, emit_type_code,
 effective_from, effective_to,
 template, item_template, joiner, wrap_group,
 params, fn_code,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name,
 updated_at, updated_by, updated_by_name, deleted)
VALUES (900202, 1, 'ALL', 'ACTIVE',
        'tiab', 'TERM', NULL, NULL, 'STRING', 'QUERY',
        TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
        '{{v}}[TIAB]', NULL, NULL, 0,
        NULL, NULL,
        JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note',
                               'TERM template for TIAB: ANY match (no quotes)')),
        0, INET6_ATON('0.0.0.0'),
        NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);

-- Render TIAB TERM with PHRASE match (quoted)
INSERT INTO patra_registry.reg_prov_expr_render_rule
(id, provenance_id, operation_type, lifecycle_status_code,
 field_key, op_code, match_type_code, negated, value_type_code, emit_type_code,
 effective_from, effective_to,
 template, item_template, joiner, wrap_group,
 params, fn_code,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name,
 updated_at, updated_by, updated_by_name, deleted)
VALUES (900204, 1, 'ALL', 'ACTIVE',
        'tiab', 'TERM', 'PHRASE', NULL, 'STRING', 'QUERY',
        TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
        '"{{v}}"[TIAB]', NULL, NULL, 0,
        NULL, NULL,
        JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note',
                               'TERM template for TIAB: PHRASE match (quoted)')),
        0, INET6_ATON('0.0.0.0'),
        NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);

-- Render TIAB IN as OR-joined group of quoted items
INSERT INTO patra_registry.reg_prov_expr_render_rule
(id, provenance_id, operation_type, lifecycle_status_code,
 field_key, op_code, match_type_code, negated, value_type_code, emit_type_code,
 effective_from, effective_to,
 template, item_template, joiner, wrap_group,
 params, fn_code,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name,
 updated_at, updated_by, updated_by_name, deleted)
VALUES (900203, 1, 'ALL', 'ACTIVE',
        'tiab', 'IN', NULL, NULL, 'STRING', 'QUERY',
        TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
        NULL, '"{{item}}"[TIAB]', ' OR ', 1,
        NULL, NULL,
        JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note',
                               'IN template for TIAB: OR-joined quoted items with wrap')),
        0, INET6_ATON('0.0.0.0'),
        NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);


-- 4) Global API parameter mappings (endpoint_name = NULL, operation_type = ALL)
--    std_key -> provider_param_name
INSERT INTO patra_registry.reg_prov_api_param_map
(id, provenance_id, operation_type, lifecycle_status_code,
 endpoint_name, std_key, provider_param_name, transform_code, notes,
 effective_from, effective_to,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name,
 updated_at, updated_by, updated_by_name, deleted)
VALUES
    -- Boolean query bridging: std 'query' -> provider 'term'
    (900310, 1, 'ALL', 'ACTIVE',
     NULL, 'query', 'term', NULL, NULL,
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note', 'std query -> term (query bridging)')), 0,
     INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- Date window lower bound
    (900301, 1, 'ALL', 'ACTIVE',
     NULL, 'from', 'mindate', NULL, NULL,
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note', 'std from -> mindate')), 0,
     INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- Date window upper bound (exclusive -> inclusive transform)
    (900302, 1, 'ALL', 'ACTIVE',
     NULL, 'to', 'maxdate', 'TO_EXCLUSIVE_MINUS_1D', NULL,
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note',
                            'std to -> maxdate (exclusive minus 1 day)')), 0, INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- Date type discriminator (PDAT/EDAT)
    (900303, 1, 'ALL', 'ACTIVE',
     NULL, 'datetype', 'datetype', NULL, NULL,
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note', 'std datetype -> datetype')), 0,
     INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- Pagination: limit -> retmax
    (900304, 1, 'ALL', 'ACTIVE',
     NULL, 'limit', 'retmax', NULL, NULL,
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note', 'std limit -> retmax')), 0,
     INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0),

    -- Pagination: offset -> retstart
    (900305, 1, 'ALL', 'ACTIVE',
     NULL, 'offset', 'retstart', NULL, NULL,
     TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
     JSON_ARRAY(JSON_OBJECT('time', '2025-10-14 00:00:00', 'by', '系统管理员', 'note', 'std offset -> retstart')), 0,
     INET6_ATON('0.0.0.0'),
     NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);

-- End of seed

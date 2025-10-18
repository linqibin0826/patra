/* ====================================================================
 * Migration: Add PubMed publication_date support
 * Purpose:
 *   - Add reg_prov_expr_capability for publication_date on PubMed (field already exists from EPMC seed)
 *   - Add reg_prov_expr_render_rule for publication_date RANGE -> PARAMS with PUBMED_DATETYPE
 * Context:
 *   - PubMed supports both entrez_date (edat) and publication_date (pdat)
 *   - PUBMED_DATETYPE function now dynamically selects edat vs pdat based on fieldKey
 * ==================================================================== */

-- 1) Add publication_date capability for PubMed
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
VALUES (900103, 1, 'ALL', 'ACTIVE',
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

-- 2) Add render rule for publication_date RANGE -> PARAMS with PUBMED_DATETYPE
INSERT INTO patra_registry.reg_prov_expr_render_rule
(id, provenance_id, operation_type, lifecycle_status_code,
 field_key, op_code, match_type_code, negated, value_type_code, emit_type_code,
 effective_from, effective_to,
 template, item_template, joiner, wrap_group,
 params, fn_code,
 record_remarks, version, ip_address,
 created_at, created_by, created_by_name,
 updated_at, updated_by, updated_by_name, deleted)
VALUES (900205, 1, 'ALL', 'ACTIVE',
        'publication_date', 'RANGE', NULL, NULL, 'DATE', 'PARAMS',
        TIMESTAMP('2025-10-18 00:00:00.000000'), NULL,
        NULL, NULL, NULL, 0,
        JSON_OBJECT('from', '{{from}}', 'to', '{{to}}', 'datetype', '{{datetype}}'), 'PUBMED_DATETYPE',
        JSON_ARRAY(JSON_OBJECT('time', '2025-10-18 00:00:00', 'by', '系统管理员', 'note',
                               'Emit std params for PubMed publication_date filtering; PUBMED_DATETYPE returns pdat')),
        0, INET6_ATON('0.0.0.0'),
        NOW(6), 1001, '系统管理员', NOW(6), 1001, '系统管理员', 0);

-- End of migration

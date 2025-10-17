-- P6.1.4 — Verify seeds applied (run against patra_registry)

-- PubMed: std_key mappings (query→term, from/to/datetype/pagination)
SELECT std_key, provider_param_name, transform_code
FROM reg_prov_api_param_map m
JOIN reg_provenance p ON p.id = m.provenance_id
WHERE p.provenance_code = 'PUBMED'
ORDER BY std_key;

-- PubMed: TIAB render rules present
SELECT field_key, op_code, match_type_code, emit_type_code, template, item_template, joiner, wrap_group
FROM reg_prov_expr_render_rule r
JOIN reg_provenance p ON p.id = r.provenance_id
WHERE p.provenance_code = 'PUBMED' AND field_key IN ('tiab','entrez_date')
ORDER BY field_key, op_code;

-- EPMC: param maps
SELECT std_key, provider_param_name
FROM reg_prov_api_param_map m
JOIN reg_provenance p ON p.id = m.provenance_id
WHERE p.provenance_code = 'EPMC'
ORDER BY std_key;

-- EPMC: render rule for FIRST_PDATE
SELECT field_key, op_code, template
FROM reg_prov_expr_render_rule r
JOIN reg_provenance p ON p.id = r.provenance_id
WHERE p.provenance_code = 'EPMC' AND field_key = 'publication_date' AND op_code = 'RANGE';

-- Crossref: param maps (query/filter/rows/offset)
SELECT std_key, provider_param_name
FROM reg_prov_api_param_map m
JOIN reg_provenance p ON p.id = m.provenance_id
WHERE p.provenance_code = 'CROSSREF'
ORDER BY std_key;

-- Crossref: PARAMS rule for filter composition
SELECT field_key, op_code, params
FROM reg_prov_expr_render_rule r
JOIN reg_provenance p ON p.id = r.provenance_id
WHERE p.provenance_code = 'CROSSREF' AND field_key = 'publication_date' AND op_code = 'RANGE';

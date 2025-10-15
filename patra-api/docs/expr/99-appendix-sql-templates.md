# 99 — Appendix: SQL Templates (Seeds)

Status: Documentation only (pre‑implementation)
Date: 2025-10-15


## A.1 PubMed — Param Map (std_key=query)

```
INSERT INTO patra_registry.reg_prov_api_param_map
  (id, provenance_id, operation_type, lifecycle_status_code,
   endpoint_name, std_key, provider_param_name, transform_code, notes,
   effective_from, effective_to,
   record_remarks, version, ip_address,
   created_at, created_by, created_by_name,
   updated_at, updated_by, updated_by_name, deleted)
VALUES
  (900310, (SELECT id FROM patra_registry.reg_provenance WHERE code='PUBMED'), 'ALL', 'ACTIVE',
   NULL, 'query', 'term', NULL, NULL,
   TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
   JSON_ARRAY(JSON_OBJECT('note','std query → term')), 0, INET6_ATON('0.0.0.0'),
   NOW(6), 1001, 'system', NOW(6), 1001, 'system', 0);
```

## A.2 PubMed — Date PARAMS placeholders (entrez_date RANGE)

```
-- Use {{from}}, {{to}}, {{datetype}} in params JSON
UPDATE patra_registry.reg_prov_expr_render_rule
SET params = JSON_OBJECT('from','{{from}}','to','{{to}}','datetype','{{datetype}}')
WHERE provenance_id = (SELECT id FROM patra_registry.reg_provenance WHERE code='PUBMED')
  AND field_key = 'entrez_date' AND op_code='RANGE' AND emit_type_code='PARAMS';
```

## A.3 EPMC — Date in Query

```
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
  (910201, (SELECT id FROM patra_registry.reg_provenance WHERE code='EPMC'), 'ALL', 'ACTIVE',
   'publication_date', 'RANGE', NULL, NULL, 'DATE', 'QUERY',
   TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
   'FIRST_PDATE:[{{from}} TO {{to}}]', NULL, NULL, 0,
   NULL, NULL,
   JSON_ARRAY(JSON_OBJECT('note','EPMC date range as query')), 0, INET6_ATON('0.0.0.0'),
   NOW(6), 1001, 'system', NOW(6), 1001, 'system', 0);
```

## A.4 Crossref — Filter PARAMS

```
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
  (920201, (SELECT id FROM patra_registry.reg_provenance WHERE code='CROSSREF'), 'ALL', 'ACTIVE',
   'publication_date', 'RANGE', NULL, NULL, 'DATE', 'PARAMS',
   TIMESTAMP('2025-10-14 00:00:00.000000'), NULL,
   NULL, NULL, NULL, 0,
   JSON_OBJECT('filter','from-pub-date:{{from}},until-pub-date:{{to}}'), NULL,
   JSON_ARRAY(JSON_OBJECT('note','Crossref filter composition')), 0, INET6_ATON('0.0.0.0'),
   NOW(6), 1001, 'system', NOW(6), 1001, 'system', 0);
```

/* ====================================================================
 * Migration: Fix PubMed entrez_date capability kind (DATETIME -> DATE)
 * Context:
 *   - docs/expr/04-provider-pubmed.md §4.3.2 specifies RANGE for date filtering; renderer emits
 *     PARAMS placeholders (from/to/datetype) and mapping applies TO_EXCLUSIVE_MINUS_1D.
 *   - Seed V1.1.1 declared capability range_kind_code='DATETIME' for field 'entrez_date'.
 *   - Tests and examples commonly use LocalDate (DATE) ranges for PubMed windows.
 *
 * Change:
 *   - Update reg_prov_expr_capability to set range_kind_code='DATE' for PUBMED/ALL/entrez_date.
 *   - Update audit fields.
 * ==================================================================== */

UPDATE patra_registry.reg_prov_expr_capability c
JOIN patra_registry.reg_provenance p ON p.id = c.provenance_id
SET c.range_kind_code = 'DATE',
    c.updated_at = NOW(6),
    c.updated_by = 1001,
    c.updated_by_name = '系统管理员',
    c.record_remarks = JSON_ARRAY(
      JSON_OBJECT('time', DATE_FORMAT(NOW(6),'%Y-%m-%d %H:%i:%s'), 'by', '系统管理员',
                  'note', 'Fix entrez_date capability kind to DATE for PubMed'))
WHERE p.provenance_code = 'PUBMED'
  AND c.operation_type = 'ALL'
  AND c.lifecycle_status_code = 'ACTIVE'
  AND c.field_key = 'entrez_date';

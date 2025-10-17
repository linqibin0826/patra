Replaced by: ../07-migration-rollout.md


# 07 — Migration Plan (DDL/Seeds)

Status: Documentation only (pre‑implementation)
Date: 2025-10-15


## 7.1 Principles

- No schema change required. We only update/extend seed migrations.
- Project has no historical data: it is safe to rewrite existing seed files in place.
- Prefer endpoint‑agnostic mappings (`endpoint_name=NULL`) initially; refine by endpoint when needed.


## 7.2 Files to Update/Create

- Update: `patra-registry/patra-registry-infra/src/main/resources/db/migration/V1.1.1__seed_pubmed_expr_config.sql`
  - Normalize PARAMS placeholders to `{{...}}`
  - Add `std_key='query' → provider 'term'` mapping
  - Ensure `to → maxdate` has `transform_code='TO_EXCLUSIVE_MINUS_1D'` when using exclusive `to`

- Add new seeds:
  - `V1.1.2__seed_epmc_expr_config.sql`
  - `V1.1.3__seed_crossref_expr_config.sql`


## 7.3 Seed Content Requirements

### 7.3.1 Field Dictionary

Add or confirm std fields:
- PubMed: `entrez_date` (DATE or DATETIME with range kind = DATETIME)
- EPMC/Crossref: `publication_date` (DATE range)

### 7.3.2 Capabilities

Per provider and field:
- For text fields: allow `TERM` (ANY/PHRASE) and `IN`
- For date fields: allow `RANGE` with appropriate range kind (DATE/DATETIME)

### 7.3.3 Render Rules

- Text: `emit=QUERY` with templates per provider (e.g., PubMed `[TIAB]`)
- Date: choose `emit=QUERY` (EPMC) or `emit=PARAMS` (PubMed, Crossref) based on provider conventions
- For PUBMED date PARAMS: `params={"from":"{{from}}","to":"{{to}}","datetype":"{{datetype}}"}`, `fn_code="PUBMED_DATETYPE"`

### 7.3.4 Param Map

Add at minimum:
- PubMed: `query→term`, `from→mindate`, `to→maxdate (TO_EXCLUSIVE_MINUS_1D)`, `datetype→datetype`, `limit→retmax`, `offset→retstart`
- EPMC: `query→query`, `limit→pageSize` (cursor optional)
- Crossref: `query→query`, `filter→filter`, `limit→rows`, `offset→offset`

Implementation tip:
- Resolve `provenance_id` via subqueries by `code` (e.g., `(SELECT id FROM reg_provenance WHERE code='PUBMED')`) to avoid environment‑specific numeric IDs.

## 7.4 Effective Times & Temporal Slices

- Use a single effective_from baseline (e.g., `TIMESTAMP('2025-10-14 00:00:00.000000')`) for the initial seeds.
- Leave `effective_to` NULL for open‑ended configs.
- CI Consideration: Using a fixed timestamp ensures deterministic ordering across environments; if you choose `NOW(6)`, document the rationale and verify ordering in tests.


## 7.5 Verification Queries (Manual)

After migration on a dev DB, validate (examples; adapt schema/database name accordingly):

```
-- Param map has query mapping for PubMed
SELECT std_key, provider_param_name, transform_code
FROM   patra_registry.reg_prov_api_param_map
WHERE  provenance_id = (SELECT id FROM patra_registry.reg_provenance WHERE code='PUBMED')
  AND  std_key IN ('query','from','to','datetype');

-- Render rules for PubMed date PARAMS
SELECT field_key, op_code, emit_type_code, params, fn_code
FROM   patra_registry.reg_prov_expr_render_rule
WHERE  provenance_id = (SELECT id FROM patra_registry.reg_provenance WHERE code='PUBMED')
  AND  field_key='entrez_date' AND op_code='RANGE';
```


## 7.6 Rollback

- Since this is seed‑only and no live data, rollback is simply re‑applying the previous seed version (if kept) or re‑creating from VCS.

## 7.7 Flyway/DB Hygiene Notes

- The plan assumes a clean database (no historical data). If Flyway has recorded prior migrations, either:
  - Drop and recreate the schema (dev only), or
  - Bump seed versions and avoid editing existing applied migrations.
- Ensure `baselineOnMigrate=false` (default); do not baseline over existing data for these changes.

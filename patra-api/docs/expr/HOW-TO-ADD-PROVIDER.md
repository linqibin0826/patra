# How To Add a New Provider (Recipe)

Status: Ready to use
Date: 2025-10-17

This guide explains how to onboard a new literature provider into the Expression Compiler‑Bridge without changing application code. All behavior lives in registry seeds + configuration.

References: `docs/expr/01-overview.md`, `docs/expr/02-architecture.md`, `docs/expr/03-compiler-bridge-internals.md`, `docs/expr/12-provider-checklist.md`.

---

## 1) Create/Verify Provenance

- Ensure an entry exists in `reg_provenance` with a stable `code` (e.g., `SCOPUS`).
- Decide endpoints/operations you support now (usually `SEARCH`), expand later via `operation_type`/`endpoint_name`.

## 2) Define Field Dictionary (std semantics)

- Insert unified fields into `reg_expr_field_dict` (e.g., `text`, `publication_date`, `journal`).
- Choose `data_type_code` (DATE/DATETIME/TEXT/NUMBER) and `cardinality_code` (SINGLE/MULTI).

## 3) Declare Capabilities

- For each field, add rows to `reg_prov_expr_capability` with allowed operations:
  - Text: TERM (ANY/PHRASE), IN
  - Date: RANGE (DATE or DATETIME)

## 4) Author Render Rules

- Choose emit type per field/op:
  - Text → `emit_type_code=QUERY` with templates like `"{{v}}"`
  - Date → either `QUERY` (fielded range) or `PARAMS` (std keys)
- Use `{{...}}` placeholders; never hardcode provider param names.
- If needed, set `fn_code` (e.g., `PUBMED_DATETYPE`) to derive placeholders.
- Consider OR/NOT sensitivity; add negation variants if provider needs special templates.

## 5) Map Std Keys → Provider Params

- Insert into `reg_prov_api_param_map` at minimum:
  - `std_key='query'` → provider boolean query param (e.g., `term`, `query`)
  - Pagination: `limit`, `offset` (or `cursor` when applicable)
  - Date params: `from`, `to`, plus transforms (e.g., `TO_EXCLUSIVE_MINUS_1D`)
  - Filters: `filter` (MULTI)
- Decide how MULTI std_keys are serialized:
  - Default: join transforms (LIST_JOIN/FILTER_JOIN)
  - Optional: enable repeats by config `expr.multi.repeat-enabled=true` (not default)

## 6) Effective Times

- Use a fixed `effective_from` (e.g., `TIMESTAMP('2025-10-14 00:00:00.000000')`) across inserts.
- Keep `effective_until` NULL.

## 7) Verify With SQL

Run quick checks after applying seeds:

```sql
-- Confirm query mapping exists
SELECT std_key, provider_param_name
FROM reg_prov_api_param_map
WHERE provenance_id = (SELECT id FROM reg_provenance WHERE code='SCOPUS')
  AND std_key IN ('query','from','to','limit','offset');
```

## 8) Test

- Unit: renderer (placeholders, OR/NOT) and compiler (bridging, transforms, STRICT mode).
- Integration: snapshot load tests, end‑to‑end adapter param binding.
- Golden: freeze snapshot/expr/expected params under `src/test/resources/golden/`.

## 9) Configure Runtime Guardrails

- Dev (`application-dev.yaml`): `expr.strict=false`, no query/param limits.
- Prod (`application-prod.yaml`): `expr.strict=true`, `max-query-length: 5000`, `warn-param-count: 50`, `max-param-count: 100`.

## 10) Roll Out

- Apply seeds to dev DB → smoke tests → stage → prod.
- Monitor error/warning rates. Adjust seeds without code changes.

---

Tips:
- Keep render rules free of provider param names. Mapping belongs in `reg_prov_api_param_map`.
- Prefer join transforms for MULTI until repeated serialization is fully verified end‑to‑end.
- Use STRICT mode in staging to catch incomplete seeds early.

# 04 — Provider: PubMed

Status: Documentation only (pre‑implementation)
Date: 2025-10-15


## 4.1 Summary

- Boolean query carrier: `term`
- Date filters: `datetype` + `mindate` + `maxdate`
- Pagination: `retmax` (limit), `retstart` (offset)
- Common field qualifier: `[TIAB]` for title/abstract (examples)
- Invariant: Renderer must not map provider parameter names; compiler performs std_key→provider mapping and query bridging.


## 4.2 std_key Mapping (param map)

Global (endpoint_name = NULL unless otherwise noted):

- `query → term` (bridge target)
- `from → mindate`
- `to → maxdate` (with `transform_code=TO_EXCLUSIVE_MINUS_1D` if we standardize `to` as exclusive)
- `datetype → datetype` (value provided by rule function `PUBMED_DATETYPE`)
- `limit → retmax`
- `offset → retstart`


## 4.3 Render Rules

### 4.3.1 TIAB text

- TERM ANY: `emit=QUERY`, `template="{{v}}[TIAB]"`
- TERM PHRASE: `emit=QUERY`, `template="\"{{v}}\"[TIAB]"`
- IN: `emit=QUERY`, `item_template="{{quoted}}[TIAB]"`, `joiner=" OR "`, `wrap_group=true`

### 4.3.2 Date range

- Field: `entrez_date`
- RANGE (DATE or DATETIME): `emit=PARAMS`
- `params = {"from":"{{from}}", "to":"{{to}}", "datetype":"{{datetype}}"}`
- `fn_code = "PUBMED_DATETYPE"`


## 4.4 Compiler‑Bridge Behavior

- After rendering, the compiler injects aggregated boolean query into `term` via `std_key=query` mapping (if not already set by a PARAMS rule).


## 4.5 Examples

Expr (JSON):
```
{
  "and": [
    { "term": { "field": "tiab", "value": "heart failure", "match": "PHRASE" } },
    { "range": { "field": "entrez_date", "from": "2023-01-01", "to": "2023-12-31" } }
  ]
}
```
Result:
- Query fragments → `"heart failure"[TIAB]`
- std_key params → `{from:"2023-01-01", to:"2023-12-31", datetype:"pdat"}`
- Mapped params → `{term:"\"heart failure\"[TIAB]", mindate:"2023-01-01", maxdate:"2023-12-30", datetype:"pdat"}`

NOT/OR example:
```
{
  "and": [
    { "term": { "field": "tiab", "value": "cancer", "match": "ANY" } },
    {
      "or": [
        { "term": { "field": "tiab", "value": "therapy", "match": "ANY" } },
        { "not": { "term": { "field": "tiab", "value": "surgery", "match": "ANY" } } }
      ]
    }
  ]
}
```
Aggregated query (template-dependent):
`cancer[TIAB] AND ("therapy"[TIAB] OR NOT("surgery"[TIAB]))`


## 4.6 Seed Migration Edits

File: `patra-registry/patra-registry-infra/src/main/resources/db/migration/V1.1.1__seed_pubmed_expr_config.sql`

- Normalize PARAMS placeholders to `{{from}}`, `{{to}}`, `{{datetype}}`.
- Insert into `reg_prov_api_param_map` a row: `std_key='query'`, `provider_param_name='term'`.
- Keep/ensure `to → maxdate` has `transform_code='TO_EXCLUSIVE_MINUS_1D'` (if we standardize exclusive upper bound).


## 4.7 Edge Cases & Notes

- OR/NOT: PubMed supports boolean logic in `term`; rules will generate `(...) AND/OR ...` with quoted phrases and field tags.
- Datetype choice: default to `pdat` initially; configurable via function in future (e.g., operation/endpoint‑aware).

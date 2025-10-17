Quick reference: ../04-providers-quickref.md


# 06 — Provider: Crossref

Status: Documentation only (pre‑implementation)
Date: 2025-10-15


## 6.1 Summary

- Boolean query carrier: `query`
- Filters: `filter` parameter combines comma‑separated key:value pairs (e.g., `from-pub-date:YYYY-MM-DD,until-pub-date:YYYY-MM-DD`)
- Pagination: `rows` (limit), `offset` (offset)
- Invariant: Renderer must not map provider parameter names; compiler performs std_key→provider mapping and query bridging.


## 6.2 std_key Mapping (param map)

Global (endpoint_name = NULL unless specified):

- `query → query` (bridge target)
- `filter → filter`
- `limit → rows`
- `offset → offset`


## 6.3 Render Rules

### 6.3.1 Text

- TERM ANY: `emit=QUERY`, `template="{{v}}"`
- TERM PHRASE: `emit=QUERY`, `template="\"{{v}}\""`
- IN: `emit=QUERY`, `item_template="\"{{v}}\""`, `joiner=" OR "`, `wrap_group=true`

### 6.3.2 Date range via filter

- Field: `publication_date` (std)
- RANGE (DATE): `emit=PARAMS`, `params={"filter":"from-pub-date:{{from}},until-pub-date:{{to}}"}`
  - Optionally apply date normalizers if format needs adjustment.


## 6.4 Compiler‑Bridge Behavior

- Aggregated boolean query is bridged to `query` via `std_key=query` mapping.
- PARAMS rule contributes `filter` std_key; compiler maps it to `filter` provider param as is (or after transforms).


## 6.5 Examples

Expr (JSON):
```
{
  "and": [
    { "term": { "field": "text", "value": "machine learning", "match": "PHRASE" } },
    { "range": { "field": "publication_date", "from": "2022-01-01", "to": "2022-12-31" } }
  ]
}
```
Result:
- Aggregated query → `"machine learning"`
- std_key params → `{filter:"from-pub-date:2022-01-01,until-pub-date:2022-12-31"}`
- Mapped → `{query:"\"machine learning\"", filter:"from-pub-date:2022-01-01,until-pub-date:2022-12-31", rows:..., offset:...}`

NOT/OR example:
```
{
  "or": [
    { "term": { "field": "text", "value": "AI", "match": "ANY" } },
    { "not": { "term": { "field": "text", "value": "survey", "match": "ANY" } } }
  ]
}
```
Aggregated: `("AI" OR NOT("survey"))`
Mapped: `{query:"(\"AI\" OR NOT(\"survey\"))"}`

## 6.6 Seed Migration (New)

- Field dictionary: add `publication_date` std field if not present.
- Capability: allow RANGE(DATE) on that field; TERM/IN on free text field.
- Render rules: as above (PARAMS for filter; QUERY for text).
- Param map: as above (`query`, `filter`, `rows`, `offset`).


## 6.7 Edge Cases & Notes

- Crossref supports multiple filters; the simplest approach uses a single `filter` std_key and composes the string in the PARAMS rule. For multiple independent filters, either:
  - emit multiple std_keys and join with a function/transform later, or
  - emit a single composed `filter` value through a function.

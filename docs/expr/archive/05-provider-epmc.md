Quick reference: ../04-providers-quickref.md


# 05 — Provider: Europe PMC

Status: Documentation only (pre‑implementation)
Date: 2025-10-15


## 5.1 Summary

- Boolean query carrier: `query` (Lucene‑like syntax)
- Date filters: commonly fielded within the query (e.g., `FIRST_PDATE:[YYYY-MM-DD TO YYYY-MM-DD]`); may vary by dataset/scope
- Pagination: `pageSize`, and optionally `cursorMark` (endpoint‑specific)
- Invariant: Renderer must not map provider parameter names; compiler performs std_key→provider mapping and query bridging.


## 5.2 std_key Mapping (param map)

Global (endpoint_name = NULL unless specified otherwise):

- `query → query` (bridge target)
- `limit → pageSize`
- Optional: `cursor → cursorMark` (if the chosen endpoint uses cursor)


## 5.3 Render Rules

### 5.3.1 Text

- Free text (TERM ANY): `emit=QUERY`, `template="{{v}}"`
- Phrase (TERM PHRASE): `emit=QUERY`, `template="\"{{v}}\""`
- IN: `emit=QUERY`, `item_template="\"{{v}}\""`, `joiner=" OR "`, `wrap_group=true`

### 5.3.2 Date range in query

- Field: `publication_date` (std field key)
- RANGE (DATE): `emit=QUERY`, `template="FIRST_PDATE:[{{from}} TO {{to}}]"`
  - Adjust field to EPMC’s actual field if different (check endpoint dataset).


## 5.4 Compiler‑Bridge Behavior

- Aggregated boolean query is bridged into `query` param via `std_key=query` mapping.


## 5.5 Examples

Expr (JSON):
```
{
  "and": [
    { "term": { "field": "text", "value": "cancer", "match": "ANY" } },
    { "range": { "field": "publication_date", "from": "2024-01-01", "to": "2024-06-01" } }
  ]
}
```
Result:
- Query fragments → `cancer` and `FIRST_PDATE:[2024-01-01 TO 2024-06-01]`
- Aggregated → `cancer AND FIRST_PDATE:[2024-01-01 TO 2024-06-01]`
- Mapped params → `{query:"cancer AND FIRST_PDATE:[2024-01-01 TO 2024-06-01]", pageSize:...}`

NOT/OR example:
```
{
  "and": [
    { "term": { "field": "text", "value": "neoplasm", "match": "ANY" } },
    {
      "or": [
        { "term": { "field": "text", "value": "gene", "match": "ANY" } },
        { "not": { "term": { "field": "text", "value": "mouse", "match": "ANY" } } }
      ]
    }
  ]
}
```
Aggregated (template-dependent): `neoplasm AND ("gene" OR NOT("mouse"))`

## 5.6 Seed Migration (New)

- Field dictionary: add `publication_date` std field.
- Capability: allow RANGE(DATE) on `publication_date`; TERM/IN on free text field.
- Render rules: as above (QUERY‑based for dates and text).
- Param map: `query → query`, `limit → pageSize` (plus cursor if needed).


## 5.7 Edge Cases & Notes

- If EPMC endpoint expects date filters as separate params (less common), model them via `emit=PARAMS` + mapping instead of query fragments.
- Validate OR/NOT in mixed expressions to ensure the expected parentheses are emitted.

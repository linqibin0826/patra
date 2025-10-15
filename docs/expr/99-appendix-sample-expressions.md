# 99 — Appendix: Sample Expressions

Status: Documentation only (pre‑implementation)
Date: 2025-10-15


## B.1 Phrase + Date (PubMed)

```
{
  "and": [
    { "term": { "field": "tiab", "value": "heart failure", "match": "PHRASE" } },
    { "range": { "field": "entrez_date", "from": "2023-01-01", "to": "2023-12-31" } }
  ]
}
```
Expected:
- query fragments: `"heart failure"[TIAB]`
- std_keys: `from`, `to`, `datetype`
- mapped: `term`, `mindate`, `maxdate`, `datetype`


## B.2 Mixed Boolean (EPMC)

```
{
  "and": [
    { "term": { "field": "text", "value": "cancer", "match": "ANY" } },
    {
      "or": [
        { "term": { "field": "text", "value": "therapy", "match": "ANY" } },
        { "not": { "term": { "field": "text", "value": "surgery", "match": "ANY" } } }
      ]
    }
  ]
}
```
Expected aggregated query (example):
`cancer AND ("therapy" OR NOT("surgery"))` (exact quoting depends on configured templates).


## B.3 Phrase + Date Filter (Crossref)

```
{
  "and": [
    { "term": { "field": "text", "value": "machine learning", "match": "PHRASE" } },
    { "range": { "field": "publication_date", "from": "2022-01-01", "to": "2022-12-31" } }
  ]
}
```
Expected:
- query: `"machine learning"`
- std_key `filter`: `from-pub-date:2022-01-01,until-pub-date:2022-12-31`
- mapped: `query`, `filter`

## B.4 Multiple Filters (MULTI with Join)

```
{
  "and": [
    { "term": { "field": "text", "value": "genomics", "match": "ANY" } },
    { "range": { "field": "publication_date", "from": "2021-01-01", "to": "2021-12-31" } },
    { "term": { "field": "journal", "value": "Nature", "match": "ANY" } }
  ]
}
```
Example strategy:
- Emit std_key `filter` twice via PARAMS rules:
  - `from-pub-date:{{from}},until-pub-date:{{to}}`
  - `container-title:{{v}}` (from `journal` term)
- Define `filter` as MULTI.
- Apply a join transform (e.g., `FILTER_JOIN` → comma‑separated) in the compiler mapping stage.
Expected provider params:
`{ query:"genomics", filter:"from-pub-date:2021-01-01,until-pub-date:2021-12-31,container-title:Nature" }`

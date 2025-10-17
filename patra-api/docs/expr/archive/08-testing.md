Replaced by: ../08-testing-and-smoke.md


# 08 — Testing & Validation

Status: Documentation only (pre‑implementation)
Date: 2025-10-15


## 8.1 Test Levels

- Unit (starter):
  - Renderer: OR/NOT behavior, rule selection, PARAMS `fn_code`, placeholders.
  - Compiler: query bridge, `transform_code`, query length error, warnings merged.
- Integration (boot modules):
  - Registry: snapshot endpoints return fields/capabilities/rules/param maps as seeded.
  - Ingest: compile → provider request assembly for PubMed/EPMC/Crossref.
- Architecture: ArchUnit ensures layer boundaries (domain purity).


## 8.2 Unit Test Matrix (Starter)

Renderer
- AND only: two TERM atoms → `fragA AND fragB`
- OR only: two TERM atoms → `(fragA OR fragB)`
- Mixed AND/OR: `A AND (B OR C)` parentheses preserved
- NOT: `NOT(A)` selects `negation=TRUE` rule or templated negation
- PARAMS placeholders: `{{from}}/{{to}}/{{datetype}}` replaced
- `fn_code`: `PUBMED_DATETYPE` returns `pdat`
- Cardinality:
  - SINGLE std_key emitted multiple times → last‑write‑wins by rule priority
  - MULTI std_key emits multiple values → verify collection at renderer output (std_keys only)

Compiler
- Bridge: `query` present → mapped to provider name (`term` or `query`)
- Transform: `TO_EXCLUSIVE_MINUS_1D` converts `to` before mapping
- Missing param map: warning `W-PARAM-MAP-MISSING`
- Query length guard: `E-QUERY-LEN-MAX`
- MULTI join: apply `LIST_JOIN`/`FILTER_JOIN` transform; verify final single provider param value
- MULTI repeat (if/when supported): verify repeated provider parameters or internal representation


## 8.3 Integration Tests (Boot)

Registry
- Load snapshot for PUBMED/EPMC/CROSSREF:
  - Contains expected fields/capabilities/render rules/param maps

Ingest
- Given expression + provenance code:
  - PubMed phrase + date range:
    - Request contains `term` + `mindate/maxdate/datetype` (maxdate adjusted)
  - EPMC free text + date window:
    - Request contains `query` with date fragment
  - Crossref phrase + date window:
    - Request contains `query` and `filter` keys


## 8.4 Fixtures & Stubs

- Snapshot stubs: fixed JSON DTOs reflecting seeds (for starter unit tests).
- Provider clients: mock HTTP clients or wiremock stubs returning minimal JSON structures to assert request lines.


## 8.5 Observability Assertions

- Logs:
  - Renderer: rule hits and misses
  - Compiler: query bridge log line; transform applications
  - Redaction: INFO logs redact/hash queries; full payloads only at DEBUG in non‑prod
- Metrics (if present):
  - `expr.render.rule_miss{provenance,endpoint}`, `expr.param.map_miss{provenance,endpoint}`, `expr.transform.applied{provenance,endpoint,code}`
  - `expr.render.rule_hits{provenance,endpoint}`, `expr.param.map_hit{provenance,endpoint}`, `expr.compile.errors{code}`


## 8.6 Performance

- Bench a typical complex query (50–100 atoms, nested OR/NOT). Ensure compile under 50ms on dev hardware.
- Memory footprint: verify no large retained objects post compile (run simple heap allocation tests).

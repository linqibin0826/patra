# Testing and Smoke Guide

Status: consolidated from 08-testing.md, 12-golden-test-harness.md, and smoke/SMOKE-TESTS.md (2025-10-17)

Scope
- Unit tests (renderer/compiler in isolation)
- Golden test harness (JSON→expected query/params)
- Smoke tests (assemble provider requests; no upstream calls)

Unit Tests
- Domain/Application: focus on behavior; no framework dependencies in domain.
- Verify OR/NOT rendering, SINGLE/MULTI merge semantics, `fn_code` execution ordering.

Golden Test Harness
- Inputs: expression JSON + expected std_key outputs + final provider params.
- Required coverage
  - Deep OR/NOT combinations
  - PubMed date RANGE with exclusive `to` transform
  - Crossref filter join (from/until + extra filters)
  - STRICT vs non-STRICT behavior for unsupported NOT
- Example structure: see docs/expr/smoke/samples/*.json

Smoke Tests (JUnit opt-in)
Prereqs
- Registry running locally with seeds; see registry-verification.sql
- Build succeeds and starter is on classpath

Run
```
RUN_SMOKE=1 mvn -q -pl patra-ingest/patra-ingest-boot -Dtest=ExprSmokeTest test
```

What it checks
- PubMed: `term` bridged, `datetype=pdat`, `mindate/maxdate` present with transform.
- Europe PMC: boolean query arrives in `query`.
- Crossref: phrase in `query` + composed `filter`.
- STRICT vs non-STRICT: NOT on unsupported range → error vs warning.

Observability
- Logging: INFO summary with redacted query hash/length; DEBUG for transform/bridge steps.
- Metrics (bounded labels `{provenance,endpoint}`)
  - `expr.param.map_hit`, `expr.param.map_miss`
  - `expr.transform.applied{code}`
  - `expr.compile.errors{code}`
  - `expr.compile.duration_ms`

Artifacts
- Samples: docs/expr/smoke/samples/
- SQL verification: docs/expr/smoke/registry-verification.sql

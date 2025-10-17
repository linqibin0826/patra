# Smoke Tests — Phase 6

Status: Ready to run (requires running registry + dev DB)
Date: 2025-10-17

This guide executes Phase 6: Rollout & Smoke Testing tasks (P6.1–P6.3) using the code and seeds in this repo.

Prereqs
- Registry DB has Flyway V1.1.x seeds applied (PubMed/EPMC/Crossref) and `patra-registry-boot` is running at http://localhost:6000.
- `Papertrace-api` builds successfully and `patra-ingest-boot` can discover `patra-registry` via Nacos or direct config.

P6.1 — Database Migrations
1) Apply migrations (already done by user per context). If needed: start `patra-registry-boot` to let Flyway run.
2) Verify with SQL:
   - Run the queries in `docs/expr/smoke/registry-verification.sql` against `patra_registry`.
   - Expect to see:
     - PubMed `std_key -> term` mapping (id≈900310), `to -> maxdate` with `TO_EXCLUSIVE_MINUS_1D`.
     - EPMC `query -> query`, `limit -> pageSize`.
     - Crossref `query -> query`, `filter -> filter`, `limit -> rows`, `offset -> offset`.

P6.2 — Smoke Compilation
We added an opt-in JUnit smoke suite that compiles sample expressions and assembles provider requests.

- Samples live in `docs/expr/smoke/samples/`:
  - `pubmed_phrase_date.json`
  - `epmc_mixed_boolean.json`
  - `crossref_phrase_date.json`
  - `strict_not_on_range_pubmed.json` (for STRICT vs non-STRICT)

Run
```
RUN_SMOKE=1 mvn -q -pl patra-ingest/patra-ingest-boot -Dtest=ExprSmokeTest test
```

What it checks
- P6.2.1 PubMed: query contains `"heart failure"[TIAB]`; params include `term/mindate/maxdate/datetype(pdat)` and the `maxdate` exclusive→inclusive transform.
- P6.2.2 EPMC: bridged boolean query arrives in provider `query` param.
- P6.2.3 Crossref: phrase in `query` + composed `filter=from-pub-date:...,until-pub-date:...`.
- P6.2.4 Adapter assembly: builds provider request objects and final gateway URL; verifies encoded query/params.
- P6.2.5/6 STRICT vs non-STRICT: NOT over PubMed date RANGE → warning in non-STRICT; error in STRICT.

P6.3 — Observability Validation
Dev profile now enables DEBUG logging for the compiler:
```
patra-ingest/patra-ingest-boot/src/main/resources/application-dev.yaml
logging.level.com.patra.starter.expr.compiler=DEBUG
```
Check logs while running smoke suite:
- INFO line: `Compiled expr ... queryHash=... queryLen=... paramCount=...` (hash only, redacted query).
- DEBUG lines: `Applied transform: ...`, `Bridged query into provider param ...`, and final params dump.

Metrics (if a MeterRegistry is present):
- `expr.param.map_hit{provenance,endpoint}` increments for each mapped key and query bridge.
- `expr.transform.applied{code}` increments for `TO_EXCLUSIVE_MINUS_1D`, `FILTER_JOIN`, etc.

Notes
- The smoke suite is intentionally lightweight and read-only: it compiles expressions and constructs request URLs but does not call upstream providers.
- For end-to-end request stubbing, consider adding WireMock in a follow-up to assert egress request-lines.

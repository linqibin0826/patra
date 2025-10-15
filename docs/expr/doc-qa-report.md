# Expression Compiler‑Bridge — Final Documentation QA Report

Status: Documentation‑only audit (no code)
Date: 2025-10-15
Reviewer: Codex (GPT‑5)
Scope: Link/anchor integrity, terminology/contracts, renderer/compiler boundary, examples, provider readiness, observability/security, migration, golden tests, performance/limits, failure modes, compatibility, concurrency/ordering.


## A. Final Readiness Checklist

1) Link & Anchor Integrity — Pass
- Index points to all section files (01–06, 07, 08, 09, 10, 11, 12x2, 99x2). No missing files.
- Cross‑refs are file‑level (not deep anchors) and resolve by path.

2) Terminology & Contracts Consistency — Pass
- std_key, SINGLE/MULTI, fn_code vs transform_code, `{{...}}` placeholders, and the execution order are consistent across 01/02/03/04/05/06/08/11.
- Execution order is formalized in 03 with a clear diagram.

3) Renderer/Compiler Boundary — Pass
- 02 and 03 assert compiler‑only mapping; renderer emits std_keys only.
- Provider docs 04/05/06 include the invariant callout (“Renderer must not map provider names”). Acceptance criteria 11 reiterates.

4) Examples Round‑Trip — Pass (by inspection; to be validated via goldens)
- 99 appendix examples match provider docs’ expected outputs:
  - PubMed: TIAB phrase + date → `term` + `mindate/maxdate/datetype` (upper bound minus 1 day).
  - EPMC: text + date in query → single `query` param with fielded date.
  - Crossref: phrase + date → `query` + composed `filter`; MULTI filter join case covered.
- NOT/OR nesting examples added for each provider.

5) Provider Checklist Applied — Pass (with notes)
- PubMed: ✔ datetype via fn_code (`PUBMED_DATETYPE`), query→term mapping, exclusive upper bound transform, pagination mapping.
- EPMC: ✔ FIRST_PDATE range in query; note dataset‑specific field variations (documented).
- Crossref: ✔ filter composition via PARAMS + join strategy, query mapping, pagination mapping.

6) Observability & Security — Pass
- 02/03/08/09 enumerate INFO redaction (hash/truncate), DEBUG full dumps non‑prod only, bounded metric names, and log points for renderer/ compiler.

7) Migration Safety — Pass
- 07 documents subquery by provenance code (no hard IDs), effective_from guidance, Flyway hygiene (clean DB assumption), and rollback/replay notes. Idempotency for seeds is implicit in “clean DB” context; re‑runs in dirty DB should use new versions.

8) Golden Tests — Pass
- 12‑golden‑test‑harness documents fixture layout, harness flow, normalization, update policy, CI integration, and failure diffing expectations.

9) Performance & Limits — Pass (with guardrails)
- 03 states `maxQueryLength` behavior (E‑QUERY‑LEN‑MAX, fail; no truncation). Perf target in 08 (<50ms typical case).
- Parameter count: 03 advises adding a soft warning threshold (W‑PARAM‑COUNT‑LIMIT) and optional hard limit E‑PARAM‑COUNT‑LIMIT if required; strategy to reduce via MULTI+join.

10) Failure Modes & Operator Action — Pass
- 03.4 enumerates: W‑PARAM‑MAP‑MISSING, W‑RENDER‑RULE‑MISSING, E‑QUERY‑LEN‑MAX, W‑FN‑OR‑TRANSFORM‑NOTFOUND. Operator action: adjust seeds (maps/rules/functions/transforms) and rerun; for E‑QUERY‑LEN‑MAX, refactor expr or add transforms.

11) Backwards/Compatibility — Pass
- 11 criteria forbid adapter‑side query construction; 09 rollout provides a temporary diagnostic fallback (toggle bridge) with the intent to return to compiler‑only mapping.

12) Concurrency/Ordering — Pass
- 03 declares registries immutable and per‑compile rendering; merge policy deterministic (priority‑based last‑write‑wins for SINGLE; join/repeat for MULTI). Ordering semantics are documented.


## B. Residual Nits (Non‑blocking)

1) Datetype selection nuance (PubMed): `PUBMED_DATETYPE` currently defaults to `pdat`; some endpoints prefer `edat`. The docs note future configurability; consider adding an example seed demonstrating endpoint‑aware datetype.

2) MULTI repeat strategy: Docs prioritize join. If any provider needs repeated parameters, add an encoder note in adapters documentation (later) to ensure repeated param serialization is supported.

3) Param count thresholds: The docs recommend thresholds but do not assign defaults. Decide project defaults (e.g., warn at 100, error at 200) during implementation PR review if needed.

4) Metric cardinality: Ensure provenance and endpoint labels are bounded; consider whitelisting label sets in metrics config (Ops doc, later).


## C. Go/No‑Go Recommendation

Recommendation: Go

Rationale:
- The documentation is internally consistent, complete for implementation, and covers boundary conditions (NOT/OR, functions/transforms, merge policy, observability, migration, golden testing).
- Must‑fix items from the previous review have been integrated (compiler‑only mapping, execution order, merge policy, logging/redaction).
- Remaining nits are minor and can be addressed during implementation PRs without changing the core design.

Pre‑implementation reminder:
- During the code PRs, enforce the invariant: renderer must not consult `apiParameterMap`; all mapping and transforms reside in the compiler. Add tests to prevent regressions (golden + unit).

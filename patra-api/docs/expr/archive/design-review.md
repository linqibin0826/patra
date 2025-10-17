Note: This document was moved to docs/expr/archive on 2025-10-17 as part of consolidation.
For current guidance, start with docs/expr/START-HERE.md.

# Expression Compiler‑Bridge Design — Technical Review

Status: Review only (no code/doc changes applied)
Date: 2025-10-15
Reviewer focus: Architecture, correctness, edge cases, scalability, observability, rollout


## 1) Executive Summary

Overall, the compiler‑bridge approach is sound and aligns with hexagonal + DDD boundaries: normalize boolean query to std_key=query, map via registry, and keep adapters thin. The plan leverages existing schema (capabilities, render rules, param map) and adds minimal engine features (OR/NOT, `fn_code`, `transform_code`).

Key must‑fix items before implementation:
- Responsibility split: the current code maps std_keys to provider names inside the renderer; the design assumes mapping happens in the compiler. This must be reconciled to avoid double‑mapping and inconsistent transform execution.
- Execution order: clarify and enforce the exact order for placeholder resolution → fn_code → std_key emission → std_key→provider mapping → transform_code → final params. Any ambiguity here will lead to subtle bugs.
- Multi‑value and merge semantics: define behavior when multiple atoms emit the same std_key (e.g., multiple date ranges, multiple filters). Without a policy, results may be non‑deterministic.

Other risks: provider boolean semantics (NOT/OR) fidelity, date semantics (inclusive/exclusive, timezone), query length/encoding, and observability details (sensitive logs). These are solvable with clear rules and tests.


## 2) Section‑By‑Section Review

### 2.1 01 — Overview
- Strengths
  - Clear problem statement; crisp goals and non‑goals.
  - Explicit definition of std_key=query as first‑class output.
- Issues
  - Glossary does not define “merge policy” for duplicate std_keys nor cardinality (single vs multi).
  - “Renderer executes fn_code before final param substitution” is ambiguous (see ordering below).
- Recommendations
  - Add a mini “value lifecycle” diagram with exact ordering and data shapes.
  - Document std_key cardinality: `SINGLE` vs `MULTI` and how MULTI maps to repeated provider params or joined values.

### 2.2 02 — Architecture
- Strengths
  - Clean layering; registry is the SSOT; adapters thin.
  - Data flow captures snapshot → normalize → validate → render → compile.
- Issues
  - Current implementation mismatch: `DefaultExprRenderer.applyParams()` already maps std_key → provider name using `apiParameterMap`. The design moves mapping to the compiler. If not harmonized, you’ll get double insertion and transform ordering bugs.
  - Observability mentions metrics but does not state stable metric names/cardinality strategy.
- Recommendations
  - Choose one canonical place to perform mapping (compiler). Refactor renderer to emit only std_keys. Update docs to state “renderer does not consult `apiParameterMap`.”
  - Define a minimal metric set: `expr.render.rule_hits`, `expr.render.rule_miss`, `expr.param.map_hit`, `expr.param.map_miss`, `expr.transform.applied`, keyed by provenanceCode/endpointName (bounded cardinality).

### 2.3 03 — Compiler‑Bridge Internals
- Strengths
  - Solid pseudocode; clarifies where transforms apply and where bridging occurs.
  - Proper error/warn taxonomy and configuration toggles.
- Issues
  - Execution order not fully pinned: the text and pseudocode imply fn_code applies in renderer, but renderer still maps provider names today. Needs final single definition.
  - No policy for multiple std_key occurrences (e.g., two `filter` keys or two `from`/`to` from different atoms). Overwrite vs append is unspecified.
  - No explicit escaping/quoting policy at compile time for provider params (renderer handles quotes for query fragments only).
- Recommendations
  - Finalize ordering (proposed):
    1) Renderer: compute placeholders from atom → apply `fn_code` (rule‑level) to placeholders → expand PARAMS templates → emit std_key/value(s) only; accumulate QUERY fragments.
    2) Compiler: aggregate QUERY → bridge via std_key=query; map all std_keys → provider names; apply `transform_code` to each mapped value; finalize Map<String,String> (or Map<String,List<String>> if MULTI).
  - Define merge policy per std_key:
    - SINGLE (default): last‑write‑wins with deterministic priority (rule priority, or field/op precedence).
    - MULTI: collect multiple values into list → either join via transform or emit repeated params (needs HTTP layer support).
  - Add explicit value escaping policy for provider params when values come from PARAMS rules (e.g., URL encoding is done later by HTTP client, but string quoting/escaping must be correct if providers require embedded quoting).

### 2.4 04 — Provider: PubMed
- Strengths
  - Correctly identifies `term` as the boolean query carrier.
  - Encodes date via PARAMS with `datetype` and exclusive‑to transform.
- Issues
  - `PUBMED_DATETYPE` default to `pdat` may not always be correct (e.g., `edat` for some operations). Missing a config hook to select based on endpoint/operation.
  - The exclusive upper bound logic may not match PubMed semantics for all `datetype` combos (needs verification).
- Recommendations
  - Let `PUBMED_DATETYPE` accept `(fieldKey, operationType, endpoint)` context and consult snapshot/seeded defaults.
  - Add a test matrix for `pdat` vs `edat`, verifying count/list endpoints.

### 2.5 05 — Provider: Europe PMC
- Strengths
  - Uses fielded date range in query string; accurate to common usage.
- Issues
  - `FIRST_PDATE` choice is a convention—some datasets use different date fields. Without endpoint scoping in rules, it may be too rigid.
- Recommendations
  - Bind the date field name per endpoint via render rules (already supported). Document endpoint‑specific deltas and add tests.

### 2.6 06 — Provider: Crossref
- Strengths
  - Correct: `filter` parameter composes from multiple keys; `query` separate.
- Issues
  - Only a single `filter` std_key is described. If multiple filters are needed, we need either a MULTI policy or composition function.
- Recommendations
  - Provide an example using MULTI `filter` std_key values combined by a `LIST_JOIN` transform (or dedicated `FILTER_JOIN`) at mapping time.

### 2.7 07 — Migration Plan
- Strengths
  - Clear file list and verification queries.
- Issues
  - Rewriting an existing migration (V1.1.1) is only safe on clean databases; note Flyway baseline/clean assumptions explicitly.
  - Effective_from hard‑coded to a fixed timestamp; if any CI runs cross midnight, ordering could differ. Not critical, but worth noting.
- Recommendations
  - Add “requires clean database / baseline-on-migrate=false” note for Flyway.
  - Consider setting effective_from to a function like `NOW(6)` if reproducibility isn’t a requirement; otherwise keep fixed and document.

### 2.8 08 — Testing
- Strengths
  - Good unit/integration coverage outline; observability assertions included.
- Issues
  - No golden test artifacts to prevent template drift.
  - No negative tests for transform/function missing or invalid codes.
  - No load/perf test guidance for large expressions.
- Recommendations
  - Add golden tests with frozen snapshots + expressions → expected compiled outputs (query + params). Store expected JSON files.
  - Add failure‑mode tests: missing mappings, missing functions/transforms, malformed templates.
  - Add perf microbench for 100–500 atom expressions.

### 2.9 09 — Rollout
- Strengths
  - Toggle for bridge behavior; staged verification.
- Issues
  - No plan to monitor downstream provider error rates (HTTP 4xx/5xx) tied back to expr changes.
- Recommendations
  - Add counters for provider request failures keyed by provenance/endpoint; correlate with bridge-enabled flag and rule versions.

### 2.10 10 — Risks
- Strengths
  - Identifies query bloat, misconfig, provider drift.
- Issues
  - Security/privacy not addressed: logging full queries or parameter values may include sensitive terms (e.g., person names).
- Recommendations
  - Redact or hash query values in INFO logs; allow DEBUG-only full dumps in non‑prod.

### 2.11 11 — Acceptance Criteria
- Strengths
  - Practical and testable criteria.
- Issues
  - Doesn’t explicitly state “renderer must not emit provider‑named params” (to close the mapping-responsibility gap).
- Recommendations
  - Add explicit criterion: “Renderer emits std_keys only; provider naming and transforms are compiler responsibilities.”

### 2.12 99 — Appendices
- Strengths
  - Handy SQL and sample exprs.
- Issues
  - SQL templates assume provenance IDs; ensure seeds query IDs by code to avoid environment mismatch.
- Recommendations
  - Prefer subqueries to resolve `provenance_id` by code in templates (already shown in verification queries—extend to inserts).


## 3) Must‑Fix vs Nice‑to‑Improve

Must‑Fix (before implementation):
- MF‑1: Resolve renderer vs compiler responsibility for std_key → provider mapping. Pick compiler as single source of naming; refactor renderer to emit std_keys only.
- MF‑2: Define and document the exact execution order: placeholders → fn_code → expand params (std_keys) → mapping → transform_code → final values.
- MF‑3: Define merge policy for duplicate std_keys (SINGLE vs MULTI) and how MULTI propagates to provider params (repeat vs join).
- MF‑4: Clarify NOT/OR rule selection and parentheses policy to match provider semantics; provide examples and tests.
- MF‑5: Add security guidance for logs (redact/hash queries in INFO).

Nice‑to‑Improve:
- NI‑1: Introduce a small DSL or helper for composing complex `filter` values (Crossref) beyond simple string templates.
- NI‑2: Provide built‑in date format transforms (RFC3339_DATE/DATETIME) and number formatting transforms; document their usage per provider.
- NI‑3: Golden tests with frozen snapshots; perf microbench for large expressions.
- NI‑4: Metric naming and cardinality guidelines; dashboards for rule misses and mapping gaps.
- NI‑5: Endpoint‑aware date field names for EPMC documented with examples.


## 4) Suggested Refinements (aligned with compiler‑bridge)

Refinement R‑1: Single Naming Stage (Compiler)
Refactor `DefaultExprRenderer` so it never consults `apiParameterMap`. It should return:
```
RenderOutcome {
  String aggregatedQuery;         // from fragments
  Map<String,String> stdKeyParams; // std_key -> value (post fn_code/template)
  List<Issue> warnings;
  RenderTrace trace;
}
```
Then in `DefaultExprCompiler`, perform:
1) Bridge `query` via std_key mapping,
2) Map all std_keys to provider names,
3) Apply `transform_code` per mapped value,
4) Validate `maxQueryLength`,
5) Return final provider‑named params.

Refinement R‑2: Merge Policy
Introduce per‑std_key metadata (SINGLE/MULTI) in the field dictionary or rule hints. For MULTI:
- Option A (repeat): carry Map<String,List<String>> to the HTTP layer and encode repeated params.
- Option B (join): apply a transform to join values into one string (e.g., `filter=a,b,c`). Make join strategy a transform code to keep generality.

Refinement R‑3: Ordering Contract
Document the invariant:
```
placeholders (from atom)
  -> (fn_code applied; placeholders may be mutated or derived)
  -> expand templates to value (std_key stage)
  -> map std_key to provider name
  -> transform_code(value)
  -> final params
```
This ensures functions don’t see provider names, and transforms see the final textual value per provider semantics.

Refinement R‑4: Negation Semantics
For providers lacking true NOT support, provide negation-aware templates that de‑scope the query (e.g., add a negative field qualifier). Document “unsupported” policy (emit warning + skip) to avoid silently changing meaning.

Refinement R‑5: Observability Guardrails
INFO logs: use hashes (`sha256`) or last‑8 chars for queries; full dumps only at DEBUG and never in prod by default. Add counters for each warning/error code.

Refinement R‑6: Seed Robustness
Resolve provenance_id via subqueries by code in inserts; avoid hard‑coded IDs. Use consistent `effective_from` values and comment blocks to explain rationale.


## 5) Alternative Patterns Considered

- “Renderer‑only” mapping (status quo): simpler but hardwires std_key→provider coupling into rendering and makes transforms harder to sequence reliably. Rejected.
- “Request Model Builder” stage after compiler: a separate component that takes std_key params + aggregated query and emits provider‑named params. This is effectively what the compiler‑bridge does; keeping it inside `DefaultExprCompiler` is fine for now and simpler to integrate.


## 6) Conclusion

The compiler‑bridge design is viable and an improvement over the current state. Address the must‑fix items—especially the renderer/ compiler responsibility split, execution order, and merge policy—before implementation. With those resolved and with the proposed refinements, the plan should deliver a robust, extensible, and fully registry‑driven cross‑provider query construction pipeline.

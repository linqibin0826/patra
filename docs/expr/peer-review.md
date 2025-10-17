# Expression Compiler–Bridge Design — Peer Review

Date: 2025-10-15
Reviewer: Independent Architecture Review (Papertrace Registry Service)

## Executive Summary

The compiler–bridge approach appears architecturally sound and aligns with hexagonal/DDD boundaries: the renderer emits provider‑agnostic std_keys and boolean fragments, while the compiler performs a single provider‑naming stage and applies param‑level transforms before producing final request parameters (see 02-architecture.md §2.2–2.4; 03-compiler-bridge-internals.md §3.2.1 Execution Order Contract). The docs cover OR/NOT semantics, merge policy (SINGLE/MULTI), transform/function execution, observability guardrails, migration, and a golden test harness.

Overall clarity is good, but several small yet important inconsistencies and edge‑case policies should be tightened before implementation. In particular: unify metric names across documents, make merge tie‑breakers fully deterministic, specify a STRICT mode for NOT when unsupported, and gate MULTI=repeat semantics until adapter serialization is documented. With these adjustments, the design is ready to build.

Verdict: ⚠️ Needs adjustments before implementation (small doc fixes and guardrails; see Recommendations).

## Strengths

- Clear renderer ↔ compiler boundary: renderer outputs std_keys only; compiler does mapping and transform_code (02-architecture.md §2.2, §2.5; 03-compiler-bridge-internals.md §3.2, §3.3).
- Formalized execution order for deterministic behavior: placeholders → fn_code (rule scope) → expand templates to std_keys → std_key→provider mapping → transform_code (value scope) → final params (01-overview.md; 03-compiler-bridge-internals.md §3.2.1 Execution Order Contract).
- Merge policy documented: SINGLE last‑write‑wins by priority; MULTI collect+join or repeat (02-architecture.md §2.5; 03-compiler-bridge-internals.md §3.8; 11-acceptance-criteria.md).
- Boolean logic fidelity: explicit OR/NOT/parentheses rules and negation‑aware selection (02-architecture.md §2.7; 04/05/06-provider docs examples).
- Observability guardrails: INFO redaction/hashing of queries, bounded metric labels, and explicit warnings/errors (02-architecture.md §2.6; 03-compiler-bridge-internals.md §3.4).
- Migration/rollout discipline and provider onboarding checklist (07-migration-plan.md; 09-rollout.md; 12-provider-checklist.md).
- Golden test harness design with fixtures and CI triggers (12-golden-test-harness.md).

## Issues / Concerns

Each item is labeled Blocker / Warning / Suggestion.

1) Metric name inconsistencies (Warning)
- 02-architecture.md suggests `expr.render.rule_hits`, `expr.render.rule_miss`, `expr.param.map_hit`, `expr.param.map_miss`. 08-testing.md lists `expr.render.rules.miss`, `expr.param.map.miss`, `expr.transform.applied` (note pluralization and dot/underscore differences). This will fracture dashboards and alerts if not unified (02-architecture.md §2.6; 08-testing.md §8.5 Observability Assertions).

2) Merge tie‑breaker determinism (Warning)
- SINGLE policy states “priority wins; if equal, stable order by field/op.” The exact tie‑breaker is not fully specified (e.g., rule_id asc?). Make it explicit to guarantee reproducibility across environments (03-compiler-bridge-internals.md §3.8).

3) NOT unsupported policy semantics (Warning)
- 02-architecture.md allows “emit warning and skip” when a provider lacks true NOT. Skipping can silently weaken filters (e.g., A AND NOT(B) → A), especially when nested under OR. Recommend a STRICT mode (configurable) that fails compilation or requires an explicit provider‑specific negation template (02-architecture.md §2.7; 06-provider-crossref.md examples).

4) MULTI=repeat path is under‑specified (Warning)
- Docs prefer join transforms for MULTI but mention a “repeat” strategy later. Repeated param serialization belongs to adapters; until those docs exist, mandate join or disable repeat via feature flag to avoid transport ambiguity (02-architecture.md §2.5; 03-compiler-bridge-internals.md §3.8).

5) Param‑count guardrails are advisory only (Suggestion)
- 03 recommends W‑PARAM‑COUNT‑LIMIT/E‑PARAM‑COUNT‑LIMIT. Define default thresholds and env keys in one place, and add acceptance criteria around them (03-compiler-bridge-internals.md §3.8; 11-acceptance-criteria.md).

6) Function/transform not found handling (Suggestion)
- Behavior is “warn and proceed” (W‑FN‑OR‑TRANSFORM‑NOTFOUND). Consider STRICT mode to fail compilation when a configured code is missing, to avoid partially compiled semantics (03-compiler-bridge-internals.md §3.4).

7) Query‑length guardrail policy (Suggestion)
- `E-QUERY-LEN-MAX` fails fast (no truncation). Optionally allow a soft cap mode that drops low‑priority fragments (WITH EXPLICIT WARNING) to salvage requests for providers with tight limits (03-compiler-bridge-internals.md §3.4; 08-testing.md performance target).

8) Escaping/quoting responsibility (Suggestion)
- Renderer “handles quoting/escaping” for fragments, but consider adding explicit rules for double‑escaping risk with nested templates or provider‑specific syntaxes (03-compiler-bridge-internals.md notes on quoting; 02-architecture.md renderer responsibilities).

- Renderer/Compiler Boundary — Largely justified: invariants consistently restated across 01/02/03/04/05/06. Minor risk remains if code paths consult param maps in the renderer; ensure tests protect this invariant.
- Observability & Security — Mostly justified but metric name inconsistencies remain. Recommend “Pass after fix” once names are unified and label cardinality guidance is finalized.
- Migration Safety — Reasonable: clean DB assumption, effective_from guidance, rollback notes (07-migration-plan.md). Add notes for non‑clean DBs (new seed versions only) — already implied.
- Golden Tests — Harness design is good; ensure fixtures cover OR/NOT depth, MULTI join, date transforms, and error/warning codes (12-golden-test-harness.md).
- Performance & Limits — Guardrails present: maxQueryLength, perf target <50ms typical (03 internals; 08-testing.md). Consider adding param‑count thresholds to acceptance criteria.

Conclusion on “Go”: With the small adjustments in this review, “Go” becomes justified.

## Recommendations

Design & Contracts
- Unify metric names and examples across all docs. Adopt a canonical set and stick to underscores (or dots) consistently; document labels and cardinality budget (02-architecture.md §2.6; 08-testing.md §8.5).
- Specify deterministic tie‑breaker: e.g., `order by rule_priority desc, field_key asc, op_code asc, rule_id asc` for SINGLE collisions (03-compiler-bridge-internals.md §3.8).
- Introduce `expr.strict=true|false` toggle:
  - If true: NOT unsupported → compilation error; missing transform/function → error.
  - If false: current warning behaviors apply (02-architecture.md §2.7; 03-compiler-bridge-internals.md §3.4).
- Gate MULTI=repeat behind a feature flag until adapter serialization is formally documented; prefer join transforms by default (03-compiler-bridge-internals.md §3.8).

Testing & Golden Harness
- Add golden cases for: deep OR/NOT nesting, MULTI join with different delimiters, fn_code+transform_code composition, query length boundary, param‑count threshold crossing (12-golden-test-harness.md).
- Include a coverage report per provider: exercised std_keys and rules; fail PR if coverage drops below an agreed threshold (12-golden-test-harness.md).

Migration & Rollout
- State explicit policy for non‑clean databases: never edit applied seeds; always add new versions; include sample Flyway versioning table (07-migration-plan.md; 09-rollout.md).
- Mandate a fixed `effective_from` in the seeds for ordering determinism; include the exact timestamp used in examples (07-migration-plan.md).

Observability & Security
- Publish a minimal metric set with stable names: `expr.render.rule_hit`, `expr.render.rule_miss`, `expr.param.map_hit`, `expr.param.map_miss`, `expr.transform.applied`, `expr.compile.error{code}`, `expr.compile.duration_ms` (histogram). Define label keys and bounds.
- Keep INFO redaction guidance and add a one‑line “no PII” reminder; include sample sanitized log lines (02-architecture.md §2.6; 09-rollout.md).

Documentation Clarity
- Add a compact “value lifecycle” diagram showing data shapes at each step of the execution order (03-compiler-bridge-internals.md §3.2.1).
- Provide a single “Error/Warning Codes → Operator Action” table (codes, severity, message, typical fixes) and reference it from adapter runbooks (03-compiler-bridge-internals.md §3.4).

## Verdict

⚠️ Needs adjustments before implementation

The design is close to ready. Resolve the metric naming inconsistencies, harden tie‑breakers, define STRICT mode for NOT/transform/function failures, and gate MULTI repeat behavior. These are small, documentation‑level changes that will significantly reduce integration risk and improve operability. Once addressed, proceed to implementation with the proposed golden tests and observability baselines.

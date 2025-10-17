Replaced by: ../07-migration-rollout.md


# 09 — Rollout & Operations

Status: Documentation only (pre‑implementation)
Date: 2025-10-15


## 9.1 Rollout Steps (Doc-Only, No Code Yet)

1) Registry
   - Apply updated PubMed seed and add EPMC/Crossref seeds on empty dev DB.
   - Smoke test registry endpoints (list provenance, get snapshot).

2) Starter config (planned)
   - Keep `patra.expr.compiler.query-param-bridge.enabled=true`.
   - Optional: set `patra.expr.compiler.max-query-length` as guardrail.

3) Ingest flow (planned)
   - Compile sample expressions for each provider.
   - Inspect compiled params map to ensure correct provider parameter names and values.

4) Adapter smoke (planned)
   - Build provider requests from compiled params only (no manual term/query binding).
   - Use stubbed provider endpoints to validate query strings and parameters.


## 9.2 Observability During Rollout

- Enable DEBUG temporarily for `com.patra.starter.expr.compiler` in non‑prod to confirm bridges and transforms.
- INFO logs must redact/hash queries (e.g., `queryHash`); do not log full queries at INFO in prod.
- Capture counts of rule/param map misses; they should be zero for the seeded providers.
- Correlate provider HTTP error rates (4xx/5xx) with bridge enabled flag and rule versions during smoke tests.


## 9.3 Fallback

- If a provider misbehaves with boolean queries, temporarily disable the bridge via configuration and revert to adapter-side binding for diagnosis. The seeds remain valid and can be adjusted without code changes.


## 9.4 Documentation & Training

- Update service-level READMEs to reference the std_key approach and the provider pages in this docs set.
- Provide a short “How to add a new provider” recipe:
  1) Add fields and capabilities,
  2) Add render rules,
  3) Add param maps (including `query`),
  4) Smoke test via snapshot + compile + adapter assembly.
  5) Observe logs/metrics; ensure no rule/param map misses and provider error rates are normal.

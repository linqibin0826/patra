# Migration and Rollout Guide

Status: consolidated from 07-migration-plan.md and 09-rollout.md (2025-10-17)

Objectives
- Migrate existing adapters to compiler-bridge without breaking behavior.
- Roll out safely with observability and kill switches.

Plan (high level)
1) Prepare seeds in registry: field dict, capabilities, render rules, param map (per provider/endpoint).
2) Enable compiler-bridge in starter with safe defaults: `expr.strict=false` in dev/staging, `expr.multi.repeat.enabled=false`.
3) Refactor adapters to bind provider-named params only; remove hardcoded query binding.
4) Run golden tests and smoke tests to verify query and params assembly.
5) Validate metrics/logs in staging; monitor `expr.compile.errors{code}` and `expr.param.map_miss`.
6) Roll out gradually with feature flag; fallback path: disable bridge and revert to prior binding if required.

Checklists
- Registry
  - std_key mappings present for `query`, date range, pagination, filters.
  - Provider transforms registered where needed (e.g., `TO_EXCLUSIVE_MINUS_1D`, `FILTER_JOIN`).
- Application/Adapters
  - Renderer emits std_keys only; no provider naming logic.
  - Compiler bridging enabled; query length budget set if applicable.
  - Request builders accept provider-named params map.
- Observability
  - DEBUG logs enabled for compiler in dev/staging.
  - Metrics: `expr.param.map_hit`, `expr.param.map_miss`, `expr.transform.applied`, `expr.compile.errors`.

Rollout Steps
1) Dev Verification: run 08-testing-and-smoke.md end-to-end, confirm logs/metrics.
2) Staging: canary 5–10% traffic; compare encoded request-lines vs baseline.
3) Production: ramp to 100% with dashboards for error rates and param map misses.
4) Post-rollout: lock STRICT mode to `true` and remove legacy adapter bindings.

Risk Mitigations
- NOT/OR mismatches: covered by STRICT mode and golden cases.
- Date boundary off-by-one: `TO_EXCLUSIVE_MINUS_1D` and tests.
- Missing param mapping: `param_map_miss` metric + fallback flag.

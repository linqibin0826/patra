# Compiler–Bridge Docs Fix PR Checklist (Documentation‑Only)

Owner: Registry Docs Team
Scope: Documentation updates only (no code changes)

## Changes to Apply

- [ ] Unify metric names and label conventions across `docs/expr/02-architecture.md`, `docs/expr/08-testing.md`, and `docs/expr/09-rollout.md`.
  - Canonical metrics (align with code):
    - `expr.render.rule_hits`, `expr.render.rule_miss`
    - `expr.param.map_hit`, `expr.param.map_miss`
    - `expr.transform.applied`
    - `expr.compile.errors{code}`
    - `expr.compile.duration_ms` (histogram)
  - Labels (bounded): `{provenance, endpoint}` (except `expr.compile.errors{code}`).

- [ ] Add deterministic merge tie‑breaker for SINGLE collisions (documented in one place and referenced elsewhere):
  - Order: `rule_priority DESC, field_key ASC, op_code ASC, rule_id ASC`.
  - Files: update `docs/expr/03-compiler-bridge-internals.md` §3.8 and reference from `docs/expr/02-architecture.md` §2.5.

- [ ] Introduce and document `expr.strict` mode (true|false):
  - If `true`: unsupported NOT, missing `transform_code`/`fn_code` ⇒ compilation error; if `false`: current warning behavior.
  - Files: `docs/expr/02-architecture.md` (§2.7 Boolean Semantics, §2.6 Observability notes), `docs/expr/03-compiler-bridge-internals.md` (§3.4 Errors/Warnings), `docs/expr/11-acceptance-criteria.md`.

- [ ] Gate MULTI=repeat behavior behind config:
  - `expr.multi.repeat.enabled=false` by default; prefer join transforms.
  - Files: `docs/expr/02-architecture.md` (§2.5 Merge Policy), `docs/expr/03-compiler-bridge-internals.md` (§3.8), `docs/expr/11-acceptance-criteria.md`.

- [ ] Update provider onboarding and testing docs for new checks:
  - Add STRICT and MULTI gating items to `docs/expr/12-provider-checklist.md`.
  - Expand tests in `docs/expr/08-testing.md` and fixtures guidance in `docs/expr/12-golden-test-harness.md` to cover STRICT failures and MULTI join.



## Definition of Done

- [ ] All modified docs build without broken anchors and reference each other consistently.
- [ ] Metric names and label sets are identical across all documents.
- [ ] SINGLE tie‑breaker order is explicitly documented and referenced.
- [ ] STRICT mode and MULTI repeat gating are documented and reflected in checklist and tests.

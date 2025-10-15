# 11 — Acceptance Criteria & Checklists

Status: Documentation only (pre‑implementation)
Date: 2025-10-15


## 11.1 Acceptance Criteria

- Compiler bridges aggregated boolean query via std_key `query` to provider param per mapping.
- Renderer supports OR and NOT; parentheses and negation are correct by rule selection.
- `fn_code` executed for PARAMS rules; `transform_code` executed after mapping.
- Renderer emits std_keys only; provider naming and transforms occur in the compiler (single naming stage).
- Merge policy implemented: SINGLE last‑write‑wins by priority; MULTI supports join (transform) or repeat (deferred).
- Seeds for PubMed/EPMC/Crossref enable end‑to‑end compile → adapter request without adapter‑side query construction.
- Tests pass (unit and integration) and logs/metrics show no rule/param map misses for seeded providers.
- Logging follows redaction rules: full queries not printed at INFO in prod; hashes or truncated IDs used instead.


## 11.2 Dev Checklist

- [ ] Update PubMed seed: PARAMS placeholders → `{{...}}`, add mapping `query→term`.
- [ ] Add EPMC and Crossref seeds following provider docs here.
- [ ] Implement function/transform registries and wire into renderer/compiler.
- [ ] Implement OR/NOT rendering and negation rule selection.
- [ ] Implement compiler bridge and transforms pass.
- [ ] Enforce renderer emits std_keys only; mapping/transforms in compiler.
- [ ] Implement merge policy for SINGLE vs MULTI std_keys.
- [ ] Update provider assemblers to rely solely on compiled params.
- [ ] Add tests per matrix; validate compiled outputs.


## 11.3 Ops Checklist

- [ ] Apply seeds in dev; verify snapshot content.
- [ ] Compile sample expressions; validate params.
- [ ] Smoke requests to stubbed providers; confirm accepted query/params.
- [ ] Monitor logs for warnings; drive seed fixes if necessary.
- [ ] Verify INFO‑level redaction of queries in prod config; DEBUG permitted only in non‑prod.

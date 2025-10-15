# 11 — Acceptance Criteria & Checklists

Status: Documentation only (pre‑implementation)
Date: 2025-10-15


## 11.1 Acceptance Criteria

- Compiler bridges aggregated boolean query via std_key `query` to provider param per mapping.
- Renderer supports OR and NOT; parentheses and negation are correct by rule selection.
- `fn_code` executed for PARAMS rules; `transform_code` executed after mapping.
- Seeds for PubMed/EPMC/Crossref enable end‑to‑end compile → adapter request without adapter‑side query construction.
- Tests pass (unit and integration) and logs/metrics show no rule/param map misses for seeded providers.


## 11.2 Dev Checklist

- [ ] Update PubMed seed: PARAMS placeholders → `{{...}}`, add mapping `query→term`.
- [ ] Add EPMC and Crossref seeds following provider docs here.
- [ ] Implement function/transform registries and wire into renderer/compiler.
- [ ] Implement OR/NOT rendering and negation rule selection.
- [ ] Implement compiler bridge and transforms pass.
- [ ] Update provider assemblers to rely solely on compiled params.
- [ ] Add tests per matrix; validate compiled outputs.


## 11.3 Ops Checklist

- [ ] Apply seeds in dev; verify snapshot content.
- [ ] Compile sample expressions; validate params.
- [ ] Smoke requests to stubbed providers; confirm accepted query/params.
- [ ] Monitor logs for warnings; drive seed fixes if necessary.

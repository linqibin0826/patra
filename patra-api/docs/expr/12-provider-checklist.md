# 12 — Provider Onboarding Checklist (One‑Pager)

Status: Documentation only (pre‑implementation)
Date: 2025-10-15


## Purpose

Step‑by‑step recipe to add or refine a provider’s expression support using the compiler‑bridge design (std_key=query, compiler‑only mapping).


## Checklist

1) Provenance & Endpoints
- [ ] Ensure `reg_provenance` has the provider with stable `code`.
- [ ] Decide target endpoints (e.g., SEARCH, DETAIL). Use `operation_type`/`endpoint_name` to scope rules/maps when necessary.

2) Field Dictionary (std semantics)
- [ ] Add unified fields in `reg_expr_field_dict` (e.g., `publication_date`, `tiab`, `text`, `journal`).
- [ ] Set `data_type_code` (DATE/DATETIME/TEXT/NUMBER) and `cardinality_code` (SINGLE/MULTI) as appropriate.

3) Capabilities
- [ ] For each field, declare allowed ops in `reg_prov_expr_capability` (TERM/IN/RANGE/EXISTS/TOKEN, match types, range kind).
- [ ] Boundaries (min/max), case sensitivity, and in‑set sizes are declared here.

4) Render Rules (emit=QUERY or emit=PARAMS)
- [ ] For text searches, define QUERY templates (e.g., `"{{v}}"[TIAB]`, `"{{v}}"`).
- [ ] For date ranges, choose QUERY (fielded range) or PARAMS (std_keys like `from/to/datetype`).
- [ ] Use `{{...}}` placeholders; avoid provider param names here.
- [ ] If needed, set `fn_code` to derive placeholders (e.g., `PUBMED_DATETYPE`).
- [ ] Add rules for OR/NOT sensitivity if provider needs special negation templates.

5) Param Map (std_key → provider parameter)
- [ ] Always add `std_key='query'` mapping to the provider's boolean query parameter (e.g., PubMed `term`, EPMC `query`, Crossref `query`).
- [ ] Map other std_keys: `from`, `to`, `datetype`, `limit`, `offset`, `filter`, etc.
- [ ] Add `transform_code` where necessary (e.g., `TO_EXCLUSIVE_MINUS_1D`, `RFC3339_DATE`).
- [ ] If a std_key is MULTI (e.g., `filter`), decide join vs repeat strategy. Use join transforms by default (repeat requires `expr.multi.repeat.enabled=true`).

6) Effective Times & Scoping
- [ ] Use consistent `effective_from` across inserts (fixed timestamp for deterministic ordering).
- [ ] Scope rules/maps by `operation_type` and `endpoint_name` only when they diverge by endpoint.

7) Verification (SQL)
- [ ] Query back fields, capabilities, rules, and param maps to confirm inserts and values.
- [ ] Ensure `std_key='query'` mapping exists.

8) Testing (Docs §08)
- Unit:
  - [ ] Renderer: text/date rules, OR/NOT behavior, fn_code execution, placeholders.
  - [ ] Compiler: query bridging, all std_key mappings, transform_code application, merge policy.
- Integration:
  - [ ] Snapshot round‑trip; compile → adapter bind; provider request contains expected params.
- Golden:
  - [ ] Freeze example snapshot + expressions + expected outputs and run golden checks.

9) Observability & Policy
- [ ] INFO logs redact/hash queries; DEBUG only in non‑prod.
- [ ] Metrics: rule hits/misses, mapping hits/misses, transform applied, compile errors.

10) STRICT Mode & MULTI Validation
- [ ] Test provider with STRICT mode enabled (`expr.strict=true`) to ensure all functions/transforms exist.
- [ ] Verify NOT behavior: if provider doesn't support NOT, ensure proper error in STRICT mode or warning in non-STRICT.
- [ ] For MULTI std_keys, verify join transforms work correctly with default config (`expr.multi.repeat.enabled=false`).
- [ ] Document any provider-specific STRICT mode considerations.

11) Rollout
- [ ] Toggle bridge (keep enabled by default).
- [ ] Test with both STRICT and non-STRICT modes in staging.
- [ ] Smoke test with real or stubbed endpoints.
- [ ] Monitor provider error rates; adjust seeds (no code changes) as needed.
- [ ] Verify metrics are reporting with correct names and labels.

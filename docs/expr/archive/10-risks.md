Note: This document was moved to docs/expr/archive on 2025-10-17 as part of consolidation.
For current guidance, start with docs/expr/START-HERE.md.

# 10 — Risks & Mitigations

Status: Documentation only (pre‑implementation)
Date: 2025-10-15


## 10.1 Risks

- Query bloat with OR/NOT producing very long strings.
- Misconfigured `fn_code` or `transform_code` leading to wrong values.
- Provider syntax drift (upstream changes) breaking templates.
- Ambiguous date semantics across providers.
- Param map gaps causing missing parameters at runtime.


## 10.2 Mitigations

- Enforce `maxQueryLength` and prefer PARAMS‑based filters when possible.
- Unit tests for each function/transform; warn on unknown codes.
- Keep templates in seeds; adjust in registry without code changes upon provider drift.
- Document date rules in provider docs; keep transforms configurable per provider.
- Add tests that enumerate all std_keys emitted for common expressions and ensure mappings exist.


## 10.3 Residual Risks

- Some providers may impose hidden server‑side limits (term length, query complexity). We’ll monitor errors and add transforms to down‑scope queries (e.g., chunking via IN rules) if needed.

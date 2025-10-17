# Provider Onboarding Guide

Status: consolidated from HOW-TO-ADD-PROVIDER.md and 12-provider-checklist.md (2025-10-17)

Steps
1) Define capabilities in registry for the provider/endpoint.
2) Add render rules (QUERY fragments, PARAMS std_keys) with `fn_code` where needed.
3) Configure std_key → provider param map with optional `transform_code`.
4) Implement adapter request binding using provider-named params only.
5) Add golden and smoke tests; run the full test suite.

Checklist (must pass)
- Strict mode coverage: errors raised for unsupported NOT when `expr.strict=true`.
- MULTI behavior: joined via transforms; `expr.multi.repeat.enabled=false` by default.
- Date semantics: exclusive `to` handled via transform where applicable.
- Pagination: correct mapping for limit/offset and provider-specific fields.
- Observability: metrics emitted; DEBUG logs confirm bridge and transforms.

Tips
- Keep renderer free of provider naming; let the compiler perform mapping.
- Prefer transform-based joining over repeated parameters unless explicitly supported.
- Add examples to docs/expr/smoke/samples/ for regression protection.

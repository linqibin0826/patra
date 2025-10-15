# 02 — Architecture

Status: Documentation only (pre‑implementation)
Date: 2025-10-15


## 2.1 Layering (Hexagonal/DDD)

- Domain (pure Java): expression VO and use cases live here; no framework dependencies.
- Application (orchestrators): coordinate compilation and execution; no business rules.
- Infrastructure (repositories, RPC): registry clients, persistence.
- Adapter (controllers/jobs): expose application use cases via HTTP/MQ/etc.

Expr engine integration resides in the starter (`patra-spring-boot-starter-expr`) and in registry (seeds), keeping Domain/App clean.


## 2.2 Components and Responsibilities

- Registry (SSOT):
  - `reg_expr_field_dict`: unified fields (std semantics).
  - `reg_prov_expr_capability`: field capabilities per provider.
  - `reg_prov_expr_render_rule`: atom render rules → QUERY fragment or PARAMS std_key/value.
  - `reg_prov_api_param_map`: std_key → provider parameter naming (+ optional `transform_code`).

- Expr Starter:
  - `RuleSnapshotLoader` → loads DTOs from registry clients into `ProvenanceSnapshot`.
  - `ExprNormalizer` → canonical whitespace/structure.
  - `CapabilityChecker` → static validation from capability matrix.
  - `ExprRenderer` → render to (query fragments + std_key params).
  - `ExprCompiler` → compile end‑to‑end; applies compiler‑bridge and transforms; returns final provider‑named params and aggregated query (for logging/validation).

- Provider Adapters:
  - Bind provider‑named params to request models; do not construct queries or filters.


## 2.3 Data Flow (Compiler‑Bridge)

1) App asks to compile expr for provider/endpoint.
2) Starter loads `ProvenanceSnapshot` (fields, capabilities, rules, param map).
3) Normalize and validate expr.
4) Renderer:
   - Applies relevant `emit=QUERY` and `emit=PARAMS` rules for each atom.
   - Executes `fn_code` for PARAMS rules.
   - Accumulates query fragments (with OR/NOT support) and std_key params (strings).
5) Compiler:
   - Aggregates fragments to boolean query string.
   - Bridges `std_key=query` via param map into provider param (e.g., `term`).
   - Applies `transform_code` to all mapped values.
6) Adapter builds the provider request using those provider‑named params only.


## 2.4 Key Interfaces (locations)

- Renderer interface: `patra-spring-boot-starter-expr/src/main/java/com/patra/starter/expr/compiler/render/ExprRenderer.java`
- Default renderer: `.../render/DefaultExprRenderer.java`
- Compiler interface and default: `.../compiler/ExprCompiler.java`, `.../compiler/DefaultExprCompiler.java`
- Snapshot model: `.../snapshot/ProvenanceSnapshot.java`
- Snapshot loader: `.../snapshot/RegistryRuleSnapshotLoader.java`


## 2.5 Rules and Mapping Semantics

- Render rule selection keys (normalized): `field_key`, `op_code`, `match_type_key`, `negated_key`, `value_type_key`, `emit_type_code`.
- `emit=QUERY`: produces a fragment string (may use `{{v}}`, `{{quoted}}`, `{{items}}`, etc.).
- `emit=PARAMS`: `params` JSON uses `{{...}}` placeholders; optional `fn_code` can return derived placeholder values (e.g., `{{datetype}}`).
- Param map ties std_keys to provider parameter names (optionally with `transform_code`).


## 2.6 Observability

- Renderer: record rule hits (field/op/prio), unmatched rule warnings, OR/NOT usage.
- Compiler: log when bridging `query` and when applying transforms; enforce `maxQueryLength` with errors.
- Metrics: counters for rule misses, param map misses, transform invocation counts.

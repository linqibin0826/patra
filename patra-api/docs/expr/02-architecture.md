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
  - `ExprRenderer` → render atoms into boolean query fragments and emit std_key/value pairs only (no provider naming inside renderer).
  - `ExprCompiler` → single source of truth for provider naming: aggregates query, bridges `std_key=query`, maps all std_keys via param map, applies `transform_code`, validates, and returns final provider‑named params plus aggregated query (for logging/validation).

- Provider Adapters:
  - Bind provider‑named params to request models; do not construct queries or filters.


## 2.3 Data Flow (Compiler‑Bridge)

1) App asks to compile expr for provider/endpoint.
2) Starter loads `ProvenanceSnapshot` (fields, capabilities, rules, param map).
3) Normalize and validate expr.
4) Renderer:
   - Applies relevant `emit=QUERY` and `emit=PARAMS` rules for each atom.
   - Executes `fn_code` for PARAMS rules to derive placeholder values.
   - Accumulates query fragments (with OR/NOT support) and emits std_key params (strings).
5) Compiler:
   - Aggregates fragments to a boolean query string (with parentheses rules, see §2.7).
   - Bridges `std_key=query` via param map into provider param (e.g., `term`).
   - Maps all std_keys to provider parameter names; applies `transform_code` to each mapped value.
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
- `emit=PARAMS`: `params` JSON uses `{{...}}` placeholders; `fn_code` may derive placeholder values (e.g., `{{datetype}}`).
- Param map ties std_keys to provider parameter names (optionally with `transform_code`). Mapping occurs only in the compiler.

### 2.5.1 std_key Cardinality & Merge Policy

- Cardinality:
  - `SINGLE` (default): std_key holds a single value; if multiple atoms emit the same std_key, the engine applies a deterministic last‑write‑wins policy by rule priority (higher priority wins; if equal, stable order by field/op).
  - `MULTI`: std_key may collect multiple values. Mapping to provider params proceeds by:
    - Repeat strategy: emit repeated provider parameters (requires HTTP layer support), or
    - Join strategy: apply a transform (e.g., `LIST_JOIN`, `FILTER_JOIN`) to produce a single provider value.
- Configuration:
  - Cardinality originates from field dictionary or rule hints (when absent, treat as `SINGLE`).
  - The chosen strategy (repeat vs join) is expressed via transforms on the std_key’s mapped parameter.

## 2.6 Observability

- Logging (with redaction):
  - INFO: redact or hash boolean query values (e.g., `queryHash=sha256:abcd1234`). Do not log full queries at INFO in prod.
  - DEBUG (non‑prod): may include full query and param previews.
  - Renderer: record rule hits (field/op/prio), unmatched rule warnings, OR/NOT usage.
  - Compiler: log when bridging `query` and when applying transforms; enforce `maxQueryLength` with errors.
- Metrics (bounded cardinality):
  - `expr.render.rule_hits{provenance,endpoint}`
  - `expr.render.rule_miss{provenance,endpoint}`
  - `expr.param.map_hit{provenance,endpoint}`
  - `expr.param.map_miss{provenance,endpoint}`
  - `expr.transform.applied{provenance,endpoint,code}`
  - `expr.compile.errors{code}` (e.g., E‑QUERY‑LEN‑MAX)

## 2.7 Boolean Semantics (OR / NOT / Parentheses)

- AND: join with ` AND `.
- OR: join child fragments with ` OR ` and wrap in parentheses when nested inside AND or NOT contexts to preserve precedence.
- NOT: pass `negated=true` into rule selection for the NOT subtree; use negation‑aware templates or rules. If a provider lacks NOT semantics, emit warning and skip (configurable) to avoid semantic corruption.

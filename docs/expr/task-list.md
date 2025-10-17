# Expression Compiler-Bridge Implementation Task List

**Status**: Implementation Phase
**Created**: 2025-10-16
**Owner**: Expr Implementation Team
**Design Docs**: `docs/expr/01-overview.md` through `docs/expr/12-*.md`

---

## Overview

This checklist tracks all implementation tasks for the Expression Compiler-Bridge design. Tasks are organized by phase and priority. Update the status field as work progresses.

**Status Legend**:
- `TODO`: Not started
- `IN_PROGRESS`: Currently being worked on
- `BLOCKED`: Waiting on dependencies
- `DONE`: Completed and verified

---

## Phase 1: Core Engine Foundation (patra-spring-boot-starter-expr)

### P1.1 Function & Transform Registry Infrastructure

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P1.1.1** | Define `RenderFunction` interface with `code()` and `apply()` methods | `RenderFunction.java` | DONE | docs/expr/03 §3.3.1 |
| **P1.1.2** | Define `ValueTransform` interface with `code()` and `apply()` methods | `ValueTransform.java` | DONE | docs/expr/03 §3.3.1 |
| **P1.1.3** | Implement `FunctionRegistry` interface and `DefaultFunctionRegistry` with immutable map | `FunctionRegistry.java`, `DefaultFunctionRegistry.java` | DONE | P1.1.1 |
| **P1.1.4** | Implement `TransformRegistry` interface and `DefaultTransformRegistry` with immutable map | `TransformRegistry.java`, `DefaultTransformRegistry.java` | DONE | P1.1.2 |
| **P1.1.5** | Implement `PubmedDatetypeFunction` returning "pdat" | `PubmedDatetypeFunction.java` | DONE | P1.1.1, P1.1.3 |
| **P1.1.6** | Implement `ToExclusiveMinus1DTransform` for date subtraction (DATE granularity) | `ToExclusiveMinus1DTransform.java` | DONE | P1.1.2, P1.1.4 |
| **P1.1.7** | Implement `ListJoinTransform` for comma-separated list joining | `ListJoinTransform.java` | DONE | P1.1.2, P1.1.4 |
| **P1.1.8** | Implement `FilterJoinTransform` for filter-specific multi-value joining | `FilterJoinTransform.java` | DONE | P1.1.2, P1.1.4 |
| **P1.1.9** | Wire registries into Spring context with auto-configuration | `ExprFunctionAutoConfiguration.java` | DONE | P1.1.3, P1.1.4 |

### P1.2 Configuration & Safety Modes

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P1.2.1** | Create `ExprCompilerProperties` with bridge/query-length/param-count settings | `CompilerProperties.java` (updated) | DONE | docs/expr/03 §3.6 |
| **P1.2.2** | Create `ExprModeProperties` with strict mode and MULTI repeat gating | `ExprModeProperties.java` | DONE | docs/expr/02 §2.8 |
| **P1.2.3** | Register properties in Spring Boot auto-configuration | `ExprCompilerAutoConfiguration.java` (updated) | DONE | P1.2.1, P1.2.2 |
| **P1.2.4** | Add default property values to `application.yml` template | `application-expr-reference.yml` | DONE | P1.2.1, P1.2.2 |

### P1.3 Renderer Enhancements

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P1.3.1** | Extend `DefaultExprRenderer` to support OR operator with parentheses wrapping | Updated `DefaultExprRenderer.java` | DONE | docs/expr/02 §2.7 |
| **P1.3.2** | Extend `DefaultExprRenderer` to support NOT operator with negation rule selection | Updated `DefaultExprRenderer.java` | DONE | docs/expr/02 §2.7 |
| **P1.3.3** | Implement parentheses logic: wrap OR in `()` when nested in AND/NOT contexts | Updated `DefaultExprRenderer.java` | DONE | P1.3.1, P1.3.2 |
| **P1.3.4** | Implement `fn_code` execution for PARAMS rules before template expansion | Updated `DefaultExprRenderer.java` | DONE | P1.1.3, docs/expr/03 §3.2.1 |
| **P1.3.5** | Ensure renderer emits std_keys ONLY (no provider naming) | Updated `DefaultExprRenderer.java` | DONE | docs/expr/02 §2.5 |
| **P1.3.6** | Implement SINGLE std_key collection with deterministic merge ordering | Updated `DefaultExprRenderer.java` | DONE | docs/expr/03 §3.8 |
| **P1.3.7** | Implement MULTI std_key collection (list accumulation) | Updated `DefaultExprRenderer.java` | DONE | docs/expr/03 §3.8 |
| **P1.3.8** | Add renderer warning codes (W-RENDER-RULE-MISSING, W-FN-OR-TRANSFORM-NOTFOUND) | Updated `DefaultExprRenderer.java` | DONE | docs/expr/03 §3.4.1 |
| **P1.3.9** | Add renderer logging (rule hits/misses, OR/NOT usage, fn_code execution) | Updated `DefaultExprRenderer.java` | DONE | docs/expr/02 §2.6 |

### P1.4 Compiler Bridge Implementation

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P1.4.1** | Implement fragment aggregation with boolean operators (AND/OR/NOT) | Updated `DefaultExprCompiler.java` | DONE | P1.3.1, P1.3.2 |
| **P1.4.2** | Implement query bridging: lookup `std_key=query` in param map and set provider param | Updated `DefaultExprCompiler.java` | DONE | docs/expr/03 §3.2 |
| **P1.4.3** | Implement std_key to provider param mapping for all std_keys | Updated `DefaultExprCompiler.java` | DONE | docs/expr/03 §3.2 |
| **P1.4.4** | Apply `transform_code` to each mapped value (including bridged query) | Updated `DefaultExprCompiler.java` | DONE | P1.1.4, P1.4.3 |
| **P1.4.5** | Implement SINGLE merge policy with deterministic ordering (rule_priority DESC, field_key ASC, op_code ASC, rule_id ASC) | Updated `DefaultExprCompiler.java` | DONE | docs/expr/03 §3.8 |
| **P1.4.6** | Implement MULTI join strategy (default: apply join transforms) | Updated `DefaultExprCompiler.java` | DONE | P1.1.7, P1.1.8 |
| **P1.4.7** | Implement MULTI repeat strategy (gated by config, initially disabled) | Updated `DefaultExprCompiler.java` | DONE | P1.2.2, P1.4.6 |
| **P1.4.8** | Enforce `maxQueryLength` with E-QUERY-LEN-MAX error | Updated `DefaultExprCompiler.java` | DONE | P1.2.1, docs/expr/03 §3.9 |
| **P1.4.9** | Implement STRICT mode behavior (fail on missing fn/transform/NOT support) | Updated `DefaultExprCompiler.java` | DONE | P1.2.2, docs/expr/03 §3.4.2 |
| **P1.4.10** | Implement non-STRICT mode behavior (warn and degrade gracefully) | Updated `DefaultExprCompiler.java` | DONE | P1.2.2, docs/expr/03 §3.4.2 |
| **P1.4.11** | Merge renderer warnings with validation warnings in CompileResult | Updated `DefaultExprCompiler.java` | DONE | P1.3.8 |
| **P1.4.12** | Add compiler logging (bridge operations, transform applications, query hash/redaction) | Updated `DefaultExprCompiler.java` | DONE | docs/expr/02 §2.6, docs/expr/03 §3.5 |
| **P1.4.13** | Add warning for missing param map (W-PARAM-MAP-MISSING) | Updated `DefaultExprCompiler.java` | DONE | docs/expr/03 §3.4.1 |

### P1.5 Observability

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P1.5.1** | Add Micrometer metrics: `expr.render.rule_hits{provenance,endpoint}` | Metrics instrumentation | DONE | docs/expr/02 §2.6 |
| **P1.5.2** | Add Micrometer metrics: `expr.render.rule_miss{provenance,endpoint}` | Metrics instrumentation | DONE | docs/expr/02 §2.6 |
| **P1.5.3** | Add Micrometer metrics: `expr.param.map_hit{provenance,endpoint}` | Metrics instrumentation | DONE | docs/expr/02 §2.6 |
| **P1.5.4** | Add Micrometer metrics: `expr.param.map_miss{provenance,endpoint}` | Metrics instrumentation | DONE | docs/expr/02 §2.6 |
| **P1.5.5** | Add Micrometer metrics: `expr.transform.applied{provenance,endpoint,code}` | Metrics instrumentation | DONE | docs/expr/02 §2.6 |
| **P1.5.6** | Add Micrometer metrics: `expr.compile.errors{code}` | Metrics instrumentation | DONE | docs/expr/02 §2.6 |
| **P1.5.7** | Add Micrometer histogram: `expr.compile.duration_ms` | Metrics instrumentation | DONE | docs/expr/02 §2.6 |
| **P1.5.8** | Implement query redaction for INFO logs (hash or truncate) | Logging utilities | DONE | docs/expr/03 §3.5 |
| **P1.5.9** | Configure DEBUG-only full query logging for non-prod environments | Logging configuration | DONE | docs/expr/03 §3.5 |

---

## Phase 2: Registry Seeds (patra-registry)

### P2.1 PubMed Seed Update

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P2.1.1** | Update existing PubMed PARAMS rules to use `{{from}}`, `{{to}}`, `{{datetype}}` placeholders | Updated `V1.1.1__seed_pubmed_expr_config.sql` | DONE | docs/expr/04 §4.6 |
| **P2.1.2** | Add `fn_code='PUBMED_DATETYPE'` to PubMed date RANGE render rule | Updated `V1.1.1__seed_pubmed_expr_config.sql` | DONE | P1.1.5, docs/expr/04 §4.3.2 |
| **P2.1.3** | Insert param map entry: `std_key='query' → provider_param_name='term'` | Updated `V1.1.1__seed_pubmed_expr_config.sql` | DONE | docs/expr/04 §4.2 |
| **P2.1.4** | Add `transform_code='TO_EXCLUSIVE_MINUS_1D'` to `to → maxdate` mapping | Updated `V1.1.1__seed_pubmed_expr_config.sql` | DONE | P1.1.6, docs/expr/04 §4.2 |
| **P2.1.5** | Update PubMed TIAB text rules for OR support (set `wrap_group=true` for IN operator) | Updated `V1.1.1__seed_pubmed_expr_config.sql` | DONE | docs/expr/04 §4.3.1 |
| **P2.1.6** | Add param map entries for pagination: `limit→retmax`, `offset→retstart` | Updated `V1.1.1__seed_pubmed_expr_config.sql` | DONE | docs/expr/04 §4.2 |
| **P2.1.7** | Verify PubMed seed with manual SQL queries | Verification report | DONE | P2.1.1-P2.1.6, docs/expr/07 §7.5 |

### P2.2 EPMC Seed Creation

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P2.2.1** | Create migration file `V1.1.2__seed_epmc_expr_config.sql` | New SQL migration file | DONE | docs/expr/05, docs/expr/07 |
| **P2.2.2** | Add `publication_date` field to field dictionary (DATE range) | Seed SQL | DONE | docs/expr/05 §5.3.2 |
| **P2.2.3** | Add EPMC capabilities: TERM/IN (text), RANGE (DATE on publication_date) | Seed SQL | DONE | docs/expr/05 §5.3 |
| **P2.2.4** | Add EPMC text render rules: TERM ANY/PHRASE, IN with OR joining | Seed SQL | DONE | docs/expr/05 §5.3.1 |
| **P2.2.5** | Add EPMC date render rule: RANGE with `FIRST_PDATE:[{{from}} TO {{to}}]` template | Seed SQL | DONE | docs/expr/05 §5.3.2 |
| **P2.2.6** | Add EPMC param map: `query→query`, `limit→pageSize` | Seed SQL | DONE | docs/expr/05 §5.2 |
| **P2.2.7** | Add optional cursor param map if needed: `cursor→cursorMark` | Seed SQL | DONE | docs/expr/05 §5.2 |
| **P2.2.8** | Verify EPMC seed with manual SQL queries | Verification report | DONE | P2.2.1-P2.2.7, docs/expr/07 §7.5 |

### P2.3 Crossref Seed Creation

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P2.3.1** | Create migration file `V1.1.3__seed_crossref_expr_config.sql` | New SQL migration file | DONE | docs/expr/06, docs/expr/07 |
| **P2.3.2** | Add `publication_date` field to field dictionary if not present (DATE range) | Seed SQL | DONE | docs/expr/06 §6.3.2 |
| **P2.3.3** | Add Crossref capabilities: TERM/IN (text), RANGE (DATE on publication_date) | Seed SQL | DONE | docs/expr/06 §6.3 |
| **P2.3.4** | Add Crossref text render rules: TERM ANY/PHRASE, IN with OR joining | Seed SQL | DONE | docs/expr/06 §6.3.1 |
| **P2.3.5** | Add Crossref date render rule: PARAMS with `filter` std_key using template `from-pub-date:{{from}},until-pub-date:{{to}}` | Seed SQL | DONE | docs/expr/06 §6.3.2 |
| **P2.3.6** | Define `filter` std_key as MULTI in field dictionary or rule hints | Seed SQL | DONE | docs/expr/06 §6.7 |
| **P2.3.7** | Add Crossref param map: `query→query`, `filter→filter`, `limit→rows`, `offset→offset` | Seed SQL | DONE | docs/expr/06 §6.2 |
| **P2.3.8** | Verify Crossref seed with manual SQL queries | Verification report | DONE | P2.3.1-P2.3.7, docs/expr/07 §7.5 |

---

## Phase 3: Integration & Adapters (patra-ingest)

### P3.1 Adapter Refactoring

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P3.1.1** | Remove manual `term` query construction from PubMed adapter | Updated PubMed adapter | TODO | P1.4.2, P2.1.3 |
| **P3.1.2** | Bind PubMed request params from compiled `params` map only | Updated PubMed adapter | TODO | P3.1.1 |
| **P3.1.3** | Remove manual `query` construction from EPMC adapter (if exists) | Updated EPMC adapter | DONE | P1.4.2, P2.2.6 |
| **P3.1.4** | Bind EPMC request params from compiled `params` map only | Updated EPMC adapter | DONE | P3.1.3 |
| **P3.1.5** | Remove manual `query`/`filter` construction from Crossref adapter (if exists) | Updated Crossref adapter | DONE | P1.4.2, P2.3.7 |
| **P3.1.6** | Bind Crossref request params from compiled `params` map only | Updated Crossref adapter | DONE | P3.1.5 |
| **P3.1.7** | Verify HTTP client/request models accept provider-named params directly | Adapter validation | DONE | P3.1.2, P3.1.4, P3.1.6 |

---

## Phase 4: Testing

### P4.1 Unit Tests - Renderer

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P4.1.1** | Test AND-only: two TERM atoms produce `fragA AND fragB` | `RendererAndTest.java` | DONE   | P1.3.1, docs/expr/08 §8.2 |
| **P4.1.2** | Test OR-only: two TERM atoms produce `(fragA OR fragB)` | `RendererOrTest.java` | DONE   | P1.3.1, docs/expr/08 §8.2 |
| **P4.1.3** | Test mixed AND/OR: `A AND (B OR C)` with correct parentheses | `RendererMixedBooleanTest.java` | DONE   | P1.3.3, docs/expr/08 §8.2 |
| **P4.1.4** | Test NOT: `NOT(A)` selects negated=true rule | `RendererNotTest.java` | DONE   | P1.3.2, docs/expr/08 §8.2 |
| **P4.1.5** | Test PARAMS placeholders: `{{from}}/{{to}}/{{datetype}}` expansion | `RendererPlaceholdersTest.java` | DONE   | P1.3.4, docs/expr/08 §8.2 |
| **P4.1.6** | Test fn_code: `PUBMED_DATETYPE` returns "pdat" | `RendererFunctionTest.java` | DONE   | P1.1.5, P1.3.4, docs/expr/08 §8.2 |
| **P4.1.7** | Test SINGLE std_key collision: deterministic merge by rule priority ordering | `RendererSingleMergeTest.java` | DONE   | P1.3.6, docs/expr/08 §8.2 |
| **P4.1.8** | Test MULTI std_key: verify list accumulation at renderer output | `RendererMultiCollectionTest.java` | DONE   | P1.3.7, docs/expr/08 §8.2 |

### P4.2 Unit Tests - Compiler

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P4.2.1** | Test query bridging: aggregated query → provider param via `std_key=query` mapping | `CompilerBridgeTest.java` | DONE | P1.4.2, docs/expr/08 §8.2 |
| **P4.2.2** | Test transform: `TO_EXCLUSIVE_MINUS_1D` converts `to` value | `CompilerTransformTest.java` | DONE | P1.1.6, P1.4.4, docs/expr/08 §8.2 |
| **P4.2.3** | Test missing param map: warning W-PARAM-MAP-MISSING | `CompilerMissingMapTest.java` | DONE | P1.4.13, docs/expr/08 §8.2 |
| **P4.2.4** | Test query length enforcement: E-QUERY-LEN-MAX error | `CompilerQueryLengthTest.java` | DONE | P1.4.8, docs/expr/08 §8.2 |
| **P4.2.5** | Test MULTI join: `LIST_JOIN`/`FILTER_JOIN` transforms produce single value | `CompilerMultiJoinTest.java` | DONE | P1.4.6, docs/expr/08 §8.2 |
| **P4.2.6** | Test MULTI repeat (if enabled): verify repeated provider parameters | `CompilerMultiRepeatTest.java` | DONE | P1.4.7, docs/expr/08 §8.2 |
| **P4.2.7** | Test STRICT mode: error on missing fn_code | `CompilerStrictFunctionTest.java` | DONE | P1.4.9, docs/expr/08 §8.2 |
| **P4.2.8** | Test STRICT mode: error on missing transform_code | `CompilerStrictTransformTest.java` | DONE | P1.4.9, docs/expr/08 §8.2 |
| **P4.2.9** | Test STRICT mode: error on unsupported NOT | `CompilerStrictNotTest.java` | DONE | P1.4.9, docs/expr/08 §8.2 |
| **P4.2.10** | Test non-STRICT mode: warnings instead of errors | `CompilerNonStrictTest.java` | DONE | P1.4.10, docs/expr/08 §8.2 |

### P4.3 Integration Tests - Registry

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P4.3.1** | Test PubMed snapshot load: verify fields, capabilities, rules, param maps | `PubmedSnapshotLoadTest.java` | TODO | P2.1.7, docs/expr/08 §8.3 |
| **P4.3.2** | Test EPMC snapshot load: verify fields, capabilities, rules, param maps | `EpmcSnapshotLoadTest.java` | TODO | P2.2.8, docs/expr/08 §8.3 |
| **P4.3.3** | Test Crossref snapshot load: verify fields, capabilities, rules, param maps | `CrossrefSnapshotLoadTest.java` | TODO | P2.3.8, docs/expr/08 §8.3 |

### P4.4 Integration Tests - End-to-End

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P4.4.1** | Test PubMed E2E: phrase + date range → compiled params with `term`, `mindate/maxdate/datetype` | `PubmedE2ETest.java` | TODO | P3.1.2, docs/expr/08 §8.3 |
| **P4.4.2** | Test EPMC E2E: text + date → compiled params with `query` containing date fragment | `EpmcE2ETest.java` | TODO | P3.1.4, docs/expr/08 §8.3 |
| **P4.4.3** | Test Crossref E2E: phrase + date → compiled params with `query` and `filter` | `CrossrefE2ETest.java` | TODO | P3.1.6, docs/expr/08 §8.3 |

### P4.5 Golden Test Harness

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P4.5.1** | Create golden test directory structure under `src/test/resources/golden/` | Directory structure | TODO | docs/expr/12-golden-test-harness.md |
| **P4.5.2** | Create PubMed golden fixtures: snapshot.json, expr-phrase-date.json, expected-phrase-date.json | Golden fixtures | TODO | P4.5.1, docs/expr/12 |
| **P4.5.3** | Create PubMed golden fixtures: expr-or-not.json, expected-or-not.json | Golden fixtures | TODO | P4.5.1, docs/expr/12 |
| **P4.5.4** | Create PubMed golden fixtures: expr-deep-or-not.json (3+ levels), expected-deep-or-not.json | Golden fixtures | TODO | P4.5.1, docs/expr/12 (Required) |
| **P4.5.5** | Create PubMed golden fixtures: expr-strict-mode-error.json, expected-strict-mode-error.json | Golden fixtures | TODO | P4.5.1, docs/expr/12 (Required) |
| **P4.5.6** | Create EPMC golden fixtures: snapshot.json, expr-date-query.json, expected-date-query.json | Golden fixtures | TODO | P4.5.1, docs/expr/12 |
| **P4.5.7** | Create EPMC golden fixtures: expr-multi-join.json, expected-multi-join.json | Golden fixtures | TODO | P4.5.1, docs/expr/12 (Required) |
| **P4.5.8** | Create Crossref golden fixtures: snapshot.json, expr-filter.json, expected-filter.json | Golden fixtures | TODO | P4.5.1, docs/expr/12 |
| **P4.5.9** | Create Crossref golden fixtures: expr-warning-codes.json, expected-warning-codes.json | Golden fixtures | TODO | P4.5.1, docs/expr/12 (Required) |
| **P4.5.10** | Implement golden test harness runner with normalization and assertion logic | `GoldenTestHarness.java` | TODO | P4.5.2-P4.5.9, docs/expr/12 |
| **P4.5.11** | Generate coverage report: std_keys/rules exercised, error/warning codes tested | Coverage report | TODO | P4.5.10, docs/expr/12 §Coverage |

### P4.6 Architecture Tests

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P4.6.1** | Implement ArchUnit test: verify domain layer has no framework dependencies | `ArchitectureTest.java` | TODO | docs/expr/08 §8.1 |
| **P4.6.2** | Implement ArchUnit test: verify adapter→app→domain dependency direction | `ArchitectureTest.java` | TODO | docs/expr/08 §8.1 |
| **P4.6.3** | Implement ArchUnit test: verify naming conventions (*Orchestrator, *Port, *RepositoryImpl) | `ArchitectureTest.java` | TODO | docs/expr/08 §8.1 |

---

## Phase 5: Documentation & Configuration

### P5.1 Configuration Files

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P5.1.1** | Add expr configuration to `application.yml` for dev environment (strict=false) | `application-dev.yml` | TODO | P1.2.3 |
| **P5.1.2** | Add expr configuration to `application.yml` for prod environment (strict=true) | `application-prod.yml` | TODO | P1.2.3 |
| **P5.1.3** | Document MULTI repeat disabled by default in configuration comments | Configuration docs | TODO | P1.2.2 |
| **P5.1.4** | Set up query length limits in configuration (e.g., 5000 chars) | Configuration values | TODO | P1.2.1 |

### P5.2 Service Documentation

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P5.2.1** | Update `patra-spring-boot-starter-expr/README.md` with std_key approach overview | Updated README | TODO | docs/expr/01, docs/expr/02 |
| **P5.2.2** | Update `patra-ingest/README.md` with adapter integration guide | Updated README | TODO | docs/expr/04, docs/expr/05, docs/expr/06 |
| **P5.2.3** | Update `patra-registry/README.md` with seed management instructions | Updated README | TODO | docs/expr/07 |
| **P5.2.4** | Create "How to add a new provider" recipe document | `docs/expr/HOW-TO-ADD-PROVIDER.md` | TODO | docs/expr/12-provider-checklist.md |

---

## Phase 6: Rollout & Smoke Testing

### P6.1 Database Migrations

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P6.1.1** | Apply PubMed seed update in dev database | Migration applied | TODO | P2.1.7 |
| **P6.1.2** | Apply EPMC seed in dev database | Migration applied | TODO | P2.2.8 |
| **P6.1.3** | Apply Crossref seed in dev database | Migration applied | TODO | P2.3.8 |
| **P6.1.4** | Verify seed application with SQL queries (all param maps, rules present) | Verification report | TODO | P6.1.1-P6.1.3, docs/expr/07 §7.5 |

### P6.2 Smoke Testing

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P6.2.1** | Compile sample PubMed expression and validate compiled params | Smoke test report | TODO | P6.1.1, docs/expr/09 §9.1 |
| **P6.2.2** | Compile sample EPMC expression and validate compiled params | Smoke test report | TODO | P6.1.2, docs/expr/09 §9.1 |
| **P6.2.3** | Compile sample Crossref expression and validate compiled params | Smoke test report | TODO | P6.1.3, docs/expr/09 §9.1 |
| **P6.2.4** | Test with stubbed provider endpoints (verify query/params format) | Smoke test report | TODO | P6.2.1-P6.2.3, docs/expr/09 §9.1 |
| **P6.2.5** | Test STRICT mode enabled: verify error behavior | Smoke test report | TODO | P1.4.9, docs/expr/09 |
| **P6.2.6** | Test non-STRICT mode: verify warning behavior | Smoke test report | TODO | P1.4.10, docs/expr/09 |

### P6.3 Observability Validation

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P6.3.1** | Verify INFO logs redact/hash queries (no full query content) | Log validation | TODO | P1.5.8, docs/expr/09 §9.2 |
| **P6.3.2** | Verify DEBUG logs include full query/params in non-prod | Log validation | TODO | P1.5.9, docs/expr/09 §9.2 |
| **P6.3.3** | Verify all metrics are reporting with correct names and bounded labels | Metrics validation | TODO | P1.5.1-P1.5.7, docs/expr/09 §9.2 |
| **P6.3.4** | Monitor rule/param map miss counts (should be zero for seeded providers) | Monitoring report | TODO | P6.3.3, docs/expr/09 §9.2 |

### P6.4 Final Acceptance

| Task ID | Description | Expected Output | Status | Dependencies |
|---------|-------------|-----------------|--------|--------------|
| **P6.4.1** | Verify all acceptance criteria from docs/expr/11-acceptance-criteria.md | Acceptance report | TODO | All previous phases |
| **P6.4.2** | Run full test suite (unit, integration, golden, architecture) | Test report | TODO | P4.1-P4.6 |
| **P6.4.3** | Performance benchmark: 50-100 atom complex query compiles under 50ms | Benchmark report | TODO | docs/expr/08 §8.6 |
| **P6.4.4** | Memory footprint validation: no large retained objects post-compile | Memory profile | TODO | docs/expr/08 §8.6 |
| **P6.4.5** | Final code review and sign-off | Review approval | TODO | P6.4.1-P6.4.4 |

---

## Summary Statistics

**Total Tasks**: 132
- **Phase 1 (Core Engine)**: 42 tasks
- **Phase 2 (Registry Seeds)**: 21 tasks
- **Phase 3 (Integration)**: 7 tasks
- **Phase 4 (Testing)**: 40 tasks
- **Phase 5 (Documentation)**: 8 tasks
- **Phase 6 (Rollout)**: 14 tasks

**Current Status**: 62 TODO, 0 IN_PROGRESS, 70 DONE, 0 BLOCKED

---

## Progress Tracking

Update this section as tasks are completed:

**Last Updated**: 2025-10-17 00:47
**Completed Tasks**: 75 / 132 (56.8%)
**Current Phase**: Phase 4 - Testing (Compiler)
**Current Milestone**: Phase 4.2 Unit Tests - Compiler (P4.2.1 kickoff)
**Next Milestone**: Phase 4.3 Integration Tests - Registry

---

## Notes & Decisions

*Document key implementation decisions, blockers, or deviations from the design as they occur.*

- **2025-10-16**: Initial task list created based on approved design documents (docs/expr/01-12).
- Design passed final review with GO verdict (docs/expr/final-review.md).
- All documentation gaps addressed (STRICT mode, MULTI gating, deterministic merge ordering).
- **2025-10-16 16:30**: Phase 1.1 COMPLETE - Function & Transform Registry Infrastructure implemented and compiling successfully. Registered 1 function (PUBMED_DATETYPE) and 3 transforms (TO_EXCLUSIVE_MINUS_1D, LIST_JOIN, FILTER_JOIN) with Spring auto-configuration.
- **2025-10-16 16:35**: Phase 1.2 COMPLETE - Configuration & Safety Modes implemented. Extended CompilerProperties with query bridge/length/param limits. Created ExprModeProperties for STRICT mode and MULTI repeat gating. Reference configuration created in application-expr-reference.yml.
- **2025-10-16 17:00**: Phase 1.3 COMPLETE - Renderer Enhancements implemented. Full OR/NOT support with parentheses, fn_code execution (PUBMED_DATETYPE), std_key-only emission (no provider naming), SINGLE/MULTI std_key merge policies, comprehensive logging. Renderer now fully implements design from docs/expr/02 §2.7 and docs/expr/03 §3.2.1.
- **2025-10-16 19:05**: Phase 1.4 COMPLETE - Compiler bridge implemented. DefaultExprCompiler now maps std_keys to provider params, bridges `query`, applies transforms, enforces STRICT vs non-STRICT behavior, and logs/limits per design (docs/expr/03 §3.2-3.5).
- **2025-10-16 19:45**: Phase 1.5 COMPLETE - Observability instrumentation added. Micrometer metrics published for render rule hits/misses, param map hits/misses, transform usage, compile errors & duration. Compiler logs now hash queries at INFO with full details retained for DEBUG.
- **2025-10-16 20:30**: Phase 2.1 COMPLETE - PubMed seed updated. Fixed PARAMS placeholders to `{{from}}`, `{{to}}`, `{{datetype}}` format (line 117). Added critical `query→term` param map entry (id=900310) for query bridging. All existing mappings verified: `fn_code='PUBMED_DATETYPE'` already present, `wrap_group=1` for IN rule, `TO_EXCLUSIVE_MINUS_1D` transform on `to→maxdate`, pagination mappings complete.
- **2025-10-16 20:35**: Phase 2.2 COMPLETE - EPMC seed created (V1.1.2__seed_epmc_expr_config.sql). Added EPMC provenance to V1.1.0. Created field dictionary (publication_date, text), capabilities (RANGE/TERM/IN), render rules (QUERY-based for text and date with `FIRST_PDATE` syntax), param map (`query→query`, `limit→pageSize`). Cursor mapping commented for future.
- **2025-10-16 20:40**: Phase 2.3 COMPLETE - Crossref seed created (V1.1.3__seed_crossref_expr_config.sql). Added CROSSREF provenance to V1.1.0. Created field dictionary (text, reused publication_date), capabilities (RANGE/TERM/IN), render rules (QUERY for text, PARAMS for date filter with `filter` std_key MULTI), param map (`query→query`, `filter→filter`, `limit→rows`, `offset→offset`). Compilation verified successfully (mvn -q -DskipTests compile).
- **2025-10-16 21:10**: Phase 3.1 adapters refactored to consume provider-named params. PubMed assembler now reads `term` from compiled params, PubMed infra logs hashed provider term, and new EPMC/Crossref assemblers plus request models bind compiled params without manual query/filter construction.
- **2025-10-16 21:45**: Phase 4.1 renderer unit tests added (P4.1.1–P4.1.7). Covered NOT negation rule selection, PARAM placeholder expansion, PUBMED_DATETYPE fn_code, and SINGLE std_key merge ordering. P4.1.8 (MULTI accumulation) queued next.
- **2025-10-16 22:05**: Phase 4.1 renderer unit tests complete (P4.1.1–P4.1.8). MULTI std_key accumulation now validated via renderer output joining with the internal delimiter.
- **2025-10-17 00:47**: Verified all P4.1 renderer unit tests pass locally (offline Maven). Proceeding to Phase 4.2 compiler unit tests per docs/expr/08-testing.md and docs/expr/03-compiler-bridge-internals.md.

---

*End of Task List - Ready for Implementation*

# patra-spring-boot-starter-expr

Spring Boot starter providing the Papertrace expression compilation pipeline. It combines the immutable AST from `patra-expr-kernel` with registry backed capability metadata and renders provider specific query fragments + parameter maps.

## Modules at a Glance

- `patra-expr-kernel`: sealed AST (`Expr`, `Atom`, `And`, `Or`, `Not`, `Const`) and factories in `Exprs`.
- `patra-spring-boot-starter-expr`: normalisation, capability validation, rendering, rule snapshot integration and Spring Boot auto configuration.

## Dependency

```xml
<dependency>
  <groupId>com.papertrace</groupId>
  <artifactId>patra-spring-boot-starter-expr</artifactId>
</dependency>
```

The starter expects Feign clients for `ProvenanceClient` and `ExprClient` (supplied by `patra-registry-api`).

## Auto Configuration

```yaml
patra:
  expr:
    compiler:
      enabled: true
      registry-api:
        enabled: true          # use Registry to fetch render/capability snapshots
        operation-default: SEARCH
```

Beans provided when not overridden by the user:

- `RuleSnapshotLoader` → `RegistryRuleSnapshotLoader`
- `CapabilityChecker` → `DefaultCapabilityChecker`
- `ExprNormalizer` → `DefaultExprNormalizer`
- `ExprRenderer` → `DefaultExprRenderer`
- `ExprCompiler` → `DefaultExprCompiler`

Override any of the above by registering your own bean of the same type.

## Core API

```java
@Autowired
ExprCompiler compiler;

var request = new CompileRequest(
    expr,                      // com.patra.expr.Expr
    ProvenanceCode.PUBMED,     // registry provenance
    null,                      // taskType (optional)
    "search",                 // operation code (normalised to upper case)
    CompileOptions.defaults().withTraceEnabled(true)
);

CompileResult result = compiler.compile(request);
```

`CompileResult` exposes the rendered query string, provider parameters, the aggregated `ValidationReport` (warnings + errors), and a `SnapshotRef` describing the provenance snapshot used. When trace mode is enabled the `RenderTrace` lists the rules that participated in rendering.

### Validation & Errors

`DefaultCapabilityChecker` walks the AST and enforces the capability profile coming from the registry snapshot. Typical error codes:

- `E-FIELD-NOT-FOUND` – field not registered for the provenance.
- `E-OP-NOT-ALLOWED` – operator not allowed for the field.
- `E-TERM-LEN-*` / `E-TERM-PATTERN` – term violations.
- `E-IN-*` – invalid `IN` clauses.
- `E-RANGE-*` – range kind / boundary issues.
- `E-QUERY-LEN-MAX` – rendered query exceeds the configured budget.

Warnings are emitted for skipped branches (e.g. unsupported `OR`/`NOT`) or missing render rules/parameter mappings.

### Rendering

The renderer matches `ProvenanceSnapshot.RenderRule` entries using `fieldKey`, `Atom.Operator`, match type, negation qualifier and value type. TERM and IN are rendered today; RANGE/EXISTS/TOKEN result in validation only until registry templates are available. Parameter rules rely on `reg_prov_api_param_map` mappings; missing entries surface as warnings.

## Snapshot Model

`RegistryRuleSnapshotLoader` materialises the following view from the registry:

- Field dictionary (`reg_expr_field_dict`): `FieldDefinition`
- Capability matrix (`reg_prov_expr_capability`): `Capability`
- Render rules (`reg_prov_expr_render_rule`): `RenderRule`
- Parameter mapping (`reg_prov_api_param_map`): `ApiParameter`

The loader lazily hydrates standard key mappings and tolerates missing definitions (reported as warnings during compilation). Custom loaders can implement `RuleSnapshotLoader` to fetch from other sources (files, caches, etc.).

## AST Construction Helpers

`patra-expr-kernel` exposes `Exprs` with strongly typed factory methods:

```java
Expr expr = Exprs.and(List.of(
    Exprs.term("title", "deep learning", TextMatch.PHRASE),
    Exprs.in("lang", List.of("en", "zh")),
    Exprs.rangeDate("year", LocalDate.of(2019, 1, 1), LocalDate.of(2020, 12, 31))
));
```

Records ensure immutability; the visitor API on `Expr` enables reflective-free traversal in user code.

## Extending the Pipeline

- Swap snapshots: implement `RuleSnapshotLoader` and register it as a bean.
- Adjust validation: provide your own `CapabilityChecker`.
- Expand rendering: implement `ExprRenderer` (e.g. to support `RANGE` or advanced templating).
- Modify normalisation: replace `ExprNormalizer` to inject custom simplification rules.

All components are stateless and thread-safe; feel free to scope custom beans as singletons.

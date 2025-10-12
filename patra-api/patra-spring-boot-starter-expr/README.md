# patra-spring-boot-starter-expr

## Purpose
Auto-configures the expression compiler stack for provenance-aware search: normalization, capability checks, rendering, and compilation.

## Auto-Configuration
- `com.patra.starter.expr.compiler.boot.ExprCompilerAutoConfiguration`
  - Requires registry API clients on classpath to enable snapshot loading:
    - `ProvenanceClient` and `ExprClient` (from `patra-registry-api`)

## Beans and Conditions
- `SnapshotAssembler`
  - Created when `patra.expr.compiler.registry-api.enabled=true` and registry clients are present.
- `RuleSnapshotLoader` → `RegistryRuleSnapshotLoader`
  - Same conditions as `SnapshotAssembler`.
- `CapabilityChecker` → `DefaultCapabilityChecker`
- `ExprNormalizer` → `DefaultExprNormalizer`
- `ExprRenderer` → `DefaultExprRenderer`
- `ExprCompiler` → `DefaultExprCompiler`
  - Requires `RuleSnapshotLoader`, `CapabilityChecker`, `ExprNormalizer`, `ExprRenderer`.

All beans are `@ConditionalOnMissingBean` so you can override selectively.

## Properties
```yaml
patra:
  expr:
    compiler:
      enabled: true
      registry-api:
        enabled: true
        operation-default: SEARCH
```

## Usage Example
```java
@Service
class SearchService {
  private final ExprCompiler compiler;
  SearchService(ExprCompiler compiler) { this.compiler = compiler; }

  CompileResult compileExpr() {
    Expr expr = Exprs.and(List.of(
        Exprs.term("title", "heart failure", TextMatch.ANY),
        Exprs.rangeDate("pubDate", LocalDate.parse("2023-01-01"), LocalDate.parse("2023-12-31"))
    ));
    return compiler.compile(expr, ProvenanceCode.PUBMED);
  }
}
```

## Notes
- Rendering for specific providers is internal to the compiler; outbound HTTP happens via the provenance clients/egress gateway (separate starters).

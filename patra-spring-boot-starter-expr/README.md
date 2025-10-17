# patra-spring-boot-starter-expr

> Expression engine integration — auto-configures expression builders, validators, and rule snapshot loaders.

## 📌 Purpose

Integrates **patra-expr-kernel** with Spring Boot:
- Expression compiler with rule snapshot loading
- Expression builder beans
- Canonicalizer auto-configured
- Expression validation support
- JSON codec beans

---

## 🧭 Std-Key Approach (What You Configure vs What You Send)

The compiler uses provider‑agnostic semantic keys (std_key) during rendering and bridges them to provider parameters at the end.

- Render rules emit only std_keys like `query`, `from`, `to`, `datetype`, `filter` — never provider names.
- A provider‑specific param map (from `patra-registry`) converts std_key → provider param (e.g., PubMed `query→term`).
- MULTI std_keys (e.g., `filter`) default to join transforms; repeated params can be enabled via config if a provider requires it.

Why this matters:
- Seeds (rules/mappings) live in the registry; evolving providers usually doesn’t require code changes.
- One expression compiles to multiple providers by swapping seeds.

Key behaviors controlled by configuration:

```yaml
# Safety modes
expr:
  strict: true                 # Fail fast on missing fn/transform, unsupported NOT
  multi:
    repeat-enabled: false      # Default off; prefer LIST_JOIN/FILTER_JOIN

# Compiler guardrails
patra:
  expr:
    compiler:
      query-param-bridge:
        enabled: true          # Bridge aggregated boolean query via std_key=query
      max-query-length: 5000   # Hard cap (0 disables)
      warn-param-count: 50     # Soft warning (0 disables)
      max-param-count: 100     # Hard error (0 disables)
```

See docs: `docs/expr/01-overview.md`, `docs/expr/02-architecture.md`, `docs/expr/03-compiler-bridge-internals.md`.

## 🔧 Auto-Configurations

### ExprCompiler (Registry-Backed)

When `patra-registry-api` is on classpath, auto-configures:
- `SnapshotAssembler` - Converts registry DTOs to rule snapshots
- `RuleSnapshotLoader` - Loads rules from `patra-registry` via Feign clients
- `ExprCompiler` - Main expression compiler with rule validation

**Requirements:**
- `ProvenanceClient` and `ExprClient` Feign interfaces present (automatically discovered via `patra-spring-cloud-starter-feign`)
- `patra.expr.compiler.registry-api.enabled=true` (default)

**Configuration:**
```yaml
patra:
  expr:
    compiler:
      enabled: true  # Enable compiler
      registry-api:
        enabled: true  # Enable registry-backed rule loading
```

### Expression Utilities
- `CapabilityChecker` - Validates expression capabilities
- `ExprNormalizer` - Normalizes expressions
- `ExprRenderer` - Renders expressions to various formats

## 🔗 Dependencies

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-expr</artifactId>
</dependency>
```

Includes: `patra-expr-kernel`, Jackson

## 🚀 Usage

### Using ExprCompiler

```java
@Component
@RequiredArgsConstructor
public class ExpressionCompilerPortImpl implements ExpressionCompilerPort {

    private final ExprCompiler compiler;  // Auto-wired

    @Override
    public CompileResult compile(String exprJson, ProvenanceCode code) {
        return compiler.compile(exprJson, code);
    }
}
```

### Using Expression Utilities

```java
@Service
@RequiredArgsConstructor
public class PlanExpressionBuilder {

    public PlanExpressionDescriptor build(PlanTriggerNorm norm, ProvenanceConfigSnapshot config) {
        Expr expr = Exprs.and(List.of(
            Exprs.rangeDate("date", norm.windowFrom(), norm.windowTo())
        ));

        ExprCanonicalSnapshot snapshot = ExprCanonicalizer.canonicalize(expr);
        return new PlanExpressionDescriptor(snapshot.hash(), snapshot.canonicalJson());
    }
}
```

## 🔗 Related Documentation

- [Main README](../README.md)
- [patra-spring-cloud-starter-feign](../patra-spring-cloud-starter-feign/README.md) - Feign client auto-configuration
- [patra-expr-kernel](../patra-expr-kernel/README.md) - Expression engine core

---

**Last Updated**: 2025-10-14

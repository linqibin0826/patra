# patra-expr-kernel

A unified, immutable expression AST kernel that powers cross-service filter composition, normalization, and signing.

## 1. Module purpose
- **Responsibility**: Provide a minimal yet stable boolean expression model (AND/OR/NOT/CONST/ATOM) that can be serialized, normalized, and hashed.
- **Primary consumers**: `patra-spring-boot-starter-expr`, `patra-ingest`, and future search/analytics services.
- **Architecture boundary**: Pure Java library with no framework dependencies. The set of operators stays compact; extensions are performed by outer compilers.

## 2. Core capabilities
- **AST definition**: A sealed `Expr` interface and record-based nodes to guarantee immutability and type safety.
- **Serialization protocol**: `ExprJsonCodec` exposes a stable JSON schema with forward-compatibility safeguards.
- **Normalization & signature**: `ExprCanonicalizer` produces canonical JSON plus a SHA-256 fingerprint for caching, idempotency, and audit trails.
- **Visitor pattern**: `ExprVisitor` enables independent translators for ES/SQL/custom engines without coupling to the kernel.
- **Factory helpers**: `Exprs` offers fluent builders to reduce boilerplate when constructing expression trees.

> For full details (AST tables, JSON samples, performance guidance) see `docs/modules/expr-kernel/deep-dive.md`.

## 3. Package layout & dependencies
- Packages: `expr/` (AST), `json/` (codec), `canonical/` (normalization), `visitor/` (visitor contract).
- Dependencies: JDK 21 and Jackson (managed by the parent POM).
- Prohibited: introducing framework dependencies or embedding aggregation/query semantics directly into the AST (those belong to translators).

## 4. Usage & configuration
- **Maven dependency**:
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-expr-kernel</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- **Required configuration**: none. When used with Spring, `patra-spring-boot-starter-expr` wires additional compilers and policies.
- **Typical flow**: build expressions via `Exprs` → serialize with `Exprs.toJson` → feed into `ExprCanonicalizer` to obtain the canonical JSON and hash.

## 5. Observability & operations
- The module itself exposes no runtime metrics; downstream systems should log `canonicalJson`/`hash` values for troubleshooting and cache analysis.

## 6. Testing strategy
- **AST**: validate record invariants (non-null fields, operator/value compatibility).
- **JSON codec**: ensure serialization/round-trip parity and forward compatibility (ignore unknown fields).
- **Canonicalizer**: cover null handling, array deduplication, numeric normalization, and regression snapshots for complex expressions.
- **Visitor**: provide minimal unit coverage for custom translators.

## 7. Roadmap & risks
| Item | Status | Risk/Notes |
|------|--------|------------|
| Publish JSON schema | High | Coordinate upgrades with consumers; schema changes require versioning. |
| Snapshot cache SPI | High | Requires careful handling of thread safety and memory footprint. |
| TextMatch extensions | Mid | Keep operator set minimal; assess compiler compatibility first. |
| Canonical performance tuning | Low | Sorting large arrays may become a bottleneck; benchmark before rollout. |

## 8. References
- Deep dive: `docs/modules/expr-kernel/deep-dive.md`
- Expression compiler starter: `patra-spring-boot-starter-expr/README.md`
- Ingest pipeline example: `docs/process/ingest-dataflow.md`

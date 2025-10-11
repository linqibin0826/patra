# patra-spring-boot-starter-expr

Expression compilation and rendering starter built on top of `patra-expr-kernel`. It turns Registry
snapshots into ready-to-use query fragments and parameter maps.

## 1. Module purpose
- **Responsibility**: Load Registry field capabilities, rendering templates, and parameter mappings to
  normalize, validate, and render expressions.
- **Primary consumers**: `patra-ingest` (planning expressions) plus forthcoming search/analytics services.
- **Architecture boundary**: Stateless Spring Boot starter. Compilation steps can be overridden via
  beans while business logic lives in the calling service.

## 2. Core capabilities
- **Normalization**: Delegate to `ExprCanonicalizer` for consistent JSON snapshots and fingerprints.
- **Capability checking**: Validate field availability, supported operators, value shape, and length constraints.
- **Rendering**: Produce backend-ready query fragments and parameter maps based on rendering rules.
- **Diagnostics**: Surface `ValidationReport` and `RenderTrace` to troubleshoot failures.
- **Registry integration**: Built-in client fetches Registry snapshots and applies operation-type defaults.

Refer to the other starter READMEs (`patra-spring-boot-starter-*`, `patra-spring-cloud-starter-feign`) for comparisons.

## 3. Package layout & dependencies
- Core packages: `compiler` (orchestration), `normalizer`, `renderer`, `report`.
- Dependencies: `patra-expr-kernel`, `patra-spring-boot-starter-core`, and the Registry API client.

## 4. Usage & configuration
- **Maven dependency**:
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-expr</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- **Configuration sample**:
  ```yaml
  patra:
    expr:
      compiler:
        enabled: true
        registry-api:
          enabled: true
          operation-default: SEARCH
  ```
- **Extension points**: override beans such as `ExprCompilerCustomizer`, `CapabilityChecker`, and
  `ExprRenderer` for bespoke behaviour.

## 5. Observability & operations
- Track compilation duration and failure reasons (reuse metrics from the core starter).
- Cache Registry snapshots and monitor their version to avoid compiling expressions against stale data.

## 6. Testing strategy
- Use in-memory snapshots to verify normalization, capability checks, and rendered output.
- Simulate invalid fields/operators/lengths and missing Registry snapshots.
- Validate rendered output together with downstream translators (ES/SQL/etc.).

## 7. Roadmap & risks
| Item | Status | Risk/Notes |
|------|--------|------------|
| Snapshot caching strategy | Planned | Must align with Registry cache invalidation policies. |
| Multi-backend rendering plugins | Planned | Plugin model increases dependency and test complexity. |
| RenderTrace visualization | Planned | Requires diagnostic UI or structured logging format. |

Risks include inconsistent Registry snapshots, expression complexity impacting performance, and bean
override ordering conflicts.

## 8. References
- Other starters: `patra-spring-boot-starter-core/README.md`, `patra-spring-boot-starter-web/README.md`,
  `patra-spring-cloud-starter-feign/README.md`, `patra-spring-boot-starter-mybatis/README.md`
- Expression kernel: `patra-expr-kernel/README.md`
- Registry snapshot deep dive: `docs/modules/registry/deep-dive.md`

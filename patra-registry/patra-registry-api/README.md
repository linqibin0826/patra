# patra-registry-api

`patra-registry-api` provides the public contracts and error codes for the Registry service. It shields downstream services from internal evolution by exposing stable DTOs and exception semantics.

## 1. Module Scope
- **Responsibilities** - Package Registry error codes, Feign interfaces, and shared DTOs for compile-time reuse.
- **Primary consumers** - `patra-ingest`, `patra-gateway-boot`, and internal tooling/SDKs.
- **Boundaries** - Pure contract module. No Spring wiring, no business logic, and no infrastructure dependencies.

## 2. Core Capabilities
- **`RegistryErrorCode` enum** - Defines the `REG-NNNN` taxonomy covering dictionary, configuration, and generic failures.
- **Documentation** - `package-info` notes and `ERROR_CODE_CATALOG.md` record canonical identifiers.
- **Reusable interfaces** - Feign contracts and DTOs that expand alongside service capabilities.

## 3. Structure and Dependencies
- Layout: `error/` (codes) and `dto/` (data structures).
- Dependencies: `patra-common` plus Jakarta Bean Validation for DTO annotations.
- Prohibited: importing Spring frameworks or implementing business behaviours.

## 4. Usage
- Add as a plain JAR dependency:
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-registry-api</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- Runtime configuration is unnecessary; set `patra.error.context-prefix=REG` via the core starter when emitting errors.

## 5. Operations
- The module has no runtime footprint. Downstream services should log and emit metrics using `RegistryErrorCode`.
- Keep `ERROR_CODE_CATALOG.md` up to date so external systems can search and triage errors.

## 6. Testing Guidance
- Maintain compile-time coverage by referencing `RegistryErrorCode` in consuming service tests.
- When introducing new DTOs or interfaces, validate serialization compatibility in integration tests.

## 7. Roadmap & Risks
| Initiative | Status | Notes |
|------------|--------|-------|
| Error code expansion | Ongoing | Follow the append-only policy and update the catalog. |
| DTO stability | Ongoing | Evolve versions with strict backward compatibility. |

## 8. References
- Error catalog: `ERROR_CODE_CATALOG.md`
- Service README: `../README.md`
- Conventions: `docs/conventions/README.md`

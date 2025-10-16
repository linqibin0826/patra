# Implementation Plan: Enhanced Logging System Redesign

**Branch**: `001-logging-starter` | **Date**: 2025-10-15 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-logging-starter/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Redesign the project's logging system to provide high readability, well-defined log levels (ERROR/WARN/INFO/DEBUG/TRACE), improved production problem diagnosis, and enhanced cross-service traceability. The implementation will integrate structured logging with Apache SkyWalking trace context propagation, implement automatic sensitive data sanitization, enable dynamic log level configuration via Nacos, and systematically update all existing log output across microservices to conform to new standards. The approach leverages existing SLF4J facade and integrates @XSlf4j from Lombok for unified logging utilities.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: SLF4J (logging facade), Logback (implementation), Lombok @XSlf4j, Apache SkyWalking (trace context), Spring Boot 3.2.4, Nacos (dynamic configuration)
**Storage**: MySQL 8.x for outbox pattern events (if event-driven logging needed), file system with rotation for fallback logs
**Testing**: JUnit 5, TestContainers for integration tests, ArchUnit for architectural compliance
**Target Platform**: Linux server (Docker containers), production deployed via Kubernetes
**Project Type**: Microservices architecture (multiple Spring Boot applications)
**Performance Goals**: <5% throughput impact from enhanced logging, asynchronous appenders for non-blocking log writes, <50ms p95 latency for log sanitization
**Constraints**: Must remain compatible with existing SLF4J infrastructure and log aggregation tools (ELK stack), sensitive data sanitization must be foolproof, trace context propagation must work across all microservices without business logic refactoring
**Scale/Scope**: 10+ microservices, ~100k LOC affected for log output updates, support for 10,000+ records/minute batch processing with aggregated logging

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### ✅ Principle I: Hexagonal Architecture + DDD
- **Status**: COMPLIANT
- **Rationale**: Logging infrastructure is cross-cutting concern implemented as utilities/starters, NOT domain logic. No violation of layer boundaries since logging support is added to all layers without changing dependency direction. Logging utilities will be placed in `patra-common` or new `patra-spring-boot-starter-logging` following existing starter pattern.

### ✅ Principle II: Dependency Direction Rules
- **Status**: COMPLIANT
- **Rationale**: Logging utilities depend on SLF4J (already ubiquitous) and will be injected through starters. No reverse dependencies introduced. Domain layer will continue using plain SLF4J (compatible with pure Java requirement). Trace context propagation handled via Spring interceptors/filters in adapter layer, NOT domain layer.

### ✅ Principle III: Event-Driven Architecture with Outbox Pattern
- **Status**: COMPLIANT (NOT APPLICABLE)
- **Rationale**: Logging system does NOT introduce new inter-service communication. If audit logging requires event publishing, it will use existing outbox pattern. This feature focuses on infrastructure logging, not business events.

### ✅ Principle IV: Idempotency & Safe Retries
- **Status**: COMPLIANT (NOT APPLICABLE)
- **Rationale**: Logging operations are idempotent by nature (writing same log multiple times is acceptable). Sanitization is pure function (deterministic). No retry semantics needed for logging itself.

### ✅ Principle V: Temporal Configuration with Effective Time Ranges
- **Status**: COMPLIANT
- **Rationale**: Dynamic log level configuration via Nacos follows existing configuration patterns. Log level changes are effective immediately without temporal slicing (acceptable for operational configuration). No impact on in-flight business operations since logging is non-blocking.

### ✅ Principle VI: Test-First Development
- **Status**: COMPLIANT
- **Rationale**:
  - Unit tests for sanitization logic (pure functions in `patra-common`)
  - Integration tests for trace context propagation (TestContainers in boot modules)
  - ArchUnit tests to verify NO logging dependencies in domain layer violate purity
  - Manual testing for dynamic log level changes via Nacos

### ✅ Principle VII: Simplicity & YAGNI
- **Status**: COMPLIANT
- **Rationale**:
  - Reuse existing SLF4J + Logback (NO new logging framework)
  - Reuse existing SkyWalking trace context (NO custom tracing)
  - Add minimal utilities for common patterns (sanitization, context extraction)
  - Defer structured logging formats (JSON) until proven necessary (start with enhanced pattern layout)
  - Focus on "make existing logs better" NOT "build observability platform"

### Summary: ALL GATES PASSED ✅
No complexity justification required. Feature aligns with all constitutional principles.

## Project Structure

### Documentation (this feature)

```
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```
# Cross-cutting logging infrastructure (NEW or ENHANCED)
patra-common/
└── src/main/java/com/papertrace/common/
    └── logging/
        ├── sanitizer/              # Sensitive data sanitization
        │   ├── SanitizationRule.java
        │   ├── FieldSanitizer.java
        │   └── LogSanitizer.java
        └── context/                # Trace context utilities
            ├── TraceContextHolder.java
            └── LogContextEnricher.java

# Logging-specific Spring Boot starter (NEW)
patra-spring-boot-starter-logging/
├── pom.xml
└── src/main/
    ├── java/com/papertrace/starter/logging/
    │   ├── autoconfigure/
    │   │   ├── LoggingAutoConfiguration.java
    │   │   ├── TraceContextAutoConfiguration.java
    │   │   └── SanitizationAutoConfiguration.java
    │   ├── filter/
    │   │   └── TraceContextFilter.java          # Propagate trace to MDC
    │   ├── interceptor/
    │   │   └── TraceContextInterceptor.java     # Feign/RestTemplate trace
    │   └── aspect/
    │       └── LoggingAspect.java               # Optional: auto-log entry/exit
    └── resources/
        ├── META-INF/spring.factories
        ├── logback-spring.xml                    # Enhanced pattern layout
        └── application-logging.yml               # Default log levels

# Updated microservices (EXISTING - changes needed)
patra-registry/
├── patra-registry-boot/
│   └── src/main/resources/
│       └── logback-spring.xml                    # Import starter config
├── patra-registry-adapter/                       # Update: @XSlf4j + sanitize
├── patra-registry-app/                           # Update: @XSlf4j + trace context
├── patra-registry-domain/                        # Keep: Plain SLF4J only
└── patra-registry-infra/                         # Update: @XSlf4j + query logging

patra-ingest/                                      # Same structure as registry
patra-gateway-boot/                                # Same structure as registry
# ... (all other microservices follow same pattern)

# Configuration (EXISTING - enhanced)
docker/nacos/config/
└── logging/
    ├── logging-common.yml                         # Shared log level config
    └── logging-{service}.yml                      # Service-specific overrides

# Documentation (generated by this plan)
docs/
└── logging/
    ├── logging-standards.md                       # Developer guide (quickstart)
    ├── troubleshooting-guide.md                   # Operations guide
    └── migration-checklist.md                     # Migration steps per module
```

**Structure Decision**: This is a **microservices project** with cross-cutting infrastructure changes. The logging system is implemented as:
1. **Shared utilities** in `patra-common` (sanitization, context extraction)
2. **Spring Boot starter** in new `patra-spring-boot-starter-logging` (auto-configuration, filters, aspects)
3. **Configuration** via Nacos for dynamic log levels
4. **Systematic updates** across all microservices to adopt new logging patterns

This follows the existing pattern established by `patra-spring-boot-starter-core`, `patra-spring-boot-starter-web`, etc.

## Complexity Tracking

*Fill ONLY if Constitution Check has violations that must be justified*

**No violations to justify. All constitution checks passed.**

---

## Post-Design Constitution Re-Evaluation

*Re-checked after completing Phase 1 design artifacts*

### ✅ Principle I: Hexagonal Architecture + DDD
- **Status**: COMPLIANT (Confirmed)
- **Validation**: Design artifacts confirm logging utilities placed in `patra-common` and `patra-spring-boot-starter-logging`, following existing starter pattern. Domain layer continues using plain SLF4J (pure Java). No domain logic contamination.

### ✅ Principle II: Dependency Direction Rules
- **Status**: COMPLIANT (Confirmed)
- **Validation**:
  - `patra-common` utilities (sanitization, trace context) are pure Java with minimal SLF4J dependency
  - Starter depends on Spring Boot + SLF4J (adapter/infra concern, NOT domain)
  - Domain layer continues using `Logger` declaration (no Lombok, no Spring)
  - Trace context propagation implemented via filters/interceptors (adapter layer)
  - No reverse dependencies introduced

### ✅ Principle III: Event-Driven Architecture with Outbox Pattern
- **Status**: COMPLIANT (Confirmed)
- **Validation**: Logging does not introduce new inter-service communication. Trace context propagation in message consumers follows existing patterns (extract from headers, populate MDC).

### ✅ Principle IV: Idempotency & Safe Retries
- **Status**: COMPLIANT (Confirmed)
- **Validation**: Logging operations are idempotent by nature. Sanitization is pure function (deterministic).

### ✅ Principle V: Temporal Configuration with Effective Time Ranges
- **Status**: COMPLIANT (Confirmed)
- **Validation**: Dynamic log level changes via Nacos apply immediately (acceptable for operational config). No impact on in-flight business operations since logging is non-blocking with async appenders.

### ✅ Principle VI: Test-First Development
- **Status**: COMPLIANT (Confirmed)
- **Validation**:
  - Unit tests planned for sanitization logic (`DefaultLogSanitizer`)
  - Integration tests planned for trace context propagation (TestContainers)
  - ArchUnit tests planned to verify domain layer purity (no Lombok, no Spring)
  - Manual testing for Nacos dynamic log level changes

### ✅ Principle VII: Simplicity & YAGNI
- **Status**: COMPLIANT (Confirmed)
- **Validation**:
  - Reused existing infrastructure: SLF4J, Logback, SkyWalking, Nacos
  - Deferred JSON structured logging (start with pattern layout)
  - Sanitization rules hardcoded initially (defer DB/UI until proven necessary)
  - Minimal API surface (3 utility interfaces: LogSanitizer, TraceContextHolder, LogContextEnricher)
  - No over-engineering: simple regex patterns, standard MDC, Logback async appenders

### Summary: ALL GATES STILL PASSED ✅

**Post-Design Confirmation**: The detailed design artifacts (data-model.md, contracts/, quickstart.md) confirm that the implementation plan adheres to all constitutional principles. No deviations introduced during design phase.

**Key Validations**:
1. **Architecture Boundaries Respected**: Logging utilities are cross-cutting infrastructure, not domain logic
2. **Dependency Direction Maintained**: No reverse dependencies, domain stays pure Java
3. **Simplicity Preserved**: Reused existing frameworks, deferred unnecessary complexity
4. **No Over-Engineering**: Started with minimal viable design, deferred advanced features (JSON logs, DB-backed rules)

**Proceed to Phase 2 (tasks.md generation) with confidence.**

# Implementation Tasks: Enhanced Logging System Redesign

**Feature**: 001-logging-starter | **Branch**: `001-logging-starter` | **Date**: 2025-10-16

## Overview

This document provides an actionable, dependency-ordered task breakdown for implementing the enhanced logging system across the Papertrace microservices platform. Tasks are organized by user story priority to enable incremental delivery and independent testing.

---

## Task Summary

| Phase | User Story | Task Count | Parallel Tasks | Status |
|-------|------------|------------|----------------|--------|
| Phase 1 | Setup | 12 | 3 | Pending |
| Phase 2 | Foundational | 8 | 4 | Pending |
| Phase 3 | US1 (P1) - Production Diagnosis | 19 | 8 | Pending |
| Phase 4 | US2 (P1) - Dynamic Log Levels | 10 | 5 | Pending |
| Phase 5 | US3 (P2) - Request Tracing | 12 | 6 | Pending |
| Phase 6 | US4 (P2) - Consistent Logging | 19 | 10 | Pending |
| Phase 7 | Polish & Cross-Cutting | 10 | 4 | Pending |
| **Total** | - | **90** | **40** | - |

---

## Dependencies & Execution Order

### Critical Path
```
Phase 1 (Setup)
    ↓
Phase 2 (Foundational - BLOCKING)
    ↓
Phase 3 (US1) ←─ HIGHEST PRIORITY (P1)
    ↓
Phase 4 (US2) ←─ HIGH PRIORITY (P1)
    ↓
Phase 5 (US3) ←─ MEDIUM PRIORITY (P2)
    ↓
Phase 6 (US4) ←─ MEDIUM PRIORITY (P2)
    ↓
Phase 7 (Polish)
```

### User Story Independence
- **US1** and **US2** are independent (can be developed in parallel after Phase 2)
- **US3** depends on US1 (trace context infrastructure)
- **US4** depends on US1 and US2 (utilities and log levels established)

---

## Phase 1: Setup & Infrastructure

**Goal**: Establish foundational project structure, dependencies, and development environment

**Duration Estimate**: 2-3 days

### Maven Module Setup

- [ ] T001 Create `patra-spring-boot-starter-logging` Maven module in patra-spring-boot-starter-logging/pom.xml
- [ ] T002 [P] Add SLF4J and Logback dependencies to starter pom.xml
- [ ] T003 [P] Add SkyWalking toolkit dependency for trace context integration in starter pom.xml
- [ ] T004 [P] Add Spring Boot auto-configuration dependencies to starter pom.xml

### Common Utilities Structure

- [ ] T005 Create logging package structure in patra-common/src/main/java/com/papertrace/common/logging/
- [ ] T006 [P] Create sanitizer subpackage in patra-common/src/main/java/com/papertrace/common/logging/sanitizer/
- [ ] T007 [P] Create context subpackage in patra-common/src/main/java/com/papertrace/common/logging/context/

### Configuration Infrastructure

- [ ] T008 Create Nacos configuration directory in docker/nacos/config/logging/
- [ ] T009 [P] Create logback-spring.xml template in patra-spring-boot-starter-logging/src/main/resources/
- [ ] T010 [P] Create spring.factories for auto-configuration in patra-spring-boot-starter-logging/src/main/resources/META-INF/
- [ ] T011 [P] Create application-logging.yml with default properties in patra-spring-boot-starter-logging/src/main/resources/
- [ ] T012 Update parent pom.xml to include new starter module in dependency management

---

## Phase 2: Foundational Components (BLOCKING)

**Goal**: Implement core utilities and auto-configuration that ALL user stories depend on

**Duration Estimate**: 3-4 days

**⚠️ CRITICAL**: This phase MUST be completed before any user story implementation begins. All user stories depend on these foundational components.

### Core Value Objects

- [ ] T013 Implement DistributedTraceContext record in patra-common/src/main/java/com/papertrace/common/logging/context/DistributedTraceContext.java
- [ ] T014 [P] Implement LogSanitizer interface in patra-common/src/main/java/com/papertrace/common/logging/sanitizer/LogSanitizer.java
- [ ] T015 [P] Implement TraceContextHolder interface in patra-common/src/main/java/com/papertrace/common/logging/context/TraceContextHolder.java
- [ ] T016 [P] Implement LogContextEnricher interface in patra-common/src/main/java/com/papertrace/common/logging/context/LogContextEnricher.java

### Default Implementations

- [ ] T017 Implement DefaultLogSanitizer with regex patterns in patra-common/src/main/java/com/papertrace/common/logging/sanitizer/DefaultLogSanitizer.java
- [ ] T018 [P] Implement DefaultTraceContextHolder with SkyWalking integration in patra-common/src/main/java/com/papertrace/common/logging/context/DefaultTraceContextHolder.java
- [ ] T019 [P] Implement DefaultLogContextEnricher with MDC management in patra-common/src/main/java/com/papertrace/common/logging/context/DefaultLogContextEnricher.java
- [ ] T020 [P] Implement MdcTaskDecorator for async MDC propagation in patra-spring-boot-starter-logging/src/main/java/com/papertrace/starter/logging/async/MdcTaskDecorator.java

---

## Phase 3: User Story 1 - Developer Diagnoses Production Issues (P1)

**Priority**: P1 (HIGHEST)
**Goal**: Enable developers to diagnose production issues using clear, traceable logs with full context
**Duration Estimate**: 5-6 days

**Independent Test Criteria** (from spec.md):
- Trigger known error scenario (external API timeout during batch processing)
- Search logs by trace ID → verify complete request chain retrieved
- Filter by correlation ID → verify batch job distinction
- View exception log → verify stack trace, input context, and business operation included

### Trace Context Propagation (FR-002, FR-003)

- [ ] T021 [P] [US1] Implement TraceContextFilter for servlet requests in patra-spring-boot-starter-logging/src/main/java/com/papertrace/starter/logging/filter/TraceContextFilter.java
- [ ] T022 [P] [US1] Implement TraceContextInterceptor for Feign clients in patra-spring-boot-starter-logging/src/main/java/com/papertrace/starter/logging/interceptor/TraceContextInterceptor.java
- [ ] T023 [P] [US1] Implement RestTemplateInterceptor for trace propagation in patra-spring-boot-starter-logging/src/main/java/com/papertrace/starter/logging/interceptor/RestTemplateInterceptor.java
- [ ] T024 [US1] Implement TraceContextAutoConfiguration to register filter and interceptors in patra-spring-boot-starter-logging/src/main/java/com/papertrace/starter/logging/autoconfigure/TraceContextAutoConfiguration.java

### Async Context Propagation (FR-004)

- [ ] T025 [P] [US1] Implement AsyncConfiguration with MdcTaskDecorator in patra-spring-boot-starter-logging/src/main/java/com/papertrace/starter/logging/autoconfigure/AsyncAutoConfiguration.java
- [ ] T026 [P] [US1] Implement RocketMQMessageListenerDecorator for MQ trace propagation in patra-spring-boot-starter-logging/src/main/java/com/papertrace/starter/logging/mq/RocketMQMessageListenerDecorator.java

### Enhanced Logback Configuration (FR-002, FR-005)

- [ ] T027 [US1] Create enhanced logback-spring.xml with MDC pattern in patra-spring-boot-starter-logging/src/main/resources/logback-spring.xml
- [ ] T028 [P] [US1] Configure async appenders with proper queue settings in logback-spring.xml
- [ ] T029 [P] [US1] Configure console and file appenders with trace context pattern in logback-spring.xml

### Exception Logging Standards (FR-005)

- [ ] T030 [US1] Document exception logging standards in docs/logging/exception-logging-guide.md
- [ ] T031 [US1] Create ExceptionLoggingAspect for automatic context capture in patra-spring-boot-starter-logging/src/main/java/com/papertrace/starter/logging/aspect/ExceptionLoggingAspect.java

### Pilot Service Integration (patra-registry)

- [ ] T032 [US1] Add logging starter dependency to patra-registry-boot/pom.xml
- [ ] T033 [US1] Remove legacy logback.xml from patra-registry-boot/src/main/resources/
- [ ] T034 [US1] Read patra-registry-adapter/README.md, then update adapter layer logging with trace context
- [ ] T035 [US1] Read patra-registry-app/README.md, then update application layer logging with trace context
- [ ] T036 [US1] Read patra-registry-infra/README.md, then update infrastructure layer logging with trace context
- [ ] T037 [US1] Read patra-registry-domain/README.md, then verify domain layer uses plain Logger (no Lombok)
- [ ] T038 [US1] Implement trace context fallback: generate new trace ID with WARN log when context missing from request in TraceContextFilter
- [ ] T039 [US1] Test trace context propagation end-to-end in patra-registry integration tests and validate fallback behavior

**Parallel Execution Opportunities**:
- T021, T022, T023 (different interceptors)
- T025, T026 (async and MQ)
- T028, T029 (appender configs)
- T032-T037 (pilot service updates - different modules)

---

## Phase 4: User Story 2 - Dynamic Log Level Configuration (P1)

**Priority**: P1 (HIGH)
**Goal**: Enable operations team to dynamically adjust log levels without redeployment
**Duration Estimate**: 3-4 days

**Independent Test Criteria** (from spec.md):
- Set INFO level → verify only key business events logged
- Change to DEBUG for specific module → verify detailed logs appear without affecting others
- Test retry scenarios → verify WARN for retries, ERROR for final failure
- Test auth failures → verify WARN logs with sanitized details

### Nacos Integration (FR-011, SC-007)

- [ ] T040 [P] [US2] Create logging-common.yml Nacos config with default levels in docker/nacos/config/logging/logging-common.yml
- [ ] T041 [P] [US2] Create logging-patra-registry.yml service-specific config in docker/nacos/config/logging/logging-patra-registry.yml
- [ ] T042 [P] [US2] Create logging-patra-ingest.yml service-specific config in docker/nacos/config/logging/logging-patra-ingest.yml
- [ ] T043 [US2] Implement DynamicLoggingConfiguration with Nacos listeners in patra-spring-boot-starter-logging/src/main/java/com/papertrace/starter/logging/autoconfigure/DynamicLoggingConfiguration.java

### Log Level Semantic Guidelines (FR-001)

- [ ] T044 [P] [US2] Document log level semantic guidelines in docs/logging/log-level-guidelines.md
- [ ] T045 [P] [US2] Create log level usage examples for each layer in docs/logging/log-level-examples.md

### Testing & Validation

- [ ] T046 [US2] Implement LogLevelConfigurationTest for Nacos integration in patra-spring-boot-starter-logging/src/test/java/
- [ ] T047 [US2] Test dynamic log level change in patra-registry (INFO → DEBUG → INFO)
- [ ] T048 [US2] Verify log level changes take effect within 60 seconds (SC-007)
- [ ] T049 [US2] Document troubleshooting guide for log level issues in docs/logging/troubleshooting-log-levels.md

**Parallel Execution Opportunities**:
- T040, T041, T042 (Nacos config files)
- T044, T045 (documentation)
- T046, T047, T048 (testing tasks)

---

## Phase 5: User Story 3 - Cross-Service Request Tracing (P2)

**Priority**: P2 (MEDIUM)
**Goal**: Enable developers to trace requests across multiple microservices
**Duration Estimate**: 4-5 days

**Dependencies**: US1 (trace context infrastructure must be in place)

**Independent Test Criteria** (from spec.md):
- Initiate request through gateway → ingest → registry → external API
- Verify all logs contain same trace ID and correlation ID
- Test async processing → verify trace context maintained across MQ boundaries
- Search by trace ID → verify chronological ordering and service boundaries visible

### Gateway Integration (Entry Point)

- [ ] T050 [P] [US3] Integrate logging starter in patra-gateway-boot/pom.xml
- [ ] T051 [P] [US3] Configure TraceContextFilter at highest precedence in gateway
- [ ] T052 [P] [US3] Add service identifier "[patra-gateway]" to logback pattern
- [ ] T053 [US3] Read patra-gateway-boot/README.md, then update gateway controllers with trace-aware logging

### Ingest Service Integration (High-Volume)

- [ ] T054 [P] [US3] Integrate logging starter in patra-ingest-boot/pom.xml
- [ ] T055 [P] [US3] Read patra-ingest-adapter/README.md, then update batch processing jobs with correlation ID
- [ ] T056 [P] [US3] Read patra-ingest-app/README.md, then update orchestrators with trace context
- [ ] T057 [US3] Read patra-ingest-infra/README.md, then update external API clients with trace propagation

### Cross-Service Trace Validation

- [ ] T058 [US3] Create end-to-end trace test spanning gateway → registry → ingest
- [ ] T059 [US3] Verify trace ID propagation across synchronous calls
- [ ] T060 [US3] Verify correlation ID propagation across async boundaries (RocketMQ)
- [ ] T061 [US3] Document trace context troubleshooting in docs/logging/trace-context-troubleshooting.md

**Parallel Execution Opportunities**:
- T050, T054 (different services)
- T051, T055 (different components)
- T052, T056 (different configurations)
- T059, T060 (different test scenarios)

---

## Phase 6: User Story 4 - Consistent Logging Standards (P2)

**Priority**: P2 (MEDIUM)
**Goal**: Provide developers with unified logging utilities and clear guidelines
**Duration Estimate**: 5-6 days

**Dependencies**: US1 (trace context), US2 (log levels)

**Independent Test Criteria** (from spec.md):
- Add logging to new orchestrator → verify automatic trace context inclusion
- Attempt to log sensitive data → verify automatic sanitization
- Log external API call → verify standardized format (URL, status, duration, error)

### Sensitive Data Sanitization (FR-008, SC-006)

- [ ] T062 [P] [US4] Implement hardcoded sanitization rules in DefaultLogSanitizer (email, phone, credit card, SSN, auth headers)
- [ ] T063 [P] [US4] Implement sanitizeJson() method for JSON-specific sanitization
- [ ] T064 [P] [US4] Implement sanitizeObject() method for DTO/entity sanitization
- [ ] T065 [US4] Create SanitizationAutoConfiguration to register sanitizer bean in patra-spring-boot-starter-logging/src/main/java/com/papertrace/starter/logging/autoconfigure/SanitizationAutoConfiguration.java

### External API Call Logging (FR-006, SC-008)

- [ ] T066 [P] [US4] Create ApiCallLogger utility in patra-common/src/main/java/com/papertrace/common/logging/ApiCallLogger.java
- [ ] T067 [P] [US4] Implement Feign RequestInterceptor with ApiCallLogger
- [ ] T068 [P] [US4] Implement RestTemplate ClientHttpRequestInterceptor with ApiCallLogger

### Database Failures Logging (FR-007, SC-008)

- [ ] T069 [P] [US4] Create DbFailureLogger utility in patra-common/src/main/java/com/papertrace/common/logging/persistence/DbFailureLogger.java
- [ ] T070 [US4] Implement MyBatis-Plus interceptor for logging failed DB operations in patra-common/src/main/java/com/papertrace/common/logging/persistence/DbFailureLoggingInterceptor.java

### Authentication & Authorization Logging (FR-009, SC-008)

- [ ] T071 [P] [US4] Create SecurityEventLogger utility in patra-spring-boot-starter-logging/src/main/java/com/papertrace/starter/logging/security/SecurityEventLogger.java
- [ ] T072 [US4] Implement Spring Security AuthenticationEventPublisher listener to log auth events in patra-spring-boot-starter-logging/src/main/java/com/papertrace/starter/logging/security/AuthenticationEventLogger.java

### Batch Processing Logging (FR-010)

- [ ] T073 [P] [US4] Create BatchProcessingLogger utility in patra-common/src/main/java/com/papertrace/common/logging/BatchProcessingLogger.java
- [ ] T074 [US4] Document batch logging patterns (summary at INFO, details at DEBUG) in docs/logging/batch-logging-guide.md

### Log Sampling for High-Frequency Events (Edge Case Handling)

- [ ] T075 [P] [US4] Implement SamplingFilter for DEBUG/TRACE logs with configurable threshold (default 100 logs/sec) in patra-spring-boot-starter-logging/src/main/java/com/papertrace/starter/logging/filter/SamplingFilter.java
- [ ] T076 [US4] Document log sampling configuration and usage in docs/logging/log-sampling-guide.md

### Developer Documentation

- [ ] T077 [P] [US4] Verify quickstart guide completeness - ALREADY CREATED at specs/001-logging-starter/quickstart.md (mark complete if satisfactory)
- [ ] T078 [P] [US4] Create layer-specific logging examples in docs/logging/layer-specific-examples.md
- [ ] T079 [P] [US4] Create common patterns guide in docs/logging/common-patterns.md
- [ ] T080 [US4] Create troubleshooting guide in docs/logging/troubleshooting.md
- [ ] T081 [US4] Create FAQ document in docs/logging/faq.md

**Parallel Execution Opportunities**:
- T062, T063, T064 (sanitization methods)
- T066, T067, T068, T069, T071, T073, T075 (utility classes)
- T077, T078, T079, T080, T081 (documentation)

---

## Phase 7: Polish & Cross-Cutting Concerns

**Goal**: Complete remaining microservices migration, testing, and performance validation
**Duration Estimate**: 6-8 days

### Remaining Microservices Migration

- [ ] T082 [P] Read each module's README.md first, then migrate remaining microservices (patra-egress-gateway, patra-provenance, etc.) following established pattern
- [ ] T083 [P] Read adapter README.md for each service, then update all adapter layers with @Slf4j and sanitization
- [ ] T084 [P] Read app README.md for each service, then update all application layers with trace context
- [ ] T085 Read domain README.md for each service, then validate all domain layers use plain Logger (no Lombok)

### Testing & Validation

- [ ] T086 [P] Create unit tests for DefaultLogSanitizer with known sensitive patterns in patra-common/src/test/java/
- [ ] T087 [P] Create integration tests for trace context propagation in patra-spring-boot-starter-logging/src/test/java/
- [ ] T088 Create ArchUnit tests to verify domain layer purity (no Lombok, no Spring) and @Slf4j usage in other layers in patra-registry/patra-registry-boot/src/test/java/

### Performance & Compliance

- [ ] T089 [P] Establish baseline throughput and latency metrics from current production or staging (before logging enhancement)
- [ ] T090 [P] Conduct performance testing to verify <5% throughput impact compared to baseline (SC-004)
- [ ] T091 [P] Run automated PII scanning to verify zero sensitive data in logs (SC-006)
- [ ] T092 [P] Validate 40% log volume reduction at INFO level compared to baseline (SC-004)
- [ ] T093 Design and execute controlled incident response test (before/after comparison) to measure time-to-resolution improvement (target 50% reduction per SC-005)

### Final Documentation

- [ ] T094 Update all module READMEs with logging examples
- [ ] T095 Create operations guide for log level management in docs/logging/operations-guide.md
- [ ] T096 Create security audit checklist for sanitization rules in docs/logging/security-audit-checklist.md

**Parallel Execution Opportunities**:
- T082, T083, T084 (different microservices and layers)
- T086, T087, T088 (different test suites)
- T089, T091 (baseline and scanning - can run independently)

---

## Implementation Strategy

### MVP Scope (Phase 1-3)
**Goal**: Deliver core production diagnosis capability (US1)
- Infrastructure setup (Phase 1)
- Foundational components (Phase 2)
- Trace context propagation and pilot service (Phase 3 - US1)

**Deliverable**: `patra-registry` pilot service with full trace context propagation, enabling production issue diagnosis

### Incremental Delivery
1. **Week 1-2**: Phases 1-3 (Setup + Foundation + US1) → MVP deployed to staging
2. **Week 3**: Phase 4 (US2) → Dynamic log levels enabled for operations team
3. **Week 4**: Phase 5 (US3) → Gateway and ingest services migrated, cross-service tracing validated
4. **Week 5**: Phase 6 (US4) → Developer utilities and documentation complete
5. **Week 6**: Phase 7 (Polish) → All services migrated, performance validated, production ready

### Risk Mitigation
- **Phase 2 is BLOCKING**: Do not proceed to user stories until foundational components are tested
- **Pilot service first**: Validate approach in `patra-registry` before rolling out to other services
- **Performance monitoring**: Track metrics at each phase, rollback if >5% throughput impact
- **Backward compatibility**: Keep legacy logging operational during migration

---

## Testing Strategy

### Unit Tests (Per User Story)
- **US1**: TraceContextFilter, TraceContextInterceptor, MdcTaskDecorator
- **US2**: DynamicLoggingConfiguration, Nacos listener integration
- **US3**: Cross-service trace propagation
- **US4**: DefaultLogSanitizer (email, phone, CC, SSN patterns), ApiCallLogger, BatchProcessingLogger

### Integration Tests (Per User Story)
- **US1**: End-to-end trace context in patra-registry (controller → orchestrator → repository → external API)
- **US2**: Dynamic log level changes via Nacos (verify within 60s)
- **US3**: Gateway → registry → ingest trace propagation
- **US4**: Sanitization in real log output, API call logging with Feign

### ArchUnit Tests
- Verify domain layer has NO Lombok dependencies (@Slf4j forbidden)
- Verify domain layer has NO Spring dependencies
- Verify all adapter/app/infra layers use @Slf4j

### Performance Tests
- <5% throughput impact with async appenders (SC-004)
- <50ms p95 sanitization latency
- 40% log volume reduction at INFO level (SC-004)

---

## Success Metrics (from spec.md)

Track these metrics during and after implementation:

| Metric | Target | User Story | Validation Task |
|--------|--------|------------|-----------------|
| SC-001 | Diagnose issues in <10 min (90% cases) | US1 | T093 - Controlled incident response test |
| SC-002 | 100% trace ID coverage (sync), 95% (async) | US1, US3 | T058, T059, T060 - Automated log analysis |
| SC-003 | 100% code using unified utilities | US4 | T088 - ArchUnit tests |
| SC-004 | 40% log volume reduction at INFO | US1, US2 | T089, T092 - Log aggregation metrics comparison |
| SC-005 | 50% faster root cause identification | US1 | T093 - Before/after incident response timing |
| SC-006 | Zero sensitive data in logs | US4 | T091 - Automated PII scanning |
| SC-007 | Log level changes within 60s | US2 | T048 - Manual testing |
| SC-008 | 100% audit logging (API/DB/auth) | US4 | T066-T072 - Implementation validation |

---

## Rollback Plan

Each phase is independently deployable. If issues arise:

1. **Phase 1-2 issues**: Fix in `patra-common` or starter, redeploy
2. **Phase 3 (US1) issues**: Revert pilot service to legacy logging, troubleshoot starter
3. **Phase 4 (US2) issues**: Disable Nacos listeners, use static log levels
4. **Phase 5-6 issues**: Rollback affected services individually, others continue using new logging
5. **Phase 7 issues**: Defer remaining migrations, keep hybrid state temporarily

**Critical**: Maintain backward compatibility during migration. Services can run with mix of old/new logging.

---

## Notes

- **[P] marker**: Indicates task can be parallelized (different files, no dependencies)
- **[US#] label**: Maps task to user story from spec.md
- **File paths**: All tasks include explicit file paths for immediate execution
- **Test-first**: US1-US4 include independent test criteria from spec.md
- **Phased approach**: Minimizes risk, enables incremental delivery
- **MVP first**: Focus on US1 (production diagnosis) as highest priority

---

**Ready to execute. Start with Phase 1 (Setup) → Phase 2 (Foundational - BLOCKING) → Phase 3 (US1 - Highest Priority).**

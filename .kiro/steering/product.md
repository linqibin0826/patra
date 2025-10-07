---
inclusion: always
---

# Product Overview

Papertrace is a medical literature data platform focused on unified collection, standardization, and intelligent analysis of scientific research literature. It provides a high-quality data foundation for search, profiling, and insights.

## Core Objectives

- **Data Integration**: Connect 10+ literature sources and build a Single Source of Truth (SSOT)
- **Traceability**: Ensure end-to-end traceability from collection → parsing → storage
- **Maintainability**: AI-generated codebase maintained by a single developer, requiring systematic documentation

## Key Modules

- **patra-registry**: Configuration SSOT managing provenance, dictionaries, and expressions
- **patra-ingest**: Collection planning engine handling scheduling, windowing, task assembly, and Outbox publishing
- **patra-egress-gateway**: Egress gateway (南向网关) for unified external service calls with resilience capabilities (rate limiting, retry, circuit breaker, timeout)
- **patra-spring-boot-starter-provenance**: Literature data source client starter, encapsulating PubMed, EPMC and other data source APIs
- **patra-gateway-boot**: API gateway for routing, authentication, and error alignment
- **patra-common**: Cross-service domain base classes, error models, JSON utilities
- **patra-expr-kernel**: Expression AST engine for deterministic rule evaluation
- **Starters**: Auto-configuration for core/web/feign/mybatis capabilities

## Current Focus

- ✅ Completed: patra-egress-gateway (南向网关核心功能)
- 🔄 In Progress: patra-spring-boot-starter-provenance (文献数据源客户端 Starter)
- Stabilize data ingestion pipeline
- Enhance Registry configuration governance
- Strengthen error handling and observability standards

## Module Development Status

### patra-egress-gateway ✅
- **Status**: Completed
- **Responsibility**: Egress gateway for unified external service calls
- **Core Features**: Resilience capabilities (retry, circuit breaker, rate limiting), observability, configuration management
- **API**: Provides EgressGatewayClient for other modules to call external services

### patra-spring-boot-starter-provenance 🔄
- **Status**: Specification completed, ready for development
- **Responsibility**: Literature data source client Starter, encapsulating PubMed, EPMC and other data source APIs
- **Core Features**: Strongly-typed API encapsulation, configuration management, gateway invocation, performance metrics recording
- **API**: Provides PubMedClient, EPMCClient for business modules to call literature data sources
- **Estimated Time**: 23-31 hours (3-4 days)

## Module Dependencies

```
Business Modules
  ↓ depends on
patra-spring-boot-starter-provenance
  ↓ depends on
patra-egress-gateway-api
  ↓ implements
patra-egress-gateway
  ↓ calls
External Services (PubMed, EPMC, Crossref, etc.)
```

### Detailed Dependencies

```
Business Modules
  ↓ depends on
patra-spring-boot-starter-provenance
  ↓ depends on
patra-egress-gateway-api (EgressGatewayClient)
  ↓ depends on
patra-registry-api (ProvenanceConfigResp)
  ↓ depends on
patra-common
```

## Development Priorities

### P0 (Must Complete) ✅
1. **patra-egress-gateway**: Egress gateway core features
   - ✅ External service call encapsulation
   - ✅ Resilience capabilities implementation
   - ✅ Basic observability

### P1 (Important) 🔄
2. **patra-spring-boot-starter-provenance**: Literature data source client Starter
   - 🔄 PubMed API encapsulation (esearch, efetch)
   - 🔄 EPMC API encapsulation (search)
   - 🔄 Configuration management (three-level priority)
   - 🔄 Performance metrics recording (Micrometer)
   - **Estimated Time**: 23-31 hours (3-4 days)

### P2 (Optimization)
3. **Advanced Features**: Performance optimization and extended features
   - ❌ Caching mechanism (future iteration)
   - ❌ Batch processing optimization (future iteration)
   - ❌ More data source support (Crossref, arXiv, etc.)

## Next Steps

### Short-term Goals (1-2 weeks) ✅
1. ✅ Complete patra-egress-gateway core feature development
2. ✅ Implement basic external service call capabilities
3. ✅ Integrate basic monitoring and logging

### Current Goals (3-4 days) 🔄
1. 🔄 Complete patra-spring-boot-starter-provenance development
   - Phase 1: Project skeleton and common components (8-11 hours)
   - Phase 2: PubMed data source implementation (6-8 hours)
   - Phase 3: EPMC data source implementation (4-5 hours)
   - Phase 4: Auto-configuration and documentation (5-7 hours)
2. 🔄 Implement PubMed API encapsulation (esearch, efetch)
3. 🔄 Implement EPMC API encapsulation (search)
4. 🔄 Improve configuration management (three-level priority) and performance metrics recording

### Mid-term Goals (1 month)
1. Improve error handling and exception system design
2. Implement API Key management and authentication mechanism
3. Performance optimization (concurrency control, connection pool)
4. Response caching mechanism
5. Automatic retry and degradation strategy
6. Unit tests and integration tests

### Long-term Goals (3 months)
1. Support more literature data sources (Crossref, arXiv, Semantic Scholar)
2. Implement advanced caching and batch processing optimization
3. Improve monitoring and alerting system

## Specification Status

### patra-egress-gateway ✅
- ✅ Requirements document (requirements.md)
- ✅ Design document (design.md)
- ✅ Task list (tasks.md)
- ✅ Implementation completed

### patra-spring-boot-starter-provenance 📋
- ✅ Requirements document (requirements.md) - 12 requirements, EARS format validation standards
- ✅ Design document (design.md) - Complete architecture design, 6 core components
- ✅ Task list (tasks.md) - 6 main tasks, 4 implementation phases
- 🔄 Implementation in progress - Execute according to task list

## Quality Assurance

### Specification Completeness
- **Requirements Coverage**: All functional requirements have corresponding validation standards
- **Design Completeness**: Architecture design, component design, data models, configuration management, auto-configuration
- **Task Executability**: Reasonable task breakdown, clear acceptance criteria, accurate time estimation

### Technical Consistency
- **Unified Tech Stack**: Spring Boot 3.2.4, Spring Cloud 2023.0.1
- **Coding Standards**: Java 21, Lombok, unified exception handling
- **Logging Standards**: SLF4J + unified prefix format
- **Metrics Standards**: Micrometer integration

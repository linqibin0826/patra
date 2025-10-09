# Papertrace Project Overview

## Purpose
Papertrace is a medical literature data platform focused on unified collection, standardization, and intelligent analysis of medical research literature. The platform aims to:
1. Integrate 10+ literature sources (PubMed, EPMC, etc.)
2. Use SSOT (Single Source of Truth - patra-registry) to manage Provenance configurations, dictionaries, and metadata
3. Parse, cleanse, and standardize raw literature data
4. Provide search and intelligent analysis capabilities (future)

## Architecture
- **Microservices**: Distributed service architecture
- **Hexagonal Architecture + DDD**: Strict layer separation with dependency inversion
- **Event-Driven**: Asynchronous communication via Outbox pattern (ready for MQ integration)
- **Current Focus**: Ensure reliable data landing pipeline (Collection → Parsing → Storage)

## Core Modules
- **patra-registry**: SSOT for configurations, dictionaries, and expression capabilities
- **patra-ingest**: Collection and planning engine, responsible for scheduling, window slicing, and task distribution
- **patra-gateway-boot**: Unified API gateway for routing, authentication, and error normalization
- **patra-common**: Cross-service domain base classes, error models, JSON normalization
- **patra-expr-kernel**: Expression AST kernel for deterministic rule evaluation
- **Custom Starters**: patra-spring-boot-starter-core/-web/-mybatis/-provenance, patra-spring-cloud-starter-feign

## Key Design Principles
- **Dependency Direction**: adapter → app → domain ← infra (domain is framework-free)
- **Self-contained Use Cases**: Each use case directory contains complete command/dto/orchestrator
- **Unified Naming**: *Orchestrator (orchestrator), *Command (command), *Impl (implementation)
- **Error Handling**: RFC 7807 ProblemDetail with format `<context-prefix>-<http-suffix>`
- **Observability**: SkyWalking tracing, parameterized logging, Micrometer metrics
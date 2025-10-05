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
- **patra-gateway-boot**: API gateway for routing, authentication, and error alignment
- **patra-common**: Cross-service domain base classes, error models, JSON utilities
- **patra-expr-kernel**: Expression AST engine for deterministic rule evaluation
- **Starters**: Auto-configuration for core/web/feign/mybatis capabilities

## Current Focus

- Stabilize data ingestion pipeline
- Enhance Registry configuration governance
- Strengthen error handling and observability standards

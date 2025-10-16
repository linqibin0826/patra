# Logging System Contracts

**Feature**: 002-redesign-the-project | **Date**: 2025-10-15

## Overview

This directory contains API contracts and interface specifications for the enhanced logging system. Since logging is primarily an infrastructure utility (not a REST API), the contracts here define **Java API interfaces** that developers will use rather than HTTP endpoints.

---

## Contract Types

### 1. **Utility APIs** (Java Interfaces)
Core logging utilities provided by `patra-common` and `patra-spring-boot-starter-logging`:
- `LogSanitizer` - Sensitive data sanitization API
- `TraceContextHolder` - Trace context extraction and propagation API
- `LogContextEnricher` - MDC population utilities

### 2. **Configuration Contracts** (YAML Schema)
Nacos configuration structure for dynamic log level management

### 3. **Integration Contracts** (Spring Boot Auto-Configuration)
Auto-configuration classes and properties for Spring Boot starter integration

---

## Files in This Directory

- `utility-api.md` - Java API specifications for logging utilities
- `spring-boot-properties.md` - Spring Boot configuration properties reference
- `mdc-fields-reference.md` - Standard MDC field names and meanings
- `integrations/trace-context-filter.md` - HTTP boundary filter contract for trace context
- `integrations/feign-interceptor-contract.md` - Feign client propagation and logging contract
- `integrations/sanitization-aspect.md` - Optional AOP-based sanitization contract (AUTO mode)
- `schemas/logging-config.schema.yml` - JSON Schema for Nacos logging config
- `schemas/README.md` - Schema usage, precedence, and validation
- `schemas/examples/` - Validated example YAMLs (common/service/env)

---

## Contract Versioning

These contracts follow semantic versioning:
- **MAJOR**: Breaking changes to public APIs (method signature changes, removed classes)
- **MINOR**: Backward-compatible additions (new utility methods, new MDC fields)
- **PATCH**: Bug fixes, documentation clarifications

**Current Version**: 1.0.1 (docs normalized; integrations + schema added)

---

## Usage

Developers integrating the new logging system should:
1. Read `utility-api.md` to understand available logging utilities
2. Check `mdc-fields-reference.md` for standard trace context fields and remapping
3. Review `spring-boot-properties.md` for configuration options
4. Validate Nacos YAML against `schemas/logging-config.schema.yml` and use examples under `schemas/examples/`

---

## Design Principles

All contracts in this directory adhere to:
- **Simplicity**: APIs are straightforward, minimal method overloads
- **Type Safety**: Strong typing, avoid `Object` parameters
- **Immutability**: Value objects are immutable (Java records)
- **Null Safety**: Use `@NonNull` and `@Nullable` annotations
- **Backward Compatibility**: Changes are additive where possible

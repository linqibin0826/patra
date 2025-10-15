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
- `configuration-schema.yml` - Nacos configuration YAML schema
- `spring-boot-properties.md` - Spring Boot configuration properties reference
- `feign-interceptor-contract.md` - Feign client integration contract
- `mdc-fields-reference.md` - Standard MDC field names and meanings

---

## Contract Versioning

These contracts follow semantic versioning:
- **MAJOR**: Breaking changes to public APIs (method signature changes, removed classes)
- **MINOR**: Backward-compatible additions (new utility methods, new MDC fields)
- **PATCH**: Bug fixes, documentation clarifications

**Current Version**: 1.0.0 (initial release)

---

## Usage

Developers integrating the new logging system should:
1. Read `utility-api.md` to understand available logging utilities
2. Check `mdc-fields-reference.md` for standard trace context fields
3. Review `spring-boot-properties.md` for configuration options
4. Refer to `configuration-schema.yml` for Nacos log level management

---

## Design Principles

All contracts in this directory adhere to:
- **Simplicity**: APIs are straightforward, minimal method overloads
- **Type Safety**: Strong typing, avoid `Object` parameters
- **Immutability**: Value objects are immutable (Java records)
- **Null Safety**: Use `@NonNull` and `@Nullable` annotations
- **Backward Compatibility**: Changes are additive where possible

# Requirements Document

## Introduction

This feature implements a comprehensive error handling system for the Patra microservices platform following hexagonal architecture + DDD + CQRS principles. The system provides unified error handling across REST APIs, Feign clients, and internal services while maintaining clean separation between domain logic and technical concerns.

The error handling system will be implemented as a set of Spring Boot starters that provide zero-configuration error handling with customization points for business-specific needs.

## Requirements

### Requirement 1: Core Error Infrastructure

**User Story:** As a platform developer, I want a foundational error handling infrastructure that provides common error types, codes, and abstractions, so that all microservices can share consistent error handling patterns without framework dependencies.

#### Acceptance Criteria

1. WHEN creating the patra-common module THEN it SHALL provide ErrorCodeLike interface for business error codes
2. WHEN creating domain exceptions THEN they SHALL extend DomainException without any HTTP or framework dependencies
3. WHEN creating application exceptions THEN they SHALL extend ApplicationException and carry ErrorCodeLike codes
4. WHEN defining error traits THEN the system SHALL provide ErrorTrait and HasErrorTraits for semantic classification
5. WHEN defining ProblemDetail constants THEN the system SHALL provide ErrorKeys for standard extension fields (code, traceId, path, timestamp, errors)

### Requirement 2: Spring Boot Core Starter

**User Story:** As a microservice developer, I want a core Spring Boot starter that provides error handling configuration and SPI interfaces, so that I can configure error handling behavior without writing boilerplate code.

#### Acceptance Criteria

1. WHEN configuring the core starter THEN it SHALL require only context-prefix as mandatory configuration
2. WHEN providing SPI interfaces THEN it SHALL include StatusMappingStrategy for HTTP status mapping
3. WHEN providing SPI interfaces THEN it SHALL include TraceProvider for distributed tracing integration
4. WHEN providing SPI interfaces THEN it SHALL include ProblemFieldContributor for custom field injection
5. WHEN providing SPI interfaces THEN it SHALL include ErrorMappingContributor for fine-grained error code overrides
6. WHEN auto-configuring THEN it SHALL use convention-over-configuration with sensible defaults

### Requirement 3: Web Error Handling Starter

**User Story:** As a REST API developer, I want a web starter that automatically converts all exceptions to RFC 7807 ProblemDetail responses, so that my APIs provide consistent error responses without manual exception handling.

#### Acceptance Criteria

1. WHEN an exception occurs in a REST controller THEN it SHALL be converted to application/problem+json response
2. WHEN converting exceptions THEN the system SHALL use the error resolution algorithm (ApplicationException → ErrorMappingContributor → ErrorTraits → naming conventions → fallback)
3. WHEN handling validation errors THEN it SHALL format JSR-380 validation errors into the errors array
4. WHEN building ProblemDetail THEN it SHALL include all standard extension fields (code, traceId, path, timestamp)
5. WHEN determining HTTP status THEN it SHALL use StatusMappingStrategy with suffix-heuristic as default
6. WHEN constructing type field THEN it SHALL use configurable type-base-url + error code

### Requirement 4: Feign Error Handling Starter

**User Story:** As a service integration developer, I want a Feign starter that automatically decodes downstream ProblemDetail responses into typed exceptions, so that I can handle remote service errors consistently without manual response parsing.

#### Acceptance Criteria

1. WHEN receiving ProblemDetail from downstream service THEN it SHALL be decoded into RemoteCallException
2. WHEN decoding errors THEN it SHALL preserve error code, HTTP status, traceId, and detail message
3. WHEN providing error utilities THEN it SHALL include RemoteErrorHelper with methods like isConflict(), isNotFound(), is(code, "...")
4. WHEN handling non-ProblemDetail errors THEN it SHALL gracefully fallback with tolerant mode
5. WHEN propagating traceId THEN it SHALL automatically add traceId to outgoing Feign requests

### Requirement 5: Registry Domain Error Model

**User Story:** As a Registry service developer, I want domain-specific exceptions that express business rules and constraints, so that I can throw meaningful exceptions from domain logic without coupling to HTTP concerns.

#### Acceptance Criteria

1. WHEN creating namespace operations THEN domain SHALL throw NamespaceAlreadyExists and NamespaceNotFound exceptions
2. WHEN creating catalog operations THEN domain SHALL throw CatalogAlreadyExists and CatalogNotFound exceptions  
3. WHEN validating schemas THEN domain SHALL throw SchemaInvalid and SchemaVersionConflict exceptions
4. WHEN handling credentials THEN domain SHALL throw CredentialInvalid and CredentialExpired exceptions
5. WHEN checking quotas THEN domain SHALL throw QuotaExceeded exception
6. WHEN all domain exceptions are created THEN they SHALL inherit from appropriate semantic base classes (RegistryNotFound, RegistryConflict, RegistryRuleViolation, RegistryQuotaExceeded)

### Requirement 6: Registry Error Code Catalog

**User Story:** As an API consumer, I want a well-defined catalog of Registry error codes with consistent naming and documentation, so that I can programmatically handle different error scenarios.

#### Acceptance Criteria

1. WHEN defining error codes THEN they SHALL follow REG-NNNN format with Registry context prefix
2. WHEN providing common codes THEN it SHALL include REG-0400, REG-0401, REG-0403, REG-0404, REG-0409, REG-0422, REG-0429, REG-0500, REG-0503, REG-0504
3. WHEN providing business-specific codes THEN it SHALL include REG-1001 (NAMESPACE_ALREADY_EXISTS), REG-1002 (NAMESPACE_NOT_FOUND), REG-1201 (SCHEMA_INVALID)
4. WHEN documenting codes THEN error catalog SHALL be maintained in patra-registry-api module
5. WHEN extending codes THEN new codes SHALL follow append-only principle

### Requirement 7: Error Resolution Algorithm

**User Story:** As a platform architect, I want a deterministic algorithm that resolves exceptions to error codes and HTTP statuses, so that error handling behavior is predictable and consistent across all services.

#### Acceptance Criteria

1. WHEN resolving ApplicationException THEN it SHALL directly use the embedded ErrorCodeLike code
2. WHEN no ApplicationException match THEN it SHALL check ErrorMappingContributor for explicit overrides
3. WHEN no explicit mapping THEN it SHALL check HasErrorTraits and ErrorTrait annotations for semantic classification
4. WHEN no traits match THEN it SHALL use naming convention heuristics (*NotFound→404, *Conflict→409, *Invalid→422, etc.)
5. WHEN no naming match THEN it SHALL fallback to 422 for client errors or 500 for server errors
6. WHEN determining HTTP status THEN it SHALL use StatusMappingStrategy with configurable mapping rules

### Requirement 8: Configuration and Integration

**User Story:** As a DevOps engineer, I want minimal configuration requirements with sensible defaults, so that services can adopt the error handling system with minimal setup overhead.

#### Acceptance Criteria

1. WHEN configuring a service THEN it SHALL require only patra.error.context-prefix as mandatory setting
2. WHEN using default configuration THEN it SHALL provide working error handling without additional setup
3. WHEN customizing behavior THEN it SHALL support optional configuration for type-base-url, include-stack, tolerant mode
4. WHEN integrating with tracing THEN it SHALL automatically detect traceId from common headers (traceId, X-B3-TraceId, traceparent)
5. WHEN enabling features THEN it SHALL use patra.error.enabled, patra.web.problem.enabled, patra.feign.problem.enabled flags

### Requirement 9: Testing and Validation

**User Story:** As a quality assurance engineer, I want comprehensive test coverage for the error handling system, so that I can verify correct behavior across different scenarios and edge cases.

#### Acceptance Criteria

1. WHEN testing domain exceptions THEN tests SHALL verify each exception triggers correct business rules
2. WHEN testing web error handling THEN tests SHALL verify HTTP status, ProblemDetail structure, and extension fields
3. WHEN testing Feign error decoding THEN tests SHALL verify RemoteCallException creation and helper methods
4. WHEN testing error resolution THEN tests SHALL verify the complete resolution algorithm with various exception types
5. WHEN testing configuration THEN tests SHALL verify auto-configuration behavior with different property combinations

### Requirement 10: Documentation and Migration

**User Story:** As a development team member, I want clear documentation and migration guidance, so that I can successfully adopt the error handling system and migrate existing services.

#### Acceptance Criteria

1. WHEN providing documentation THEN it SHALL include setup instructions for each starter module
2. WHEN documenting error codes THEN it SHALL provide searchable error code catalog with descriptions
3. WHEN providing migration guide THEN it SHALL include step-by-step instructions for existing services
4. WHEN documenting configuration THEN it SHALL include all available properties with examples
5. WHEN providing examples THEN it SHALL include complete working examples for Registry service integration
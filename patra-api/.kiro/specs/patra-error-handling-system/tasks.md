# Implementation Plan

- [x] 1. Create patra-common error foundation
  - Create ErrorCodeLike interface for business error codes
  - Implement DomainException base class without framework dependencies
  - Implement ApplicationException with ErrorCodeLike support
  - Create ErrorTrait enum and HasErrorTraits interface for semantic classification
  - Define ErrorKeys constants for ProblemDetail extension fields
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [x] 2. Implement patra-spring-boot-starter-core infrastructure
  - Create ErrorProperties and TracingProperties configuration classes
  - Implement StatusMappingStrategy interface returning int (not HttpStatus)
  - Create TraceProvider, ProblemFieldContributor, and ErrorMappingContributor SPI interfaces
  - Implement SuffixHeuristicStatusMappingStrategy with int return type
  - Create HeaderBasedTraceProvider for distributed tracing
  - Implement ErrorResolutionService with cause chain traversal and class-level caching
  - Create CoreErrorAutoConfiguration with conditional beans
  - Add META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports for Spring Boot 3
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, Implementation Constraints 5, 6, 7, 8_

- [x] 3. Build patra-spring-boot-starter-web error handling
  - Create WebErrorProperties for web-specific configuration
  - Implement GlobalRestExceptionHandler extending ResponseEntityExceptionHandler with @Order
  - Build ProblemDetailBuilder with sensitive data masking and proxy-aware path extraction
  - Create ValidationErrorsFormatter with sensitive data masking and size limits
  - Handle data layer exceptions (DuplicateKeyException, DataIntegrityViolationException, OptimisticLockingFailureException)
  - Implement int to HttpStatus conversion with 500 fallback
  - Create WebErrorAutoConfiguration with web-specific beans
  - Add META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, Implementation Constraints 9, 12, 13, 15, 18, 19_

- [x] 4. Develop patra-spring-cloud-starter-feign error handling
  - Create FeignErrorProperties for Feign-specific configuration
  - Implement ProblemDetailErrorDecoder with tolerant mode for 404/empty body/non-JSON handling
  - Build RemoteCallException for typed remote errors (adapter-layer only)
  - Create RemoteErrorHelper utility with convenience methods
  - Implement TraceIdRequestInterceptor for trace propagation
  - Create FeignErrorAutoConfiguration with Feign-specific beans
  - Add META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, Implementation Constraints 3, 21_

- [x] 5. Create Registry domain exception model
  - Implement semantic base exceptions (RegistryNotFound, RegistryConflict, RegistryRuleViolation, RegistryQuotaExceeded)
  - Create concrete domain exceptions for namespace operations (NamespaceNotFound, NamespaceAlreadyExists)
  - Implement catalog-related exceptions (CatalogNotFound, CatalogAlreadyExists)
  - Create schema validation exceptions (SchemaInvalid, SchemaVersionConflict)
  - Implement credential and quota exceptions (CredentialInvalid, CredentialExpired, QuotaExceeded)
  - Add ErrorTrait annotations to all domain exceptions
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

- [x] 6. Build Registry error code catalog
  - ✅ Create RegistryErrorCode enum implementing ErrorCodeLike in patra-registry-api module
  - ✅ Define common HTTP-aligned codes (REG-0400 through REG-0504)
  - ✅ Add business-specific codes mapped to actual domain exceptions:
    - Dictionary operations (REG-1401 to REG-1409) mapping to DictionaryNotFoundException, DictionaryItemDisabled, etc.
    - Registry general operations (REG-1501) mapping to RegistryQuotaExceeded
  - ✅ Create comprehensive error code documentation (ERROR_CODE_CATALOG.md) with usage examples
  - ✅ Create package documentation (package-info.java) and module README
  - ✅ Implement append-only error code management strategy with clear policy documentation
  - ✅ Verify compilation and integration with existing Registry domain exceptions
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_
  - **Files Created:**
    - `patra-registry-api/src/main/java/com/patra/registry/api/error/RegistryErrorCode.java`
    - `patra-registry-api/src/main/java/com/patra/registry/api/error/package-info.java`
    - `patra-registry-api/ERROR_CODE_CATALOG.md`
    - `patra-registry-api/README.md`

- [x] 7. Implement error resolution algorithm
  - Create ErrorResolution data class with error code and HTTP status
  - Implement ApplicationException direct code resolution
  - Add ErrorMappingContributor chain processing
  - Implement HasErrorTraits semantic classification
  - Create naming convention heuristic matching
  - Add fallback resolution for unmatched exceptions
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_

- [x] 8. Configure Registry service integration
  - Add starter dependencies to patra-registry-boot module
  - Configure patra.error.context-prefix=REG and other properties in application.yml
  - Create RegistryErrorMappingContributor in boot module for fine-grained mappings
  - Configure Jackson ObjectMapper with consistent settings (WRITE_DATES_AS_TIMESTAMPS=false, FAIL_ON_UNKNOWN_PROPERTIES=false)
  - Remove existing exception handlers in favor of global handling
  - Update REST controllers to rely on automatic error handling
  - Ensure @Transactional rollback behavior works with domain exceptions
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, Implementation Constraints 10, 17_

- [x] 9. Implement comprehensive test suite
  - Create unit tests for all domain exceptions and their traits
  - Build integration tests for web error handling with actual HTTP responses
  - Implement Feign error decoding tests with mock downstream services
  - Create error resolution algorithm tests covering all branches
  - Add configuration and auto-configuration tests
  - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

- [x] 10. Create documentation and migration guide
  - Write setup documentation for each starter module
  - Create searchable error code catalog documentation
  - Build step-by-step migration guide for existing services
  - Document all configuration properties with examples
  - Provide complete working examples for Registry service integration
  - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

- [x] 11. Add monitoring and observability
  - Implement error metrics collection for resolution performance
  - Add error code distribution tracking across services
  - Create Feign error decoding success rate monitoring
  - Implement circuit breakers for error mapping contributors
  - Add structured logging for error resolution process
  - _Requirements: Performance monitoring and operational visibility_

- [x] 12. Validate architectural boundaries and implementation constraints
  - Verify core module has no spring-web dependencies
  - Ensure RemoteCallException stays in adapter layer
  - Confirm ProblemDetail usage limited to HTTP adapter
  - Validate ErrorMappingContributor implementations in boot/adapter modules
  - Test clean layer separation in integration scenarios
  - Verify Spring Boot 3 auto-configuration imports are used
  - Test cause chain traversal and caching performance
  - Validate sensitive data masking in all error responses
  - Test proxy-aware path extraction with various headers
  - Verify UTC timestamp format consistency
  - Test validation error size limits and truncation
  - _Requirements: All implementation constraints 1-21_
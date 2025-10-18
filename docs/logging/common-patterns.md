# Common Logging Patterns

**Feature**: Enhanced Logging System | **Phase**: 6 - User Story 4 | **Date**: 2025-10-17

## Overview

This guide provides reusable logging patterns for common scenarios in the Papertrace platform. Each pattern includes rationale, implementation example, and best practices aligned with FR-015 (Consistent Service Identifiers).

---

## Table of Contents

1. [Request-Response Patterns](#request-response-patterns)
2. [Batch Processing Patterns](#batch-processing-patterns)
3. [External Integration Patterns](#external-integration-patterns)
4. [Error Handling Patterns](#error-handling-patterns)
5. [Performance Monitoring Patterns](#performance-monitoring-patterns)
6. [Security and Audit Patterns](#security-and-audit-patterns)
7. [Testing and Debugging Patterns](#testing-and-debugging-patterns)

---

## Request-Response Patterns

### Pattern 1: HTTP Request Logging

**Use Case**: Log incoming HTTP requests with trace context

**FR-015 Requirement**: Include `[service=X][layer=adapter]` identifier in all adapter logs

```java
@RestController
@RequestMapping("/api/v1/articles")
@Slf4j
public class ArticleController {

    private final LogSanitizer sanitizer;
    private final LogContextEnricher enricher;
    private final ArticleQueryOrchestrator orchestrator;

    @GetMapping("/{id}")
    public ResponseEntity<ArticleDTO> getById(@PathVariable Long id) {
        // Service/layer identifiers automatically added by logging starter
        // Format: [service=patra-ingest][layer=adapter]

        enricher.enrich("operation", "QUERY_ARTICLE");
        enricher.enrich("resourceId", String.valueOf(id));

        try {
            // INFO: Request received (key business event)
            log.info("Article query received: articleId={}", id);

            ArticleDTO result = orchestrator.queryById(id);

            // INFO: Response sent
            log.info("Article query completed: articleId={}, found={}",
                    id, result != null);

            return ResponseEntity.ok(result);

        } catch (ArticleNotFoundException e) {
            log.warn("Article not found: articleId={}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found", e);

        } catch (Exception e) {
            log.error("Article query failed: articleId={}", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal error", e);

        } finally {
            enricher.clearEnriched();
        }
    }
}
```

**Benefits:**
- ✅ Automatic service/layer identification per FR-015
- ✅ Clear request lifecycle (received → completed/failed)
- ✅ MDC enrichment with operation context
- ✅ Proper exception handling
- ✅ MDC cleanup in finally block

**Log Output Example (FR-015 compliant):**
```
2025-10-17T10:23:45.123+08:00 INFO [patra-ingest-boot] [http-nio-8080-exec-1] [traceId=abc123][correlationId=xyz789][service=patra-ingest][layer=adapter] c.p.i.a.ArticleController : Article query received: articleId=12345
```

---

### Pattern 2: Request Validation Logging

**Use Case**: Log validation failures with sanitized input

```java
@PostMapping
public ResponseEntity<ArticleDTO> create(
        @Valid @RequestBody CreateArticleRequest request) {

    enricher.enrich("operation", "CREATE_ARTICLE");

    try {
        // INFO: Request received with key identifiers
        log.info("Creating article: title={}, source={}",
                sanitizer.sanitize(request.getTitle()),
                request.getSource());

        // DEBUG: Full request body (may be sampled)
        log.debug("Create request: {}", sanitizer.sanitizeObject(request));

        ArticleDTO result = orchestrator.create(request);

        log.info("Article created: articleId={}", result.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);

    } catch (ValidationException e) {
        // WARN: Business validation failure
        log.warn("Article validation failed: title={}, errors={}",
                sanitizer.sanitize(request.getTitle()), e.getErrors());
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Validation failed", e);

    } finally {
        enricher.clearEnriched();
    }
}
```

**Benefits:**
- ✅ Sanitized user input before logging
- ✅ WARN level for validation failures
- ✅ Detailed error context
- ✅ DEBUG for full request body (high-frequency protection)

---

### Pattern 3: Pagination Query Logging

**Use Case**: Log paginated queries with result metadata

```java
@GetMapping
public ResponseEntity<Page<ArticleDTO>> search(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String query) {

    enricher.enrich("operation", "SEARCH_ARTICLES");

    try {
        // INFO: Query with pagination params
        log.info("Searching articles: query={}, page={}, size={}",
                sanitizer.sanitize(query), page, size);

        Page<ArticleDTO> result = orchestrator.search(query, page, size);

        // INFO: Result summary
        log.info("Article search completed: query={}, page={}, totalElements={}, " +
                "totalPages={}",
                sanitizer.sanitize(query), page, result.getTotalElements(),
                result.getTotalPages());

        return ResponseEntity.ok(result);

    } finally {
        enricher.clearEnriched();
    }
}
```

**Benefits:**
- ✅ Pagination context included
- ✅ Result metadata logged
- ✅ Sanitized query parameters
- ✅ Concise INFO logging

---

## Batch Processing Patterns

### Pattern 1: Batch Job with Progress Tracking

**Use Case**: Log batch processing with periodic progress updates

```java
@Slf4j
@Component
public class DailyArticleIngestJob {

    private final TraceContextHolder traceContextHolder;
    private final BatchOrchestrator orchestrator;

    @Scheduled(cron = "0 0 2 * * ?")
    public void executeDaily() {
        String batchId = generateBatchId();
        DistributedTraceContext context = traceContextHolder.withCorrelationId(batchId);
        traceContextHolder.populateMDC(context);

        MDC.put("batchId", batchId);
        MDC.put("jobType", "DAILY_INGEST");

        try {
            // INFO: Job start
            log.info("Daily ingest job started: batchId={}", batchId);

            List<Source> sources = getSources();
            int totalRecords = 0;
            int successCount = 0;
            int errorCount = 0;

            for (Source source : sources) {
                try {
                    // INFO: Per-source processing
                    log.info("Processing source: batchId={}, source={}",
                            batchId, source.getName());

                    BatchResult result = orchestrator.ingestFromSource(source, batchId);
                    totalRecords += result.getRecordCount();
                    successCount += result.getSuccessCount();
                    errorCount += result.getErrorCount();

                    // INFO: Per-source summary
                    log.info("Source completed: source={}, records={}, success={}, errors={}",
                            source.getName(), result.getRecordCount(),
                            result.getSuccessCount(), result.getErrorCount());

                } catch (Exception e) {
                    errorCount++;
                    // ERROR: Per-source failure (job continues)
                    log.error("Source failed: batchId={}, source={}",
                            batchId, source.getName(), e);
                }
            }

            // INFO: Job completion summary
            log.info("Daily ingest job completed: batchId={}, sources={}, " +
                    "records={}, success={}, errors={}",
                    batchId, sources.size(), totalRecords, successCount, errorCount);

            // WARN: High error rate
            double errorRate = (double) errorCount / totalRecords;
            if (errorRate > 0.1) {
                log.warn("High error rate detected: batchId={}, errorRate={:.2f}%",
                        batchId, errorRate * 100);
            }

        } catch (Exception e) {
            // ERROR: Job-level failure
            log.error("Daily ingest job failed: batchId={}", batchId, e);
            throw e;

        } finally {
            MDC.remove("batchId");
            MDC.remove("jobType");
            traceContextHolder.clearMDC();
        }
    }

    private String generateBatchId() {
        return "daily-ingest-" + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }
}
```

**Benefits:**
- ✅ Batch-level trace context
- ✅ Per-source progress tracking
- ✅ Summary metrics at INFO level
- ✅ Error rate alerting
- ✅ MDC cleanup

---

### Pattern 2: Batch with Item-Level Logging

**Use Case**: Log individual item processing with sampling protection

```java
@Slf4j
@Service
public class BatchItemProcessor {

    public void processBatch(List<Item> items) {
        String batchId = UUID.randomUUID().toString();
        MDC.put("batchId", batchId);

        try {
            log.info("Batch processing started: batchId={}, itemCount={}",
                    batchId, items.size());

            int processed = 0;
            int successCount = 0;
            int errorCount = 0;

            for (Item item : items) {
                try {
                    // DEBUG: Per-item processing (may be sampled)
                    log.debug("Processing item: itemId={}", item.getId());

                    processItem(item);
                    successCount++;
                    processed++;

                    // INFO: Progress checkpoint every 1000 items
                    if (processed % 1000 == 0) {
                        log.info("Batch progress: processed={}/{}, success={}, errors={}",
                                processed, items.size(), successCount, errorCount);
                    }

                } catch (Exception e) {
                    errorCount++;
                    processed++;

                    // ERROR: Per-item failure
                    log.error("Item processing failed: itemId={}", item.getId(), e);
                }
            }

            // INFO: Batch completion
            log.info("Batch completed: batchId={}, total={}, success={}, errors={}",
                    batchId, items.size(), successCount, errorCount);

        } finally {
            MDC.remove("batchId");
        }
    }
}
```

**Benefits:**
- ✅ DEBUG for item-level logs (sampling protection)
- ✅ INFO for periodic checkpoints
- ✅ ERROR for failures (never sampled)
- ✅ Summary metrics

---

## External Integration Patterns

### Pattern 1: REST API Call (FR-006)

**Use Case**: Log external API calls with URL, status, duration per FR-006

**FR-015 Requirement**: Include `[service=X][layer=infra]` identifier

```java
@Slf4j
@Component
public class PubMedApiClient {

    private final RestTemplate restTemplate;
    private final LogSanitizer sanitizer;

    public PubMedSearchResponse search(String query) {
        String url = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi";
        long startTime = System.currentTimeMillis();

        // Service/layer identifiers automatically added: [service=patra-ingest][layer=infra]

        // INFO: API call initiated (FR-006: URL)
        log.info("Calling PubMed API: url={}, query={}",
                url, sanitizer.sanitize(query));

        // DEBUG: Request details
        log.debug("Request params: term={}, retmax={}",
                sanitizer.sanitize(query), 100);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    url + "?term=" + query + "&retmax=100", String.class);

            long duration = System.currentTimeMillis() - startTime;
            HttpStatus status = response.getStatusCode();

            // INFO: API call success (FR-006: URL, status, duration)
            log.info("PubMed API call succeeded: url={}, status={}, duration={}ms",
                    url, status.value(), duration);

            // DEBUG: Response body (sanitized)
            log.debug("Response: status={}, bodyLength={}",
                    status.value(), response.getBody().length());

            return parsePubMedResponse(response.getBody());

        } catch (HttpClientErrorException e) {
            long duration = System.currentTimeMillis() - startTime;

            // WARN: Client error (4xx)
            log.warn("PubMed API client error: url={}, status={}, duration={}ms",
                    url, e.getStatusCode().value(), duration);
            throw new PubMedApiException("Client error", e);

        } catch (HttpServerErrorException e) {
            long duration = System.currentTimeMillis() - startTime;

            // ERROR: Server error (5xx) - FR-006 compliant
            log.error("PubMed API server error: url={}, status={}, duration={}ms",
                    url, e.getStatusCode().value(), duration, e);
            throw new PubMedApiException("Server error", e);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            // ERROR: Network/timeout error
            log.error("PubMed API call failed: url={}, duration={}ms, error={}",
                    url, duration, e.getMessage(), e);
            throw new PubMedApiException("Network error", e);
        }
    }
}
```

**Benefits (FR-006 & FR-015 compliant):**
- ✅ INFO logs include URL, status code, duration (FR-006)
- ✅ Service/layer identifier: `[service=patra-ingest][layer=infra]` (FR-015)
- ✅ DEBUG for request/response details (may be sampled)
- ✅ WARN for client errors (4xx)
- ✅ ERROR for server errors (5xx), network failures
- ✅ Duration measurement

---

### Pattern 2: Retry with Exponential Backoff

**Use Case**: Log retry attempts with backoff strategy

```java
@Slf4j
@Component
public class ResilientApiClient {

    @Retryable(
        value = {HttpServerErrorException.class, ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public ApiResponse callWithRetry(ApiRequest request) {
        String url = "https://api.example.com/endpoint";

        try {
            // INFO: Attempt logged
            int attemptNumber = RetrySynchronizationManager.getContext().getRetryCount() + 1;
            log.info("API call attempt {}: url={}", attemptNumber, url);

            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                    url, request, ApiResponse.class);

            return response.getBody();

        } catch (Exception e) {
            int attemptNumber = RetrySynchronizationManager.getContext().getRetryCount() + 1;
            int maxAttempts = 3;

            if (attemptNumber < maxAttempts) {
                // WARN: Retry will occur
                log.warn("API call failed, will retry: attempt={}/{}, url={}, error={}",
                        attemptNumber, maxAttempts, url, e.getMessage());
            } else {
                // ERROR: Final attempt failed
                log.error("API call failed after {} attempts: url={}",
                        maxAttempts, url, e);
            }

            throw e;
        }
    }

    @Recover
    public ApiResponse recover(Exception e, ApiRequest request) {
        // ERROR: All retries exhausted
        log.error("API call exhausted all retries: request={}",
                sanitizer.sanitizeObject(request), e);
        throw new ApiCallFailedException("All retries failed", e);
    }
}
```

**Benefits:**
- ✅ Log attempt number
- ✅ WARN for retries
- ✅ ERROR for final failure
- ✅ Recovery handler logging

---

## Error Handling Patterns

### Pattern 1: Exception with Business Context

**Use Case**: Log exceptions with full business context per FR-005

```java
@Slf4j
@Service
@Transactional
public class CreateArticleOrchestrator {

    public CreateArticleResult execute(CreateArticleCommand command) {
        // Service/layer identifier: [service=patra-ingest][layer=app]

        log.info("Creating article: title={}, source={}",
                command.getTitle(), command.getSource());

        try {
            // Validate uniqueness
            if (articleRepository.existsByDoi(command.getDoi())) {
                log.warn("Duplicate article detected: doi={}", command.getDoi());
                throw new ArticleDuplicateException(command.getDoi());
            }

            // Create domain aggregate
            Article article = Article.create(
                    command.getTitle(),
                    command.getDoi(),
                    command.getAuthors()
            );

            // Persist
            articleRepository.save(article);

            log.info("Article created successfully: articleId={}, doi={}",
                    article.getId(), article.getDoi());

            return CreateArticleResult.from(article);

        } catch (ArticleDuplicateException e) {
            // WARN: Business exception (expected)
            log.warn("Article creation failed - duplicate: doi={}, title={}",
                    command.getDoi(), command.getTitle());
            throw e;

        } catch (ValidationException e) {
            // WARN: Business validation failure
            log.warn("Article validation failed: title={}, errors={}",
                    command.getTitle(), e.getErrors());
            throw e;

        } catch (Exception e) {
            // ERROR: Unexpected system error (FR-005: full context)
            log.error("Article creation failed unexpectedly: title={}, doi={}, " +
                    "source={}, traceId={}",
                    command.getTitle(), command.getDoi(), command.getSource(),
                    MDC.get("traceId"), e);
            throw new OrchestrationException("Article creation failed", e);
        }
    }
}
```

**Benefits (FR-005 & FR-015 compliant):**
- ✅ ERROR logs include full business context (FR-005)
- ✅ Service/layer identifier: `[service=patra-ingest][layer=app]` (FR-015)
- ✅ WARN for business exceptions
- ✅ ERROR for system exceptions
- ✅ Trace ID included in error logs

---

### Pattern 2: Graceful Degradation

**Use Case**: Log fallback behavior when primary path fails

```java
@Slf4j
@Service
public class ArticleEnrichmentService {

    private final PubMedApiClient pubMedClient;
    private final EpmcApiClient epmcClient;

    public ArticleMetadata enrichArticle(String doi) {
        try {
            // INFO: Primary enrichment attempt
            log.info("Enriching article from PubMed: doi={}", doi);

            ArticleMetadata metadata = pubMedClient.fetchMetadata(doi);

            log.info("Article enriched from PubMed: doi={}", doi);
            return metadata;

        } catch (Exception e) {
            // WARN: Primary source failed, trying fallback
            log.warn("PubMed enrichment failed, trying EPMC fallback: doi={}, error={}",
                    doi, e.getMessage());

            try {
                ArticleMetadata metadata = epmcClient.fetchMetadata(doi);

                log.info("Article enriched from EPMC (fallback): doi={}", doi);
                return metadata;

            } catch (Exception fallbackError) {
                // ERROR: Both sources failed
                log.error("Article enrichment failed from all sources: doi={}",
                        doi, fallbackError);
                throw new EnrichmentFailedException("All enrichment sources failed",
                        fallbackError);
            }
        }
    }
}
```

**Benefits:**
- ✅ WARN for primary failure + fallback attempt
- ✅ INFO for successful fallback
- ✅ ERROR only when all paths fail
- ✅ Clear degradation path

---

## Performance Monitoring Patterns

### Pattern 1: Operation Duration Tracking

**Use Case**: Track and log slow operations

```java
@Slf4j
@Service
public class PerformanceMonitoringService {

    private static final long SLOW_THRESHOLD_MS = 1000;

    public Result performOperation(Request request) {
        long startTime = System.currentTimeMillis();
        String operationId = request.getOperationId();

        // DEBUG: Operation start
        log.debug("Operation started: operationId={}", operationId);

        try {
            Result result = executeOperation(request);

            long duration = System.currentTimeMillis() - startTime;

            if (duration > SLOW_THRESHOLD_MS) {
                // WARN: Slow operation detected
                log.warn("Slow operation detected: operationId={}, duration={}ms, " +
                        "threshold={}ms", operationId, duration, SLOW_THRESHOLD_MS);
            } else {
                // DEBUG: Normal performance
                log.debug("Operation completed: operationId={}, duration={}ms",
                        operationId, duration);
            }

            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            // ERROR: Operation failed
            log.error("Operation failed: operationId={}, duration={}ms",
                    operationId, duration, e);
            throw e;
        }
    }
}
```

**Benefits:**
- ✅ WARN for slow operations
- ✅ DEBUG for normal operations (may be sampled)
- ✅ Duration included in all logs
- ✅ Threshold-based alerting

---

### Pattern 2: Resource Usage Logging

**Use Case**: Log resource consumption for capacity planning

```java
@Slf4j
@Component
public class ResourceMonitor {

    @Scheduled(fixedRate = 60000)  // Every minute
    public void logResourceUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double memoryUsagePercent = (double) usedMemory / totalMemory * 100;

        // INFO: Resource metrics (periodic)
        log.info("Resource usage: memoryUsed={}MB, memoryTotal={}MB, " +
                "memoryUsage={:.2f}%, activeThreads={}",
                usedMemory / 1024 / 1024, totalMemory / 1024 / 1024,
                memoryUsagePercent, Thread.activeCount());

        // WARN: High memory usage
        if (memoryUsagePercent > 80) {
            log.warn("High memory usage detected: memoryUsage={:.2f}%, threshold=80%",
                    memoryUsagePercent);
        }
    }
}
```

**Benefits:**
- ✅ Periodic resource logging
- ✅ WARN for high usage
- ✅ Capacity planning data

---

## Security and Audit Patterns

### Pattern 1: Authentication Events (FR-009)

**Use Case**: Log authentication success/failure per FR-009

```java
@Slf4j
@Component
public class AuthenticationEventLogger implements ApplicationListener<AbstractAuthenticationEvent> {

    private final LogSanitizer sanitizer;

    @Override
    public void onApplicationEvent(AbstractAuthenticationEvent event) {
        if (event instanceof AuthenticationSuccessEvent) {
            handleSuccess((AuthenticationSuccessEvent) event);
        } else if (event instanceof AuthenticationFailureEvent) {
            handleFailure((AuthenticationFailureEvent) event);
        }
    }

    private void handleSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        String remoteAddress = getRemoteAddress(event);

        // INFO: Authentication success (FR-009)
        log.info("Authentication successful: username={}, remoteAddress={}, " +
                "authorities={}",
                sanitizer.sanitize(username), remoteAddress,
                event.getAuthentication().getAuthorities());
    }

    private void handleFailure(AuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        String remoteAddress = getRemoteAddress(event);
        String reason = event.getException().getMessage();

        // WARN: Authentication failure (FR-009, security alert)
        log.warn("Authentication failed: username={}, remoteAddress={}, reason={}",
                sanitizer.sanitize(username), remoteAddress, reason);
    }
}
```

**Benefits (FR-009 compliant):**
- ✅ INFO for successful authentication
- ✅ WARN for failed authentication
- ✅ Sanitized username
- ✅ Remote address included
- ✅ Security audit trail

---

### Pattern 2: Sensitive Data Sanitization (FR-008)

**Use Case**: Sanitize sensitive data before logging per FR-008

```java
@Slf4j
@RestController
public class UserController {

    private final LogSanitizer sanitizer;

    @PostMapping("/users")
    public ResponseEntity<User> createUser(@RequestBody CreateUserRequest request) {
        // ALWAYS sanitize user input (FR-008)
        log.info("Creating user: email={}, name={}",
                sanitizer.sanitize(request.getEmail()),
                sanitizer.sanitize(request.getName()));

        // DEBUG: Sanitized full request
        log.debug("User creation request: {}", sanitizer.sanitizeObject(request));

        User user = userService.createUser(request);

        // NEVER log passwords, even in DEBUG
        log.info("User created: userId={}, email={}",
                user.getId(), sanitizer.sanitize(user.getEmail()));

        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
}
```

**Benefits (FR-008 compliant):**
- ✅ All user input sanitized
- ✅ Passwords never logged
- ✅ PII protection
- ✅ Compliance with security requirements

---

## Testing and Debugging Patterns

### Pattern 1: Integration Test Logging

**Use Case**: Verify trace context propagation in tests

```java
@SpringBootTest
@Slf4j
class TraceContextPropagationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldPropagateTraceContextAcrossLayers() throws Exception {
        // Generate test trace context
        String testTraceId = "test-trace-" + UUID.randomUUID();
        String testCorrelationId = "test-correlation-" + UUID.randomUUID();

        // INFO: Test start
        log.info("Starting trace propagation test: traceId={}, correlationId={}",
                testTraceId, testCorrelationId);

        mockMvc.perform(get("/api/v1/articles/{id}", 123)
                .header("X-Trace-Id", testTraceId)
                .header("X-Correlation-Id", testCorrelationId))
                .andExpect(status().isOk());

        // Verify trace context in logs
        assertThat(MDC.get("traceId")).isEqualTo(testTraceId);
        assertThat(MDC.get("correlationId")).isEqualTo(testCorrelationId);

        // INFO: Test passed
        log.info("Trace propagation test passed: traceId={}", testTraceId);
    }
}
```

**Benefits:**
- ✅ Test trace context generation
- ✅ Verification of propagation
- ✅ Clear test logging

---

### Pattern 2: Debug Mode Logging

**Use Case**: Enable verbose logging for specific scenarios

```java
@Slf4j
@Service
public class DebugModeService {

    @Value("${papertrace.debug.enabled:false}")
    private boolean debugEnabled;

    public void processWithDebugMode(Request request) {
        if (debugEnabled || log.isDebugEnabled()) {
            // DEBUG: Verbose logging when debug mode enabled
            log.debug("Processing request in debug mode: requestId={}, " +
                    "timestamp={}, headers={}",
                    request.getId(), request.getTimestamp(), request.getHeaders());
        }

        // INFO: Normal logging
        log.info("Processing request: requestId={}", request.getId());

        // ... processing logic
    }
}
```

**Benefits:**
- ✅ Conditional verbose logging
- ✅ Minimal overhead in production
- ✅ Debugging aid

---

## Summary

### Pattern Categories

| Category | Patterns | Key Requirements |
|----------|----------|------------------|
| Request-Response | HTTP logging, validation, pagination | FR-015 (service/layer IDs) |
| Batch Processing | Job lifecycle, progress tracking | Correlation IDs, summary metrics |
| External Integration | REST calls, retry logic | FR-006 (URL, status, duration), FR-015 |
| Error Handling | Business exceptions, graceful degradation | FR-005 (full context), FR-015 |
| Performance | Duration tracking, resource monitoring | WARN for slow operations |
| Security | Authentication, sanitization | FR-008 (sanitization), FR-009 (audit) |
| Testing | Integration tests, debug mode | Trace context verification |

### FR-015 Compliance: Service Identifiers

**Canonical Format**: `[service=<service-name>][layer=<layer-name>]`

**Valid Service Names**:
- `patra-registry`
- `patra-ingest`
- `patra-gateway`
- Format: lowercase, alphanumeric with hyphens, starts with letter

**Valid Layer Names**:
- `adapter` - Controllers, Jobs, Message Listeners
- `app` - Orchestrators, Use Case coordinators
- `domain` - Pure Java business logic (rare logging)
- `infra` - Repositories, External API clients

**Configuration**:
```yaml
spring:
  application:
    name: patra-registry  # Becomes [service=patra-registry]

papertrace:
  logging:
    layer: adapter  # Becomes [layer=adapter] (auto-detected if not specified)
```

**Example Log Output**:
```
2025-10-17T10:23:45.123+08:00 INFO [patra-registry-boot] [http-nio-8080-exec-1] [traceId=abc123][correlationId=xyz789][service=patra-registry][layer=adapter] c.p.r.a.ProvenanceController : Fetching provenance: source=PubMed
```

### Best Practices

1. **Service Identifiers (FR-015)**: Always include `[service=X][layer=Y]` via logging starter configuration
2. **Sanitization (FR-008)**: Use `LogSanitizer` for all user input and PII
3. **External API Logging (FR-006)**: Include URL, status code, duration
4. **Exception Context (FR-005)**: Log full business context with exceptions
5. **Log Levels**: INFO for business events, DEBUG for details, WARN for recoverable errors, ERROR for failures
6. **MDC Management**: Enrich in adapter layer, clean up in finally blocks
7. **Trace Context**: Generate for batch/scheduled jobs, extract from upstream requests
8. **Sampling Protection**: Use DEBUG/TRACE for high-frequency logs

---

**Related Documentation:**
- [Layer-Specific Examples](layer-specific-examples.md) - Detailed examples by layer
- [Log Level Guidelines](log-level-guidelines.md) - Semantic log level usage
- [Quickstart Guide](../../specs/001-logging-starter/quickstart.md) - Migration guide

**Next Steps:**
- Review patterns relevant to your use case
- Adapt patterns to your service context
- Ensure FR-015 compliance with service/layer identifiers
- Test locally and verify log format

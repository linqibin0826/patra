# Enhanced Logging System - Developer Quickstart Guide

**Version**: 1.0.0 | **Last Updated**: 2025-10-15

## Overview

This guide helps developers adopt the enhanced logging system across Papertrace microservices. It covers migration steps, best practices, and common patterns for structured logging with trace context propagation.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Migration Checklist](#migration-checklist)
3. [Layer-Specific Guidelines](#layer-specific-guidelines)
4. [Common Patterns](#common-patterns)
5. [Troubleshooting](#troubleshooting)
6. [FAQ](#faq)

---

## Quick Start

### For Existing Services

**Step 1**: Add dependency to your module's `pom.xml`:

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-logging</artifactId>
</dependency>
```

**Step 2**: Remove old logback configuration (if any) and let starter auto-configure:

```xml
<!-- Delete or comment out in src/main/resources/ -->
<!-- logback.xml or logback-spring.xml -->
```

**Step 3**: Add logging properties to `application.yml`:

```yaml
spring:
  application:
    name: patra-your-service  # Required for MDC population

papertrace:
  logging:
    trace:
      enabled: true
    sanitization:
      enabled: true
      mode: MANUAL  # Require explicit sanitization
```

**Step 4**: Update your code to use new logging utilities (see examples below).

**Step 5**: Test locally and verify trace context in logs:

```bash
mvn spring-boot:run
# Check logs include [traceId] and [correlationId]
```

---

## Migration Checklist

### Pre-Migration

- [ ] Read this guide completely
- [ ] Review module's README.md for domain-specific context
- [ ] Identify classes with sensitive data logging
- [ ] List external API calls that need structured logging

### Module Setup

- [ ] Add `patra-spring-boot-starter-logging` dependency
- [ ] Configure Spring Boot properties (`application.yml`)
- [ ] Remove legacy logback configuration files
- [ ] Verify compilation: `mvn clean compile`

### Code Updates by Layer

#### Adapter Layer
- [ ] Replace logger declarations with `@Slf4j`
- [ ] Add sanitization for user input logging
- [ ] Enrich MDC with business context (userId, operation)
- [ ] Ensure MDC cleanup in finally blocks

#### Application Layer
- [ ] Replace logger declarations with `@Slf4j`
- [ ] Populate trace context for batch operations
- [ ] Add correlation IDs for async operations
- [ ] Log orchestration start/end with context

#### Infrastructure Layer
- [ ] Replace logger declarations with `@Slf4j`
- [ ] Add structured API call logging (URL, method, status, duration)
- [ ] Sanitize API request/response bodies
- [ ] Log database operation failures with context

#### Domain Layer
- [ ] Keep plain `Logger` declaration (NO Lombok)
- [ ] Minimal logging (validation failures, state transitions)
- [ ] NO MDC manipulation (pure Java requirement)

### Testing

- [ ] Unit tests pass: `mvn test`
- [ ] Integration tests verify trace context propagation
- [ ] Manual testing confirms logs include trace IDs
- [ ] Performance testing shows <5% throughput impact

### Documentation

- [ ] Update module README with logging examples
- [ ] Document any service-specific sanitization patterns
- [ ] Add troubleshooting tips for common issues

---

## Layer-Specific Guidelines

### Adapter Layer (Controllers, Jobs, Listeners)

**Controllers**:

```java
@RestController
@RequestMapping("/api/v1/ingest")
@Slf4j
public class IngestController {

    @Autowired
    private LogSanitizer sanitizer;

    @Autowired
    private LogContextEnricher enricher;

    @Autowired
    private IngestOrchestrator orchestrator;

    @PostMapping("/pubmed")
    public ResponseEntity<IngestResult> ingestPubMed(
            @Valid @RequestBody IngestRequest request) {

        // TraceContextFilter already populated traceId, correlationId

        // Enrich MDC with business context
        enricher.enrich("operation", "INGEST_PUBMED");
        enricher.enrich("source", "PubMed");

        try {
            log.info("Starting PubMed ingestion: query={}", sanitizer.sanitize(request.getQuery()));

            IngestResult result = orchestrator.ingest(request);

            log.info("PubMed ingestion completed: recordsIngested={}", result.getCount());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("PubMed ingestion failed", e);
            throw e;
        } finally {
            enricher.clearEnriched();
        }
    }
}
```

**Scheduled Jobs**:

```java
@Component
@Slf4j
public class BatchProcessingJob {

    @Autowired
    private TraceContextHolder traceContextHolder;

    @Autowired
    private BatchOrchestrator orchestrator;

    @Scheduled(cron = "0 0 2 * * ?")  // 2 AM daily
    public void processDailyBatch() {
        String batchId = "batch-" + LocalDate.now().format(DateTimeFormatter.ISO_DATE);

        // Generate trace context for batch
        TraceContext context = traceContextHolder.withCorrelationId(batchId);
        traceContextHolder.populateMDC(context);

        MDC.put("batchId", batchId);

        try {
            log.info("Daily batch processing started: batchId={}", batchId);

            orchestrator.processBatch(batchId);

            log.info("Daily batch processing completed: batchId={}", batchId);

        } catch (Exception e) {
            log.error("Daily batch processing failed: batchId={}", batchId, e);
            throw e;
        } finally {
            MDC.remove("batchId");
            traceContextHolder.clearMDC();
        }
    }
}
```

**Message Listeners**:

```java
@Component
@Slf4j
@RocketMQMessageListener(topic = "task-events", consumerGroup = "task-processor")
public class TaskEventListener implements RocketMQListener<TaskEvent> {

    @Autowired
    private TraceContextHolder traceContextHolder;

    @Override
    public void onMessage(TaskEvent event) {
        // Extract trace context from message headers
        String traceId = event.getTraceId();
        String correlationId = event.getCorrelationId();

        TraceContext context = TraceContext.withCorrelation(traceId, correlationId);
        traceContextHolder.populateMDC(context);

        MDC.put("taskId", event.getTaskId());

        try {
            log.info("Processing task event: taskId={}, type={}", event.getTaskId(), event.getType());

            // ... processing logic

            log.info("Task event processed successfully: taskId={}", event.getTaskId());

        } catch (Exception e) {
            log.error("Task event processing failed: taskId={}", event.getTaskId(), e);
            throw e;
        } finally {
            MDC.remove("taskId");
            traceContextHolder.clearMDC();
        }
    }
}
```

---

### Application Layer (Orchestrators)

**Orchestrator Pattern**:

```java
@Service
@Slf4j
@Transactional
public class CreatePlanOrchestrator {

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private ProvenanceRepository provenanceRepository;

    public CreatePlanResult execute(CreatePlanCommand command) {
        // TraceContext already populated by adapter layer

        log.info("Creating plan: source={}, window={}", command.getSource(), command.getWindow());

        // Domain logic
        Provenance provenance = provenanceRepository.findBySource(command.getSource())
                .orElseThrow(() -> new ProvenanceNotFoundException(command.getSource()));

        PlanAggregate plan = PlanAggregate.create(
                command.getSource(),
                command.getWindow(),
                command.getStrategy(),
                provenance
        );

        // Persist
        planRepository.save(plan);

        log.info("Plan created successfully: planId={}, planKey={}", plan.getId(), plan.getPlanKey());

        return CreatePlanResult.from(plan);
    }
}
```

**Batch Orchestrator**:

```java
@Service
@Slf4j
public class BatchProcessingOrchestrator {

    @Autowired
    private ItemRepository itemRepository;

    public void processBatch(String batchId) {
        // Batch ID already in MDC from adapter layer

        List<Item> items = itemRepository.findPendingItems();

        log.info("Batch processing started: itemCount={}", items.size());

        int successCount = 0;
        int errorCount = 0;

        for (Item item : items) {
            try {
                processItem(item);
                successCount++;

                // Log progress every 1000 items
                if ((successCount + errorCount) % 1000 == 0) {
                    log.debug("Batch progress: processed={}, success={}, errors={}",
                             successCount + errorCount, successCount, errorCount);
                }

            } catch (Exception e) {
                errorCount++;
                log.error("Item processing failed: itemId={}, error={}",
                         item.getId(), e.getMessage(), e);
            }
        }

        log.info("Batch processing completed: success={}, errors={}, total={}",
                 successCount, errorCount, items.size());
    }

    private void processItem(Item item) {
        log.debug("Processing item: itemId={}", item.getId());
        // ... business logic
    }
}
```

---

### Infrastructure Layer (Repositories, Clients)

**Repository Implementation**:

```java
@Repository
@Slf4j
public class PlanRepositoryMpImpl implements PlanRepository {

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private PlanDOMapper planDOMapper;

    @Override
    public Optional<PlanAggregate> findById(Long id) {
        log.debug("Finding plan by ID: planId={}", id);

        PlanDO planDO = planMapper.selectById(id);
        if (planDO == null) {
            log.debug("Plan not found: planId={}", id);
            return Optional.empty();
        }

        PlanAggregate plan = planDOMapper.toDomain(planDO);
        log.debug("Plan found: planId={}, planKey={}", id, plan.getPlanKey());
        return Optional.of(plan);
    }

    @Override
    public void save(PlanAggregate plan) {
        log.debug("Saving plan: planId={}, planKey={}", plan.getId(), plan.getPlanKey());

        try {
            PlanDO planDO = planDOMapper.toDO(plan);
            if (plan.getId() == null) {
                planMapper.insert(planDO);
                plan.setId(planDO.getId());  // Update with generated ID
            } else {
                planMapper.updateById(planDO);
            }

            log.debug("Plan saved successfully: planId={}", plan.getId());

        } catch (Exception e) {
            log.error("Failed to save plan: planKey={}, error={}",
                     plan.getPlanKey(), e.getMessage(), e);
            throw new RepositoryException("Failed to save plan", e);
        }
    }
}
```

**External API Client**:

```java
@Component
@Slf4j
public class PubMedApiClient {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private LogSanitizer sanitizer;

    public PubMedResponse fetchArticles(String query) {
        String url = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi";

        log.debug("Calling PubMed API: query={}", sanitizer.sanitize(query));

        long startTime = System.currentTimeMillis();
        HttpStatus status = null;
        String errorMessage = null;

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    url + "?term=" + query,
                    String.class
            );

            status = response.getStatusCode();
            long duration = System.currentTimeMillis() - startTime;

            log.info("PubMed API call succeeded: status={}, duration={}ms", status.value(), duration);
            log.debug("Response body: {}", sanitizer.sanitize(response.getBody()));

            return parsePubMedResponse(response.getBody());

        } catch (HttpClientErrorException e) {
            status = e.getStatusCode();
            errorMessage = e.getMessage();
            long duration = System.currentTimeMillis() - startTime;

            log.warn("PubMed API returned client error: status={}, duration={}ms, error={}",
                    status.value(), duration, errorMessage);
            throw new PubMedApiException("PubMed API client error", e);

        } catch (HttpServerErrorException e) {
            status = e.getStatusCode();
            errorMessage = e.getMessage();
            long duration = System.currentTimeMillis() - startTime;

            log.error("PubMed API failed: status={}, duration={}ms, error={}",
                     status.value(), duration, errorMessage, e);
            throw new PubMedApiException("PubMed API server error", e);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            log.error("PubMed API call failed unexpectedly: duration={}ms", duration, e);
            throw new PubMedApiException("Unexpected error calling PubMed API", e);
        }
    }
}
```

---

### Domain Layer (Pure Java)

**Domain Aggregate**:

```java
// NO @Slf4j annotation in domain layer
public class PlanAggregate {

    private static final Logger log = LoggerFactory.getLogger(PlanAggregate.class);

    private Long id;
    private String planKey;
    private String source;
    private TimeWindow window;
    private FetchStrategy strategy;

    public static PlanAggregate create(String source, TimeWindow window,
                                       FetchStrategy strategy, Provenance provenance) {

        // Validate inputs
        if (source == null || source.isBlank()) {
            log.warn("Plan creation failed: source is null or empty");
            throw new ValidationException("Source is required");
        }

        // Business logic
        String planKey = generatePlanKey(source, window, strategy);

        log.debug("Creating plan aggregate: planKey={}", planKey);

        PlanAggregate plan = new PlanAggregate();
        plan.planKey = planKey;
        plan.source = source;
        plan.window = window;
        plan.strategy = strategy;

        log.debug("Plan aggregate created: planKey={}", planKey);
        return plan;
    }

    public void validate() {
        if (planKey == null) {
            log.warn("Plan validation failed: planKey is null");
            throw new ValidationException("Plan key is required");
        }

        if (window == null || !window.isValid()) {
            log.warn("Plan validation failed: invalid time window");
            throw new ValidationException("Valid time window is required");
        }

        log.debug("Plan validation passed: planKey={}", planKey);
    }

    // ... other domain methods

    private static String generatePlanKey(String source, TimeWindow window, FetchStrategy strategy) {
        // Generate hash-based key
        String key = source + window.toString() + strategy.name();
        return DigestUtil.sha256Hex(key);
    }
}
```

---

## Common Patterns

### Pattern 1: Sanitizing User Input

```java
@Slf4j
@RestController
public class UserController {

    @Autowired
    private LogSanitizer sanitizer;

    @PostMapping("/users")
    public ResponseEntity<User> createUser(@RequestBody @Valid CreateUserRequest request) {
        // ALWAYS sanitize user input before logging
        log.info("Creating user: email={}, name={}",
                 sanitizer.sanitize(request.getEmail()),
                 sanitizer.sanitize(request.getName()));

        // ... business logic
    }
}
```

### Pattern 2: Batch Processing with Progress Logging

```java
@Slf4j
@Service
public class BatchProcessor {

    public void processBatch(List<Item> items) {
        String batchId = UUID.randomUUID().toString();
        MDC.put("batchId", batchId);

        try {
            log.info("Batch started: batchId={}, itemCount={}", batchId, items.size());

            int processed = 0;
            int errors = 0;

            for (Item item : items) {
                try {
                    processItem(item);
                    processed++;

                    // Log progress every 1000 items
                    if ((processed + errors) % 1000 == 0) {
                        log.debug("Batch progress: processed={}/{}", processed + errors, items.size());
                    }

                } catch (Exception e) {
                    errors++;
                    log.error("Item failed: itemId={}", item.getId(), e);
                }
            }

            log.info("Batch completed: batchId={}, success={}, errors={}",
                     batchId, processed, errors);

        } finally {
            MDC.remove("batchId");
        }
    }
}
```

### Pattern 3: Async Task with Trace Context

```java
@Service
@Slf4j
public class AsyncService {

    @Autowired
    private LogContextEnricher enricher;

    @Async("taskExecutor")  // Must use executor with MdcTaskDecorator
    public CompletableFuture<Result> processAsync(Request request) {
        // MDC automatically propagated by MdcTaskDecorator

        log.info("Async processing started: requestId={}", request.getId());

        try {
            Result result = performWork(request);
            log.info("Async processing completed: requestId={}", request.getId());
            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Async processing failed: requestId={}", request.getId(), e);
            throw e;
        }
    }
}
```

### Pattern 4: External API Call with Structured Logging

```java
@Component
@Slf4j
public class ApiClient {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private LogSanitizer sanitizer;

    public ApiResponse callExternalApi(ApiRequest request) {
        String url = "https://api.example.com/endpoint";
        long startTime = System.currentTimeMillis();

        log.debug("Calling external API: url={}, request={}",
                 url, sanitizer.sanitizeJson(toJson(request)));

        try {
            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                    url, request, ApiResponse.class
            );

            long duration = System.currentTimeMillis() - startTime;

            log.info("External API call succeeded: url={}, status={}, duration={}ms",
                    url, response.getStatusCodeValue(), duration);

            log.debug("Response: {}", sanitizer.sanitizeJson(toJson(response.getBody())));

            return response.getBody();

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            log.error("External API call failed: url={}, duration={}ms", url, duration, e);
            throw new ApiClientException("API call failed", e);
        }
    }
}
```

---

## Troubleshooting

### Issue: Trace ID Not Appearing in Logs

**Symptoms**: Logs missing `[traceId]` field

**Causes**:
1. `TraceContextFilter` not registered (check starter dependency)
2. SkyWalking agent not attached
3. Request not going through servlet filter (e.g., scheduled job)

**Solutions**:
```java
// For scheduled jobs, manually generate trace context
@Scheduled(cron = "0 0 * * * ?")
public void scheduledTask() {
    TraceContext context = traceContextHolder.currentOrGenerate();
    traceContextHolder.populateMDC(context);
    try {
        // ... task logic
    } finally {
        traceContextHolder.clearMDC();
    }
}
```

### Issue: MDC Fields Lost in Async Tasks

**Symptoms**: Async tasks missing trace ID/correlation ID

**Cause**: Thread pool not configured with `MdcTaskDecorator`

**Solution**:
```java
@Configuration
public class AsyncConfiguration {
    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(new MdcTaskDecorator());  // ← Add this
        executor.initialize();
        return executor;
    }
}
```

### Issue: Sensitive Data in Logs

**Symptoms**: Passwords/tokens visible in production logs

**Cause**: Forgot to call `sanitizer.sanitize()` before logging

**Solution**:
```java
// WRONG
log.info("User created: {}", user);  // May contain password!

// RIGHT
log.info("User created: {}", sanitizer.sanitizeObject(user));
```

### Issue: High Log Volume in Production

**Symptoms**: Log storage filling up, performance degradation

**Cause**: DEBUG/TRACE logs enabled in production

**Solution**:
```yaml
# Update Nacos logging-common.yml
logging:
  level:
    root: INFO
    com.papertrace: INFO  # Change from DEBUG to INFO
```

---

## FAQ

**Q: Should I use `@Slf4j` in domain layer?**
A: No. Domain layer should use plain `Logger` declaration to keep it pure Java (no framework dependencies).

**Q: When should I sanitize logs?**
A: Always sanitize user input, external API responses, and any data that might contain PII or credentials.

**Q: How do I add custom MDC fields?**
A: Use `LogContextEnricher.enrich()` and remember to clean up in `finally` block.

**Q: Can I change log levels without restarting?**
A: Yes, update Nacos configuration and changes apply within 60 seconds.

**Q: Should I log every method entry/exit?**
A: No. Log at DEBUG level for complex orchestrators, but avoid excessive logging in simple methods.

**Q: How do I test trace context propagation?**
A: Write integration test that makes HTTP request and verifies all logs contain same `traceId`.

**Q: What if trace ID generation conflicts with existing tracing?**
A: SkyWalking handles trace ID generation automatically. Our system just extracts and logs it.

---

## Next Steps

1. **Migrate your module** using the checklist above
2. **Test locally** and verify trace context in logs
3. **Review logs** for sensitive data exposure
4. **Update module README** with logging examples
5. **Submit PR** for review

---

## Getting Help

- **Documentation**: See `specs/002-redesign-the-project/` for detailed specifications
- **Examples**: Check migrated services (e.g., `patra-registry`) for patterns
- **Questions**: Ask in #dev-logging Slack channel

---

**Happy Logging!** 🎉

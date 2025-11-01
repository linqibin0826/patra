# Common Patterns from Papertrace Codebase

## Overview

This document captures common code patterns extracted from the actual Papertrace codebase. These are battle-tested patterns that follow Hexagonal Architecture + DDD principles while solving real-world problems.

---

## Pattern 1: Record-Based Aggregates (Immutability)

### Problem

Domain aggregates need to be immutable to prevent accidental mutations and ensure thread safety.

### Solution

Use Java `record` for immutable aggregates.

### Implementation

**patra-registry-domain:**
```java
public record ProvenanceConfiguration(
    Provenance provenance,
    WindowOffsetConfig windowOffset,
    PaginationConfig pagination,
    HttpConfig http,
    BatchingConfig batching,
    RetryConfig retry,
    RateLimitConfig rateLimit
) {
    // Business logic methods (no setters!)
    public boolean isComplete() {
        return provenance != null &&
               provenance.isActive() &&
               windowOffset != null &&
               pagination != null &&
               http != null;
    }

    public boolean supportsOperation(String operationType) {
        // Validation logic
        return true; // simplified
    }

    // Factory method for defaults
    public static ProvenanceConfiguration withDefaults(Provenance provenance) {
        return new ProvenanceConfiguration(
            provenance,
            WindowOffsetConfig.DEFAULT,
            PaginationConfig.DEFAULT,
            HttpConfig.DEFAULT,
            BatchingConfig.DEFAULT,
            RetryConfig.DEFAULT,
            RateLimitConfig.DEFAULT
        );
    }
}
```

**Benefits**:
- ✅ Immutable by default
- ✅ Equals/hashCode generated automatically
- ✅ Concise syntax (no boilerplate)
- ✅ Thread-safe

**When to Use**:
- Value objects
- Immutable aggregates
- DTOs (API layer)
- Query results (CQRS read models)

---

## Pattern 2: Port Interface with Default Methods

### Problem

Repository ports need common query methods, but each implementation requires custom logic.

### Solution

Use `default` methods in port interfaces for shared logic.

### Implementation

**patra-registry-domain:**
```java
public interface ProvenanceConfigPort {
    // Abstract methods (must be implemented)
    Optional<ProvenanceConfiguration> findByProvenanceAndOperation(
        ProvenanceCode provenanceCode,
        String operationType,
        Instant effectiveAt
    );

    List<ProvenanceConfiguration> findAll();

    void save(ProvenanceConfiguration config);

    // Default method with shared logic
    default Optional<ProvenanceConfiguration> loadActiveConfig(
        ProvenanceCode provenanceCode,
        String operationType
    ) {
        return findByProvenanceAndOperation(
            provenanceCode,
            operationType,
            Instant.now()  // Use current time
        ).filter(config -> config.isComplete());
    }

    // Another default helper
    default boolean existsForProvenance(ProvenanceCode provenanceCode) {
        return findAll().stream()
            .anyMatch(config -> config.provenance().provenanceCode().equals(provenanceCode));
    }
}
```

**Benefits**:
- ✅ Shared logic in domain layer (no duplication)
- ✅ Implementation flexibility (can override if needed)
- ✅ Domain-driven (logic stays in domain)

---

## Pattern 3: MapStruct DO ↔ Domain Converter

### Problem

Need to convert between Database Objects (DO) and Domain entities without manual mapping code.

### Solution

Use MapStruct for compile-time code generation.

### Implementation

**patra-registry-infra:**

**DO Entity**:
```java
@Data
@TableName("reg_provenance_config")
public class RegProvenanceConfigDO {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String provenanceCode;
    private String operationType;
    private String windowOffsetJson;  // JSON serialized
    private String paginationJson;
    private String httpJson;
    private String batchingJson;
    private String retryJson;
    private String rateLimitJson;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**MapStruct Converter**:
```java
@Mapper(
    componentModel = "spring",
    uses = {JacksonMapper.class}  // For JSON conversion
)
public interface ProvenanceConfigConverter {

    @Mapping(target = "provenance", source = "provenanceCode", qualifiedByName = "codeToProvenance")
    @Mapping(target = "windowOffset", source = "windowOffsetJson", qualifiedByName = "jsonToWindowOffset")
    @Mapping(target = "pagination", source = "paginationJson", qualifiedByName = "jsonToPagination")
    ProvenanceConfiguration toDomain(RegProvenanceConfigDO dataObject);

    @Mapping(target = "provenanceCode", source = "provenance.provenanceCode.value")
    @Mapping(target = "windowOffsetJson", source = "windowOffset", qualifiedByName = "windowOffsetToJson")
    @Mapping(target = "paginationJson", source = "pagination", qualifiedByName = "paginationToJson")
    RegProvenanceConfigDO toDO(ProvenanceConfiguration domain);

    // Custom mappings
    @Named("jsonToWindowOffset")
    default WindowOffsetConfig jsonToWindowOffset(String json) {
        return JacksonMapper.fromJson(json, WindowOffsetConfig.class);
    }

    @Named("windowOffsetToJson")
    default String windowOffsetToJson(WindowOffsetConfig config) {
        return JacksonMapper.toJson(config);
    }

    // ... more custom mappings
}
```

**Repository Implementation**:
```java
@Repository
@RequiredArgsConstructor
public class ProvenanceConfigRepositoryMpImpl implements ProvenanceConfigPort {
    private final RegProvenanceConfigMapper mapper;  // MyBatis-Plus
    private final ProvenanceConfigConverter converter;  // MapStruct

    @Override
    public Optional<ProvenanceConfiguration> findByProvenanceAndOperation(
        ProvenanceCode provenanceCode,
        String operationType,
        Instant effectiveAt
    ) {
        QueryWrapper<RegProvenanceConfigDO> query = new QueryWrapper<>();
        query.eq("provenance_code", provenanceCode.value())
             .eq("operation_type", operationType)
             .le("effective_from", effectiveAt)
             .and(wrapper -> wrapper.isNull("effective_to")
                                    .or()
                                    .gt("effective_to", effectiveAt));

        return Optional.ofNullable(mapper.selectOne(query))
                       .map(converter::toDomain);
    }

    @Override
    public void save(ProvenanceConfiguration config) {
        RegProvenanceConfigDO dataObject = converter.toDO(config);
        mapper.insert(dataObject);
    }
}
```

**Benefits**:
- ✅ No manual mapping code
- ✅ Compile-time validation
- ✅ JSON serialization for complex objects
- ✅ Type-safe conversions

---

## Pattern 4: Query Assembler (CQRS Read Model)

### Problem

Read models (queries) often need data from multiple aggregates.

### Solution

Use Query Assembler in Application layer to compose read models.

### Implementation

**patra-registry-app:**

**Query Model** (domain read model):
```java
// In domain layer
public record ProvenanceConfigQuery(
    ProvenanceCode provenanceCode,
    String name,
    WindowOffsetConfig windowOffset,
    PaginationConfig pagination,
    HttpConfig http,
    boolean isComplete
) {
    public static ProvenanceConfigQuery from(ProvenanceConfiguration config) {
        return new ProvenanceConfigQuery(
            config.provenance().provenanceCode(),
            config.provenance().name(),
            config.windowOffset(),
            config.pagination(),
            config.http(),
            config.isComplete()
        );
    }
}
```

**Query Assembler**:
```java
// In application layer
@Service
@RequiredArgsConstructor
public class ProvenanceConfigQueryAssembler {
    private final ProvenanceConfigPort configPort;
    private final ExpressionPort expressionPort;

    public Optional<EnrichedProvenanceConfigQuery> assembleEnrichedConfig(
        ProvenanceCode provenanceCode,
        String operationType
    ) {
        // 1. Load base configuration
        Optional<ProvenanceConfiguration> config = configPort.loadActiveConfig(
            provenanceCode,
            operationType
        );

        if (config.isEmpty()) {
            return Optional.empty();
        }

        // 2. Load related expressions
        List<Expression> expressions = expressionPort.findByProvenance(provenanceCode);

        // 3. Assemble enriched query model
        return Optional.of(new EnrichedProvenanceConfigQuery(
            ProvenanceConfigQuery.from(config.get()),
            expressions.stream()
                      .map(ExpressionSummary::from)
                      .toList(),
            config.get().supportedCapabilities()
        ));
    }
}
```

**Benefits**:
- ✅ Separation of read and write models (CQRS)
- ✅ Optimized for query performance
- ✅ Encapsulates complex joins

---

## Pattern 5: Scope Precedence Resolver

### Problem

Configuration can exist at TASK, SOURCE, GLOBAL levels. Need to resolve which one applies.

### Solution

Implement precedence resolver with fallback chain.

### Implementation

**patra-registry-app:**
```java
@Service
@RequiredArgsConstructor
public class ConfigurationResolver {
    private final TaskConfigPort taskConfigPort;
    private final SourceConfigPort sourceConfigPort;
    private final GlobalConfigPort globalConfigPort;

    public ProvenanceConfiguration resolve(
        ProvenanceCode provenanceCode,
        String operationType,
        Optional<TaskId> taskId
    ) {
        Instant now = Instant.now();

        // 1. Try TASK level (highest priority)
        if (taskId.isPresent()) {
            Optional<ProvenanceConfiguration> taskConfig =
                taskConfigPort.findByTask(taskId.get(), now);

            if (taskConfig.isPresent()) {
                log.debug("Using TASK-level config for task {}", taskId.get());
                return taskConfig.get();
            }
        }

        // 2. Try SOURCE level (medium priority)
        Optional<ProvenanceConfiguration> sourceConfig =
            sourceConfigPort.findByProvenanceAndOperation(
                provenanceCode,
                operationType,
                now
            );

        if (sourceConfig.isPresent()) {
            log.debug("Using SOURCE-level config for provenance {}", provenanceCode);
            return sourceConfig.get();
        }

        // 3. Fall back to GLOBAL level
        ProvenanceConfiguration globalConfig =
            globalConfigPort.findByOperation(operationType, now)
                .orElseThrow(() -> new ConfigurationNotFoundException(
                    "No global config for operation: " + operationType
                ));

        log.debug("Using GLOBAL-level config (fallback)");
        return globalConfig;
    }
}
```

**Usage in Orchestrator**:
```java
@Service
@RequiredArgsConstructor
public class TaskExecutionOrchestrator {
    private final ConfigurationResolver configResolver;

    public void executeTask(BatchTask task) {
        // Resolve configuration with scope precedence
        ProvenanceConfiguration config = configResolver.resolve(
            task.provenanceCode(),
            "harvest",
            Optional.of(task.id())  // Task-specific config if exists
        );

        int retryLimit = config.retry().maxRetries();
        // ... use config
    }
}
```

**Benefits**:
- ✅ Clear precedence rules
- ✅ Centralized resolution logic
- ✅ Easy to add new scopes

---

## Pattern 6: Business Key Generation

### Problem

Need idempotency for API calls (prevent duplicates).

### Solution

Generate deterministic business key from request parameters.

### Implementation

**patra-ingest-domain:**
```java
public class BusinessKeyGenerator {

    public static String generate(
        ProvenanceCode provenanceCode,
        Map<String, Object> apiParams
    ) {
        // 1. Sort keys for deterministic ordering
        List<String> sortedKeys = new ArrayList<>(apiParams.keySet());
        Collections.sort(sortedKeys);

        // 2. Build canonical string
        StringBuilder canonical = new StringBuilder();
        canonical.append(provenanceCode.value());

        for (String key : sortedKeys) {
            Object value = apiParams.get(key);
            // Normalize value (handle different types)
            String normalizedValue = normalizeValue(value);
            canonical.append("_").append(key).append("=").append(normalizedValue);
        }

        // 3. Hash to fixed-length key
        return DigestUtils.md5Hex(canonical.toString());
    }

    private static String normalizeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.stream()
                      .map(Object::toString)
                      .sorted()  // Sort list elements too!
                      .collect(Collectors.joining(","));
        }
        return value.toString();
    }
}
```

**Usage**:
```java
Map<String, Object> params = Map.of(
    "mindate", "2024-01-01",
    "maxdate", "2024-12-31",
    "retstart", 0,
    "retmax", 1000
);

String businessKey = BusinessKeyGenerator.generate(
    ProvenanceCode.PUBMED,
    params
);

// businessKey = "a3f2b1c9d8e7f6..."

BatchTask task = new BatchTask(
    TaskId.generate(),
    planId,
    sliceId,
    ProvenanceCode.PUBMED,
    businessKey,  // <-- Idempotency key
    params,
    TaskStatus.PENDING,
    ...
);
```

**Database Constraint**:
```sql
CREATE TABLE batch_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    business_key VARCHAR(64) NOT NULL,
    ...
    UNIQUE KEY uk_business_key (business_key)  -- Prevent duplicates
);
```

**Benefits**:
- ✅ Idempotent operations
- ✅ Deterministic (same params = same key)
- ✅ Database-enforced uniqueness

---

## Pattern 7: Retry with Exponential Backoff

### Problem

Transient failures (rate limiting, timeouts) should be retried with increasing delays.

### Solution

Implement exponential backoff with jitter.

### Implementation

**patra-ingest-app:**
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class RetryOrchestrator {
    private final TaskPort taskPort;
    private final ConfigurationResolver configResolver;
    private final ScheduledExecutorService scheduler;

    public void scheduleRetry(BatchTask task) {
        RetryConfig retryConfig = configResolver.resolve(
            task.provenanceCode(),
            "harvest",
            Optional.of(task.id())
        ).retry();

        if (task.retryCount() >= retryConfig.maxRetries()) {
            // Exhaust retries
            task = task.withStatus(TaskStatus.EXHAUSTED);
            taskPort.save(task);
            log.error("Task {} exhausted after {} retries", task.id(), task.retryCount());
            return;
        }

        // Calculate delay with exponential backoff
        Duration baseDelay = retryConfig.calculateDelay(task.retryCount());

        // Add jitter (-10% to +10%)
        long jitterMs = (long) (baseDelay.toMillis() * (Math.random() * 0.2 - 0.1));
        Duration delayWithJitter = baseDelay.plusMillis(jitterMs);

        // Schedule retry
        scheduler.schedule(
            () -> retryTask(task),
            delayWithJitter.toMillis(),
            TimeUnit.MILLISECONDS
        );

        log.info("Task {} scheduled for retry in {} (attempt {}/{})",
            task.id(), delayWithJitter, task.retryCount() + 1, retryConfig.maxRetries());
    }

    private void retryTask(BatchTask task) {
        // Update task for retry
        BatchTask retryTask = task.withStatus(TaskStatus.PENDING)
                                  .withRetryCount(task.retryCount() + 1);
        taskPort.save(retryTask);

        // Task will be picked up by executor
        log.info("Task {} re-queued for execution (retry {})",
            task.id(), retryTask.retryCount());
    }
}
```

**Benefits**:
- ✅ Handles transient failures
- ✅ Jitter prevents thundering herd
- ✅ Configurable backoff strategy

---

## Pattern 8: Orchestrator → Coordinator Delegation

### Problem

Orchestrators can become bloated with too many responsibilities.

### Solution

Delegate concerns to specialized Coordinators.

### Implementation

**patra-ingest-app:**

**Main Orchestrator**:
```java
@Service
@Transactional
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {
    // Coordinators for different concerns
    private final PlanPersistenceCoordinator persistenceCoordinator;
    private final PlanIdempotencyCoordinator idempotencyCoordinator;
    private final PlanSlicingCoordinator slicingCoordinator;
    private final PlanPublishingCoordinator publishingCoordinator;

    @Override
    public PlanIngestionResult ingest(PlanIngestionCommand command) {
        // Phase 1: Idempotency check
        if (idempotencyCoordinator.isDuplicate(command.requestId())) {
            return idempotencyCoordinator.loadExistingResult(command.requestId());
        }

        // Phase 2: Generate slices
        List<Slice> slices = slicingCoordinator.generateSlices(
            command.startTime(),
            command.endTime(),
            command.provenanceCode()
        );

        // Phase 3: Create Plan entity
        BatchPlan plan = BatchPlan.create(
            command.provenanceCode(),
            command.startTime(),
            command.endTime(),
            slices,
            command.createdBy()
        );

        // Phase 4: Persist
        persistenceCoordinator.save(plan);

        // Phase 5: Publish events
        publishingCoordinator.publishPlanCreated(plan);

        // Phase 6: Record for idempotency
        idempotencyCoordinator.recordResult(command.requestId(), plan.id());

        return new PlanIngestionResult(plan.id(), slices.size());
    }
}
```

**Specialized Coordinator**:
```java
@Component
@RequiredArgsConstructor
public class PlanSlicingCoordinator {
    private final ConfigurationResolver configResolver;
    private final RecordEstimator recordEstimator;

    public List<Slice> generateSlices(
        LocalDateTime startTime,
        LocalDateTime endTime,
        ProvenanceCode provenanceCode
    ) {
        // Load window configuration
        WindowOffsetConfig windowConfig = configResolver.resolve(
            provenanceCode,
            "harvest",
            Optional.empty()
        ).windowOffset();

        // Generate time-based slices
        List<Slice> slices = new ArrayList<>();
        LocalDateTime current = startTime;
        int sliceNumber = 1;

        while (current.isBefore(endTime)) {
            LocalDateTime sliceEnd = current.plus(windowConfig.sliceDuration());
            if (sliceEnd.isAfter(endTime)) {
                sliceEnd = endTime;
            }

            // Estimate record count
            int estimated = recordEstimator.estimate(
                provenanceCode,
                current,
                sliceEnd
            );

            slices.add(new Slice(
                SliceId.generate(),
                null,  // planId set later
                current,
                sliceEnd,
                estimated,
                SliceStatus.PENDING,
                sliceNumber++
            ));

            current = sliceEnd;
        }

        return slices;
    }
}
```

**Benefits**:
- ✅ Single Responsibility Principle
- ✅ Testable coordinators
- ✅ Clear separation of concerns

---

## Pattern 9: Domain Event Publishing

### Problem

Need to trigger side effects (e.g., Task generation) when Plan is created.

### Solution

Publish domain events from aggregates, handle in Application layer.

### Implementation

**Domain Event**:
```java
// In domain layer
public record PlanCreatedEvent(
    PlanId planId,
    ProvenanceCode provenanceCode,
    LocalDateTime startTime,
    LocalDateTime endTime,
    int sliceCount,
    Instant occurredAt
) implements DomainEvent {
    public PlanCreatedEvent(
        PlanId planId,
        ProvenanceCode provenanceCode,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int sliceCount
    ) {
        this(planId, provenanceCode, startTime, endTime, sliceCount, Instant.now());
    }
}
```

**Event Publisher (Infrastructure)**:
```java
@Component
public class SpringEventPublisher implements DomainEventPublisher {
    private final ApplicationEventPublisher publisher;

    public SpringEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(DomainEvent event) {
        publisher.publishEvent(event);
    }
}
```

**Event Handler (Application)**:
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanCreatedEventHandler {
    private final TaskGenerationOrchestrator taskGenerator;

    @EventListener
    @Async  // Process asynchronously
    public void onPlanCreated(PlanCreatedEvent event) {
        log.info("Plan {} created, generating tasks...", event.planId());

        try {
            taskGenerator.generateTasksForPlan(event.planId());
            log.info("Tasks generated successfully for plan {}", event.planId());
        } catch (Exception e) {
            log.error("Failed to generate tasks for plan {}", event.planId(), e);
            // Handle error (e.g., retry, alert)
        }
    }
}
```

**Benefits**:
- ✅ Decoupled components
- ✅ Asynchronous processing
- ✅ Event-driven architecture

---

## Pattern 10: Temporal Validity Check

### Problem

Configurations expire and need time-based validity checks.

### Solution

Use `effectiveFrom`/`effectiveTo` pattern with helper methods.

### Implementation

**Base Interface**:
```java
public interface TemporallyValid {
    Instant effectiveFrom();
    Instant effectiveTo();  // null = forever

    default boolean isEffectiveAt(Instant instant) {
        return !instant.isBefore(effectiveFrom()) &&
               (effectiveTo() == null || instant.isBefore(effectiveTo()));
    }

    default boolean isCurrentlyEffective() {
        return isEffectiveAt(Instant.now());
    }

    default boolean willBeEffectiveAt(Instant instant) {
        return instant.isAfter(effectiveFrom()) || instant.equals(effectiveFrom());
    }

    default boolean hasExpired() {
        return effectiveTo() != null && Instant.now().isAfter(effectiveTo());
    }
}
```

**Usage in Domain Model**:
```java
public record WindowOffsetConfig(
    Duration sliceDuration,
    Duration lookbackPeriod,
    Duration maxWindowSize,
    Instant effectiveFrom,
    Instant effectiveTo
) implements TemporallyValid {

    public boolean isEffectiveFor(Plan plan) {
        return isEffectiveAt(plan.createdAt());
    }
}
```

**Query with Temporal Filter**:
```java
public Optional<WindowOffsetConfig> loadActiveConfig(
    ProvenanceCode provenanceCode,
    Instant at
) {
    return repository.findAllConfigs(provenanceCode).stream()
        .filter(config -> config.isEffectiveAt(at))
        .max(Comparator.comparing(WindowOffsetConfig::effectiveFrom));  // Latest
}
```

**Benefits**:
- ✅ Clean temporal logic
- ✅ Version configs safely
- ✅ No downtime during transitions

---

## Summary

**Key Patterns**:

1. **Record-Based Aggregates**: Immutability with Java records
2. **Port with Default Methods**: Shared logic in domain
3. **MapStruct Converters**: DO ↔ Domain mapping
4. **Query Assemblers**: CQRS read models
5. **Scope Precedence**: TASK > SOURCE > GLOBAL
6. **Business Key**: Idempotency through deterministic hashing
7. **Retry with Backoff**: Exponential backoff + jitter
8. **Orchestrator/Coordinator**: Separation of concerns
9. **Domain Events**: Event-driven workflows
10. **Temporal Validity**: Time-based configuration versioning

**Design Principles**:
- Immutability where possible
- Clear layer boundaries (Hexagonal Architecture)
- Type-safe conversions
- Idempotent operations
- Event-driven decoupling

**See Also**:
- [business-concepts.md](business-concepts.md) for domain model definitions
- [plan-task-workflow.md](plan-task-workflow.md) for workflow implementation

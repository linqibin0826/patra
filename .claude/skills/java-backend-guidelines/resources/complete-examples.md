# Complete Examples - Full Working Code

Real-world examples showing complete implementation patterns across all architectural layers from Papertrace patra-ingest module.

## Table of Contents

- [Complete Feature Example: Plan Ingestion](#complete-feature-example-plan-ingestion)
- [Architecture Layers Walkthrough](#architecture-layers-walkthrough)
- [Refactoring Example: Bad to Good](#refactoring-example-bad-to-good)
- [End-to-End Request Flow](#end-to-end-request-flow)
- [Testing Strategy](#testing-strategy)

---

## Complete Feature Example: Plan Ingestion

### Overview

The Plan Ingestion feature demonstrates a complete Hexagonal Architecture + DDD implementation spanning all four layers: **Adapter → Application → Domain ← Infrastructure**.

**Business Context**: When a scheduler triggers a data ingestion task, the system creates a Plan with multiple Slices and Tasks based on temporal windows and configuration snapshots. This ensures idempotent, reliable, and auditable data collection.

**Key Components**:
- **Orchestrator**: PlanIngestionOrchestrator coordinates the entire workflow
- **Coordinators**: Separate concerns for persistence, idempotency, and publishing
- **Domain Aggregates**: PlanAggregate encapsulates business rules and state
- **Repository**: MyBatis-Plus implementation with MapStruct converters

---

## Architecture Layers Walkthrough

### 1. Domain Layer (Pure Java)

**Purpose**: Business logic and rules. NO framework dependencies.

#### Aggregate Root: PlanAggregate

**File**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/aggregate/PlanAggregate.java`

```java
/**
 * Aggregate root representing an ingestion plan blueprint with state transitions.
 *
 * Idempotency: planKey (source + operation + window + strategy hash) prevents duplicates
 * State machine: DRAFT → SLICING → READY/PARTIAL → COMPLETED/FAILED
 *
 * Thread safety: single-threaded usage only, not shared across threads
 */
@Getter
public class PlanAggregate extends AggregateRoot<Long> {

  /** Scheduler instance identifier */
  private final Long scheduleInstanceId;

  /** Business idempotency key for deduplication */
  private final String planKey;

  /** Provenance/source code (e.g., PUBMED) */
  private final String provenanceCode;

  /** Operation type (full, incremental, compensation) */
  private final OperationCode operationCode;

  /** Hash of plan expression prototype */
  private final String exprProtoHash;

  /** Snapshot of raw expression prototype (JSON) */
  private final String exprProtoSnapshotJson;

  /** Snapshot of provenance configuration */
  private final String provenanceConfigSnapshotJson;

  /** Window boundary specification */
  private final WindowSpec windowSpec;

  /** Slicing strategy code (TIME, DATE, SINGLE) */
  private final String sliceStrategyCode;

  /** Current state of the plan */
  private PlanStatus status;

  // Private constructor ensures use of factory methods
  private PlanAggregate(
      Long id,
      Long scheduleInstanceId,
      String planKey,
      String provenanceCode,
      OperationCode operationCode,
      String exprProtoHash,
      String exprProtoSnapshotJson,
      String provenanceConfigSnapshotJson,
      String provenanceConfigHash,
      WindowSpec windowSpec,
      String sliceStrategyCode,
      String sliceParamsJson,
      PlanStatus status) {
    super(id);
    this.scheduleInstanceId = Objects.requireNonNull(scheduleInstanceId);
    this.planKey = Objects.requireNonNull(planKey);
    this.provenanceCode = provenanceCode;
    this.operationCode = operationCode;
    this.exprProtoHash = exprProtoHash;
    this.exprProtoSnapshotJson = exprProtoSnapshotJson;
    this.provenanceConfigSnapshotJson = provenanceConfigSnapshotJson;
    this.windowSpec = Objects.requireNonNull(windowSpec);
    this.sliceStrategyCode = sliceStrategyCode;
    this.status = status == null ? PlanStatus.DRAFT : status;
  }

  // Factory method for creating new plans
  public static PlanAggregate create(/* parameters */) {
    // Business rule validation happens here
    return new PlanAggregate(/* ... */);
  }

  // Business logic methods
  public void markAsCompleted() {
    // Business rule: validate state transition
    if (this.status == PlanStatus.CANCELLED) {
      throw new IllegalStateException("Cannot complete cancelled plan");
    }
    this.status = PlanStatus.COMPLETED;
    // Emit domain event for cross-aggregate reactions
  }
}
```

**Key Takeaways**:
- ✅ Pure Java (extends AggregateRoot from patra-common)
- ✅ Immutable fields with business meaning
- ✅ Factory methods for creation
- ✅ Business rules in domain methods
- ❌ NO Spring annotations (@Service, @Autowired)
- ❌ NO framework dependencies

---

#### Value Object: BatchPlan

**File**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/vo/batch/BatchPlan.java`

```java
/**
 * Value object representing a batch planning outcome.
 *
 * Invariants:
 * - batches must not be null (but may be empty)
 * - totalBatches must be >= 0
 */
public record BatchPlan(
    List<Batch> batches,
    int totalBatches,
    boolean exceedsLimit) {

  // Compact constructor for validation
  public BatchPlan {
    if (batches == null) {
      throw new IllegalArgumentException("batches must not be null");
    }
    if (totalBatches < 0) {
      throw new IllegalArgumentException("totalBatches must not be negative");
    }
  }

  /** Create an empty batch plan */
  public static BatchPlan empty() {
    return new BatchPlan(List.of(), 0, false);
  }

  /** Create a plan containing a single batch */
  public static BatchPlan single(Batch batch) {
    return new BatchPlan(List.of(batch), 1, false);
  }

  /** Returns true when the plan contains at least one batch */
  public boolean hasBatches() {
    return !batches.isEmpty();
  }
}
```

**Key Takeaways**:
- ✅ Use `record` for immutable value objects
- ✅ Compact constructor validates invariants
- ✅ Factory methods for common cases
- ✅ Equality by value (automatic with record)
- ✅ Self-validating

---

#### Port Interface: PlanRepository

**File**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/PlanRepository.java`

```java
/**
 * Port interface for Plan persistence (defined in domain layer).
 *
 * Infrastructure layer implements this interface.
 */
public interface PlanRepository {

  /**
   * Saves a Plan: insert or update based on presence of ID
   * @param plan aggregate
   * @return persisted aggregate with ID populated
   */
  PlanAggregate save(PlanAggregate plan);

  /**
   * Finds a plan by business key (idempotent query)
   * @param planKey business idempotency key
   * @return plan if found
   */
  Optional<PlanAggregate> findByPlanKey(String planKey);

  /**
   * Checks whether a planKey exists
   * @param planKey business key
   * @return true if exists
   */
  boolean existsByPlanKey(String planKey);
}
```

**Key Takeaways**:
- ✅ Port defined in domain layer
- ✅ Domain types in signatures (PlanAggregate)
- ✅ Business-oriented method names
- ❌ NO infrastructure types (DOs, DTOs)

---

### 2. Application Layer (Orchestration)

**Purpose**: Coordinate use cases, manage transactions, delegate to domain and infrastructure.

#### Orchestrator: PlanIngestionOrchestrator

**File**: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/plan/PlanIngestionOrchestrator.java`

```java
/**
 * Main orchestrator for plan ingestion flow.
 *
 * Coordinates six phases:
 * 1. Persist schedule instance and load provenance config snapshot
 * 2. Query cursor watermark and resolve execution window
 * 3. Build plan expression and run pre-validations
 * 4. Assemble plan/slices/tasks (with idempotency)
 * 5. Check for existing plan (idempotent reuse)
 * 6. Persist and publish task enqueued events (Outbox pattern)
 *
 * This orchestrator maintains the @Transactional boundary to ensure atomicity
 * across persistence and event publishing (Outbox pattern).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {

  // Domain ports
  private final PatraRegistryPort patraRegistryPort;
  private final CursorRepository cursorRepository;
  private final TaskRepository taskRepository;
  private final PlanRepository planRepository;

  // Application services
  private final PlanningWindowResolver planningWindowResolver;
  private final PlannerValidator plannerValidator;
  private final PlanAssembler planAssembler;
  private final PlanExpressionBuilder planExpressionBuilder;

  // Coordinators for delegating specific responsibilities
  private final PlanPersistenceCoordinator persistenceCoordinator;
  private final PlanIdempotencyCoordinator idempotencyCoordinator;
  private final PlanPublishingCoordinator publishingCoordinator;

  /**
   * Main plan orchestration flow (entry method)
   */
  @Override
  @Transactional  // Transaction boundary at orchestrator level
  public PlanIngestionResult ingestPlan(PlanIngestionCommand request) {
    logPlanIngestionStart(request);

    // Phase 1: Prepare planning context
    PlanningContext context = preparePlanningContext(request);

    // Phase 2: Build plan expression
    PlanExpressionDescriptor expressionDescriptor = buildPlanExpression(context);

    // Phase 3: Pre-validation
    performPreValidation(context);

    // Phase 4: Assemble plan
    PlanAssemblyResult assembly = assembleAndValidatePlan(context, expressionDescriptor);

    // Phase 5: Check for existing plan (idempotency)
    PlanAggregate existingPlan = checkForExistingPlan(assembly.plan());
    if (existingPlan != null) {
      return idempotencyCoordinator.handleIdempotentPlanReuse(
          existingPlan, context.schedule(), assembly.plan().getPlanKey());
    }

    // Phase 6: Persist and publish
    return persistAndPublishNewPlan(
        assembly.plan(), assembly, context.schedule(), context.window());
  }

  /**
   * Prepares planning context by loading configuration and resolving window
   */
  private PlanningContext preparePlanningContext(PlanIngestionCommand request) {
    log.debug("Preparing planning context for provenance [{}] operation [{}]",
        request.provenanceCode(), request.operationCode());

    // Delegate to persistence coordinator
    ScheduleInstanceAggregate schedule =
        persistenceCoordinator.persistScheduleInstance(request);

    // Fetch configuration snapshot from registry
    ProvenanceConfigSnapshot configSnapshot =
        patraRegistryPort.fetchConfig(request.provenanceCode(), request.operationCode());

    PlanTriggerNorm norm = buildTriggerNorm(schedule, request);

    // Query cursor watermark
    Instant cursorWatermark =
        lookupCursorWatermark(request.provenanceCode(), request.operationCode());

    // Resolve planning window
    PlannerWindow window =
        resolvePlannerWindow(norm, configSnapshot, cursorWatermark, request.triggeredAt());

    return new PlanningContext(
        schedule, configSnapshot, norm, window,
        request.provenanceCode(), request.operationCode());
  }

  /**
   * Persists new plan with slices and tasks, then publishes queued events
   */
  private PlanIngestionResult persistAndPublishNewPlan(
      PlanAggregate draftPlan,
      PlanAssemblyResult assembly,
      ScheduleInstanceAggregate schedule,
      PlannerWindow window) {

    log.debug("Persisting plan for provenance [{}] operation [{}]: planKey={}",
        draftPlan.getProvenanceCode(), draftPlan.getOperationCode(), draftPlan.getPlanKey());

    // Delegate to persistence coordinator
    PlanAggregate persistedPlan = persistenceCoordinator.savePlan(draftPlan);
    List<PlanSliceAggregate> persistedSlices =
        persistenceCoordinator.persistSlices(persistedPlan, assembly.slices());
    List<TaskAggregate> persistedTasks =
        persistenceCoordinator.persistTasks(persistedPlan, persistedSlices, assembly.tasks());

    log.debug("Persisted plan [{}] with {} slices and {} tasks",
        persistedPlan.getId(), persistedSlices.size(), persistedTasks.size());

    // Delegate to publishing coordinator
    List<TaskQueuedEvent> queuedEvents =
        publishingCoordinator.collectQueuedEvents(persistedTasks);
    publishingCoordinator.publishNewPlanEvents(queuedEvents, persistedPlan, schedule);

    log.info("Successfully created plan [{}] for provenance [{}] operation [{}]: "
        + "{} slices, {} tasks generated for window [{}, {})",
        persistedPlan.getId(), persistedPlan.getProvenanceCode(),
        persistedPlan.getOperationCode(), persistedSlices.size(), persistedTasks.size(),
        window == null ? null : window.from(), window == null ? null : window.to());

    return publishingCoordinator.buildIngestionResult(
        schedule, persistedPlan, persistedSlices, persistedTasks.size(),
        assembly.status().name());
  }

  // Internal record holding planning context data
  private record PlanningContext(
      ScheduleInstanceAggregate schedule,
      ProvenanceConfigSnapshot configSnapshot,
      PlanTriggerNorm norm,
      PlannerWindow window,
      ProvenanceCode provenanceCode,
      OperationCode operationCode) {}
}
```

**Key Takeaways**:
- ✅ Orchestrate only, delegate business logic to Domain
- ✅ @Transactional at orchestrator level (transaction boundary)
- ✅ Coordinator pattern for separation of concerns
- ✅ Clear phases with logging
- ❌ NO business rules (belong in Domain)
- ❌ NO direct database access (use ports)

---

#### Coordinator: PlanPersistenceCoordinator

**File**: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/plan/PlanPersistenceCoordinator.java`

```java
/**
 * Coordinator for plan persistence operations.
 *
 * Responsible for safely persisting plan aggregates, slices, tasks, and schedule
 * instances with proper exception handling and logging.
 *
 * Note: This coordinator does NOT use @Transactional. It relies on the outer
 * transaction boundary from the main orchestrator.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanPersistenceCoordinator {

  private final PlanRepository planRepository;
  private final PlanSliceRepository planSliceRepository;
  private final TaskRepository taskRepository;
  private final ScheduleInstanceRepository scheduleInstanceRepository;

  /**
   * Saves or updates schedule instance (idempotent)
   */
  public ScheduleInstanceAggregate persistScheduleInstance(PlanIngestionCommand request) {
    ScheduleInstanceAggregate schedule = ScheduleInstanceAggregate.start(
        request.scheduler(),
        request.schedulerJobId(),
        request.schedulerLogId(),
        request.triggerType(),
        request.triggeredAt(),
        request.triggerParams(),
        request.provenanceCode().getCode());

    try {
      return scheduleInstanceRepository.saveOrUpdateInstance(schedule);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.SCHEDULE_INSTANCE,
          "Failed to persist schedule instance",
          ex);
    }
  }

  /**
   * Persists plan aggregate and wraps underlying exceptions
   */
  public PlanAggregate savePlan(PlanAggregate draftPlan) {
    try {
      return planRepository.save(draftPlan);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.PLAN,
          "Failed to persist plan aggregate",
          ex);
    }
  }

  /**
   * Batch persists plan slice aggregates
   */
  public List<PlanSliceAggregate> persistSlices(
      PlanAggregate plan,
      List<PlanSliceAggregate> slices) {
    if (CollUtil.isEmpty(slices)) {
      return List.of();
    }

    // Bind plan ID to each slice
    slices.forEach(slice -> slice.bindPlan(plan.getId()));

    try {
      return planSliceRepository.saveAll(slices);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.PLAN_SLICE,
          "Failed to persist plan slices",
          ex);
    }
  }

  /**
   * Batch persists task aggregates and binds plan and slice IDs
   */
  public List<TaskAggregate> persistTasks(
      PlanAggregate plan,
      List<PlanSliceAggregate> persistedSlices,
      List<TaskAggregate> tasks) {
    if (CollUtil.isEmpty(tasks)) {
      return List.of();
    }

    // Create slice lookup map
    Map<Integer, PlanSliceAggregate> sliceBySeq =
        MapUtil.newHashMap(persistedSlices.size());
    for (PlanSliceAggregate slice : persistedSlices) {
      sliceBySeq.putIfAbsent(slice.getSliceNo(), slice);
    }

    // Bind plan and slice IDs to each task
    for (TaskAggregate task : tasks) {
      Long placeholderSequence = task.getSliceId();
      PlanSliceAggregate slice = ObjectUtil.isNull(placeholderSequence)
          ? null
          : sliceBySeq.get(placeholderSequence.intValue());
      task.bindPlanAndSlice(plan.getId(), slice == null ? null : slice.getId());
    }

    try {
      return taskRepository.saveAll(tasks);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.TASK,
          "Failed to persist tasks",
          ex);
    }
  }
}
```

**Key Takeaways**:
- ✅ Coordinator separates persistence concerns
- ✅ Wraps exceptions with domain-specific types
- ✅ Batch operations for performance
- ✅ NO @Transactional (relies on outer boundary)
- ✅ Clear logging for debugging

---

### 3. Infrastructure Layer (Driven)

**Purpose**: Implement domain ports, provide data access.

#### Repository Implementation: PlanRepositoryMpImpl

**File**: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/repository/PlanRepositoryMpImpl.java`

```java
/**
 * MyBatis-Plus implementation of PlanRepository (Infrastructure layer).
 *
 * Responsibilities:
 * - Mapping between PlanAggregate and PlanDO (using MapStruct)
 * - Idempotent query by planKey / existence check
 * - Insert / update (optimistic locking via @Version)
 *
 * Logging strategy:
 * - DEBUG: log key fields on insert/update (id, planKey)
 * - INFO: avoid noisy high-frequency CRUD logs
 *
 * Thread-safety: stateless singleton via dependency injection
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PlanRepositoryMpImpl implements PlanRepository {

  /** Plan mapper (MyBatis-Plus) */
  private final PlanMapper planMapper;

  /** Aggregate-to-DO converter (MapStruct) */
  private final PlanConverter planConverter;

  /**
   * Saves a Plan: insert or update based on presence of ID.
   *
   * Converts aggregate to DO and back to ensure version/auto-increment
   * fields are reflected.
   */
  @Override
  public PlanAggregate save(PlanAggregate plan) {
    PlanDO entity = planConverter.toEntity(plan);

    if (entity.getId() == null) {
      // Insert new plan
      if (log.isDebugEnabled()) {
        log.debug("plan insert planKey={}", entity.getPlanKey());
      }
      planMapper.insert(entity);
    } else {
      // Update existing plan
      if (log.isDebugEnabled()) {
        log.debug("plan update id={} planKey={}", entity.getId(), entity.getPlanKey());
      }
      planMapper.updateById(entity);
    }

    // Convert back to aggregate (with generated ID/version)
    return planConverter.toAggregate(entity);
  }

  /**
   * Finds a plan by planKey (idempotent query)
   */
  @Override
  public Optional<PlanAggregate> findByPlanKey(String planKey) {
    if (planKey == null || planKey.isBlank()) {
      return Optional.empty();
    }

    PlanDO entity = planMapper.findByPlanKey(planKey);
    boolean found = entity != null;

    if (log.isDebugEnabled()) {
      log.debug("query plan by planKey={}, found={}", planKey, found);
    }

    return Optional.ofNullable(entity)
        .map(planConverter::toAggregate);
  }

  /**
   * Checks whether a planKey exists
   */
  @Override
  public boolean existsByPlanKey(String planKey) {
    if (planKey == null || planKey.isBlank()) {
      return false;
    }
    return planMapper.countByPlanKey(planKey) > 0;
  }
}
```

**Key Takeaways**:
- ✅ Implements domain port interface
- ✅ MapStruct for DO ↔ Domain conversion
- ✅ MyBatis-Plus for simple operations
- ✅ Detailed logging at DEBUG level
- ❌ NEVER expose DOs outside infrastructure layer

---

#### MyBatis-Plus Mapper: PlanMapper

**File**: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/mapper/PlanMapper.java`

```java
/**
 * MyBatis-Plus mapper for Plan entity.
 *
 * Extends BaseMapper for CRUD operations.
 * Custom queries defined here or in XML mapper.
 */
@Mapper
public interface PlanMapper extends BaseMapper<PlanDO> {

  /**
   * Find plan by business key
   * @param planKey idempotent key
   * @return plan DO if found
   */
  PlanDO findByPlanKey(@Param("planKey") String planKey);

  /**
   * Count plans by business key (for existence check)
   * @param planKey idempotent key
   * @return count (0 or 1)
   */
  int countByPlanKey(@Param("planKey") String planKey);
}
```

---

#### Data Object: PlanDO

**File**: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/entity/PlanDO.java`

```java
/**
 * MyBatis-Plus data object for t_batch_plan table.
 *
 * Annotations:
 * - @TableName: maps to table
 * - @TableId: auto-generated ID
 * - @TableField: custom type handlers or column mapping
 * - @TableLogic: soft delete
 * - @Version: optimistic locking
 */
@Data
@TableName("t_batch_plan")
public class PlanDO {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long scheduleInstanceId;
  private String planKey;
  private String provenanceCode;
  private String operationCode;

  /** Hash of plan expression prototype */
  private String exprProtoHash;

  /** JSON snapshot of expression prototype */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private String exprProtoSnapshotJson;

  /** JSON snapshot of provenance configuration */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private String provenanceConfigSnapshotJson;

  private String provenanceConfigHash;

  /** Window specification (JSON) */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private String windowSpecJson;

  /** Slicing strategy code */
  private String sliceStrategyCode;

  /** Slicing parameters (JSON) */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private String sliceParamsJson;

  /** Current plan status */
  private Integer status;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updatedAt;

  /** Soft delete flag */
  @TableLogic
  private Boolean deleted;

  /** Optimistic locking version */
  @Version
  private Integer version;
}
```

**Key Annotations**:
- `@TableName`: Map to table name
- `@TableId(type = IdType.ASSIGN_ID)`: Auto-generate ID (Snowflake)
- `@TableField(typeHandler = JacksonTypeHandler.class)`: Store JSON
- `@TableLogic`: Soft delete (deleted=true)
- `@Version`: Optimistic locking
- `@TableField(fill = FieldFill.INSERT)`: Auto-fill on insert

---

#### MapStruct Converter: PlanConverter

**File**: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/converter/PlanConverter.java`

```java
/**
 * MapStruct converter between PlanAggregate and PlanDO.
 *
 * Handles:
 * - Value object ↔ primitive conversions
 * - Enum ↔ code conversions
 * - Complex object ↔ JSON conversions
 */
@Mapper(componentModel = "spring")
public interface PlanConverter {

  /**
   * Converts DO to Domain aggregate
   *
   * @param entity PlanDO from database
   * @return PlanAggregate for domain layer
   */
  @Mapping(target = "id", source = "id")
  @Mapping(target = "scheduleInstanceId", source = "scheduleInstanceId")
  @Mapping(target = "planKey", source = "planKey")
  @Mapping(target = "provenanceCode", source = "provenanceCode")
  @Mapping(target = "operationCode",
      expression = "java(mapOperationCode(entity.getOperationCode()))")
  @Mapping(target = "windowSpec",
      expression = "java(mapWindowSpec(entity.getWindowSpecJson()))")
  @Mapping(target = "status",
      expression = "java(mapPlanStatus(entity.getStatus()))")
  PlanAggregate toAggregate(PlanDO entity);

  /**
   * Converts Domain aggregate to DO
   *
   * @param aggregate PlanAggregate from domain
   * @return PlanDO for persistence
   */
  @Mapping(target = "id", source = "id")
  @Mapping(target = "scheduleInstanceId", source = "scheduleInstanceId")
  @Mapping(target = "planKey", source = "planKey")
  @Mapping(target = "provenanceCode", source = "provenanceCode")
  @Mapping(target = "operationCode",
      expression = "java(aggregate.getOperationCode().getCode())")
  @Mapping(target = "windowSpecJson",
      expression = "java(serializeWindowSpec(aggregate.getWindowSpec()))")
  @Mapping(target = "status",
      expression = "java(aggregate.getStatus().getCode())")
  @Mapping(target = "deleted", ignore = true)  // Managed by MyBatis-Plus
  @Mapping(target = "version", ignore = true)  // Managed by @Version
  @Mapping(target = "createdAt", ignore = true)  // Auto-fill
  @Mapping(target = "updatedAt", ignore = true)  // Auto-fill
  PlanDO toEntity(PlanAggregate aggregate);

  // Helper methods for complex conversions
  default OperationCode mapOperationCode(String code) {
    return code == null ? null : OperationCode.fromCode(code);
  }

  default PlanStatus mapPlanStatus(Integer code) {
    return code == null ? null : PlanStatus.fromCode(code);
  }

  default WindowSpec mapWindowSpec(String json) {
    // JSON deserialization logic
    return WindowSpec.fromJson(json);
  }

  default String serializeWindowSpec(WindowSpec spec) {
    // JSON serialization logic
    return spec.toJson();
  }
}
```

**Key Takeaways**:
- ✅ MapStruct for type-safe conversions
- ✅ Expression mappings for complex conversions
- ✅ Ignore auto-managed fields (version, timestamps)
- ✅ Helper methods for enum/JSON conversions

---

## Refactoring Example: Bad to Good

### BEFORE: Business Logic in Wrong Layer ❌

```java
// ❌ BAD: Direct MyBatis-Plus usage in Application layer
@Service
@RequiredArgsConstructor
public class PlanIngestionService {

  private final PlanMapper planMapper;  // ❌ Wrong! Should use domain port

  @Transactional
  public void createPlan(CreatePlanRequest request) {
    // ❌ Business logic mixed with persistence
    PlanDO planDO = new PlanDO();
    planDO.setProvenanceCode(request.getProvenanceCode());
    planDO.setOperationCode(request.getOperationCode());

    // ❌ Business rules in service layer
    if (request.getWindowFrom().isAfter(request.getWindowTo())) {
      throw new ValidationException("Invalid window");
    }

    // ❌ Direct mapper usage
    planMapper.insert(planDO);

    // ... 100+ more lines of mixed logic
  }
}
```

### AFTER: Clean Separation ✅

**1. Domain Layer** (Business Rules):
```java
// ✅ GOOD: Business rules in domain aggregate
@Getter
public class PlanAggregate extends AggregateRoot<Long> {

  private final WindowSpec windowSpec;

  // Factory method with validation
  public static PlanAggregate create(/* parameters */) {
    // ✅ Business rule validation in domain
    if (windowFrom.isAfter(windowTo)) {
      throw new IllegalArgumentException("Invalid window: from must be before to");
    }
    return new PlanAggregate(/* ... */);
  }
}
```

**2. Application Layer** (Orchestration):
```java
// ✅ GOOD: Clean orchestration, delegates to domain and ports
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator {

  private final PlanRepository planRepository;  // ✅ Domain port
  private final PlanPersistenceCoordinator persistenceCoordinator;

  @Transactional
  public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
    // ✅ Delegate to domain for creation
    PlanAggregate plan = PlanAggregate.create(/* ... */);

    // ✅ Delegate to coordinator for persistence
    PlanAggregate persisted = persistenceCoordinator.savePlan(plan);

    return PlanIngestionResult.from(persisted);
  }
}
```

**3. Infrastructure Layer** (Data Access):
```java
// ✅ GOOD: Clean repository implementation
@Repository
@RequiredArgsConstructor
public class PlanRepositoryMpImpl implements PlanRepository {

  private final PlanMapper planMapper;
  private final PlanConverter planConverter;

  @Override
  public PlanAggregate save(PlanAggregate plan) {
    // ✅ Convert domain → DO
    PlanDO entity = planConverter.toEntity(plan);
    planMapper.insert(entity);

    // ✅ Convert back DO → domain
    return planConverter.toAggregate(entity);
  }
}
```

**Result**:
- Domain: Pure business logic, easy to test
- Application: Clear orchestration, no business rules
- Infrastructure: Clean data access, no business logic
- **Testable, maintainable, follows architecture!**

---

## End-to-End Request Flow

### Complete Request Flow Diagram

```
Scheduler Trigger (XXL-Job or Manual)
    ↓
PlanIngestionOrchestrator.ingestPlan(command)
    ↓
Phase 1: Prepare Planning Context
    ├─ PlanPersistenceCoordinator.persistScheduleInstance()
    │   └─ ScheduleInstanceRepositoryMpImpl.saveOrUpdateInstance()
    │       └─ MyBatis-Plus insert/update
    ├─ PatraRegistryPort.fetchConfig() [External service call]
    ├─ CursorRepository.findLatestGlobalTimeWatermark()
    │   └─ MyBatis-Plus query
    └─ PlanningWindowResolver.resolveWindow() [Domain service]
    ↓
Phase 2: Build Plan Expression
    └─ PlanExpressionBuilder.build() [Domain service]
    ↓
Phase 3: Pre-validation
    ├─ TaskRepository.countQueuedTasks()
    │   └─ MyBatis-Plus count query
    └─ PlannerValidator.validateBeforeAssemble() [Domain service]
    ↓
Phase 4: Assemble Plan
    └─ PlanAssembler.assemble()
        └─ Returns PlanAggregate + List<PlanSliceAggregate> + List<TaskAggregate>
    ↓
Phase 5: Check Idempotency
    └─ PlanRepository.findByPlanKey()
        └─ MyBatis-Plus query by business key
        ↓
        If exists → PlanIdempotencyCoordinator.handleIdempotentPlanReuse()
        If not exists → Continue to Phase 6
    ↓
Phase 6: Persist and Publish
    ├─ PlanPersistenceCoordinator.savePlan()
    │   └─ PlanRepositoryMpImpl.save()
    │       └─ MyBatis-Plus insert
    ├─ PlanPersistenceCoordinator.persistSlices()
    │   └─ PlanSliceRepositoryMpImpl.saveAll()
    │       └─ MyBatis-Plus batch insert
    ├─ PlanPersistenceCoordinator.persistTasks()
    │   └─ TaskRepositoryMpImpl.saveAll()
    │       └─ MyBatis-Plus batch insert
    ├─ PlanPublishingCoordinator.collectQueuedEvents()
    │   └─ Create TaskQueuedEvent for each task
    └─ PlanPublishingCoordinator.publishNewPlanEvents()
        └─ OutboxPublisher.publish() [Outbox pattern, atomic with DB transaction]
            └─ OutboxMessageRepositoryMpImpl.insert()
                └─ MyBatis-Plus insert into t_outbox_message
    ↓
@Transactional COMMIT (All persistence + outbox in single transaction)
    ↓
Outbox Relay Job (separate process)
    └─ Publishes messages from t_outbox_message to RocketMQ
    ↓
Task Execution Workers
    └─ Consume TaskQueuedEvent and execute data collection
```

**Key Observations**:
- ✅ **Single @Transactional boundary** at orchestrator level
- ✅ **Outbox pattern** ensures reliable event publishing
- ✅ **Idempotency** via business key (planKey)
- ✅ **Separation of concerns** via coordinators
- ✅ **NO external API calls inside transaction** (configuration fetch happens before)

---

## Testing Strategy

### 1. Domain Layer Tests (Pure Java)

**File**: `patra-ingest-domain/src/test/java/com/patra/ingest/domain/model/aggregate/PlanAggregateTest.java`

```java
/**
 * Unit tests for PlanAggregate (NO mocks needed)
 */
class PlanAggregateTest {

  @Test
  void should_create_plan_with_valid_window() {
    // Given
    Instant windowFrom = Instant.parse("2024-01-01T00:00:00Z");
    Instant windowTo = Instant.parse("2024-01-02T00:00:00Z");

    // When
    PlanAggregate plan = PlanAggregate.create(
        scheduleInstanceId,
        planKey,
        provenanceCode,
        operationCode,
        windowFrom,
        windowTo,
        /* other params */
    );

    // Then
    assertThat(plan).isNotNull();
    assertThat(plan.getWindowSpec().from()).isEqualTo(windowFrom);
    assertThat(plan.getWindowSpec().to()).isEqualTo(windowTo);
  }

  @Test
  void should_throw_exception_when_window_invalid() {
    // Given
    Instant windowFrom = Instant.parse("2024-01-02T00:00:00Z");
    Instant windowTo = Instant.parse("2024-01-01T00:00:00Z");  // Before from!

    // When & Then
    assertThrows(IllegalArgumentException.class, () -> {
      PlanAggregate.create(/* params with invalid window */);
    });
  }
}
```

### 2. Application Layer Tests (Mock Ports)

**File**: `patra-ingest-app/src/test/java/com/patra/ingest/app/usecase/plan/PlanIngestionOrchestratorTest.java`

```java
/**
 * Unit tests for PlanIngestionOrchestrator (mock domain ports)
 */
@ExtendWith(MockitoExtension.class)
class PlanIngestionOrchestratorTest {

  @Mock
  private PlanRepository planRepository;

  @Mock
  private CursorRepository cursorRepository;

  @Mock
  private PlanPersistenceCoordinator persistenceCoordinator;

  @InjectMocks
  private PlanIngestionOrchestrator orchestrator;

  @Test
  void should_create_plan_successfully() {
    // Given
    PlanIngestionCommand command = createTestCommand();
    when(cursorRepository.findLatestGlobalTimeWatermark(any(), any()))
        .thenReturn(Optional.of(Instant.now()));
    when(planRepository.findByPlanKey(any()))
        .thenReturn(Optional.empty());
    when(persistenceCoordinator.savePlan(any()))
        .thenReturn(createTestPlan());

    // When
    PlanIngestionResult result = orchestrator.ingestPlan(command);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.planId()).isNotNull();
    verify(persistenceCoordinator).savePlan(any());
  }
}
```

### 3. Infrastructure Layer Tests (Integration with TestContainers)

**File**: `patra-ingest-boot/src/test/java/com/patra/ingest/infra/persistence/repository/PlanRepositoryMpImplIT.java`

```java
/**
 * Integration tests for PlanRepositoryMpImpl (real database)
 */
@SpringBootTest
@Testcontainers
class PlanRepositoryMpImplIT {

  @Container
  static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
      .withDatabaseName("papertrace_test")
      .withUsername("test")
      .withPassword("test");

  @Autowired
  private PlanRepository planRepository;

  @Test
  void should_save_and_find_plan_by_plan_key() {
    // Given
    PlanAggregate plan = createTestPlan();
    String planKey = plan.getPlanKey();

    // When
    PlanAggregate saved = planRepository.save(plan);
    Optional<PlanAggregate> found = planRepository.findByPlanKey(planKey);

    // Then
    assertThat(saved.getId()).isNotNull();
    assertThat(found).isPresent();
    assertThat(found.get().getPlanKey()).isEqualTo(planKey);
  }

  @Test
  void should_handle_duplicate_plan_key_idempotently() {
    // Given
    PlanAggregate plan1 = createTestPlan();
    PlanAggregate plan2 = createTestPlan();  // Same planKey

    // When
    planRepository.save(plan1);
    boolean exists = planRepository.existsByPlanKey(plan1.getPlanKey());

    // Then
    assertThat(exists).isTrue();
    // Trying to save plan2 would violate unique constraint on planKey
  }
}
```

---

**Related Files:**
- [SKILL.md](../SKILL.md) - Main guide
- [orchestrator-coordinator-patterns.md](orchestrator-coordinator-patterns.md) - Orchestrator patterns
- [domain-modeling-patterns.md](domain-modeling-patterns.md) - Domain layer patterns
- [mybatis-plus-patterns.md](mybatis-plus-patterns.md) - Database access patterns
- [adapter-layer-patterns.md](adapter-layer-patterns.md) - Adapter layer patterns

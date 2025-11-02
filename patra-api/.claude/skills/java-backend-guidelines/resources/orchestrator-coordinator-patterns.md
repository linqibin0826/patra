# 编排器与协调器模式 - 应用层

应用层中组织用例编排和协调逻辑的完整指南。

## 目录

- [Application Layer Overview](#application-layer-overview)
- [Orchestrator Pattern](#orchestrator-pattern)
- [Coordinator Pattern](#coordinator-pattern)
- [Pattern 1: Orchestrator + Coordinators](#pattern-1-orchestrator--coordinators)
- [Pattern 2: Main Orchestrator + Sub-UseCases](#pattern-2-main-orchestrator--sub-usecases)
- [Transaction Management](#transaction-management)
- [Design Principles](#design-principles)
- [Testing Orchestrators](#testing-orchestrators)

---

## 应用层 Overview

### 目的 of Application Layer

**Application layer coordinates use cases** - the 'how' of workflows:

```
Controller asks: "Create a plan for this provenance"
Orchestrator coordinates: "Fetch config → Validate → Assemble → Persist → Publish"
Coordinators execute: "Here's how to persist", "Here's how to publish"
Domain provides: Business rules and validations
```

**Application layer is responsible for:**
- ✅ Use case orchestration (workflow coordination)
- ✅ Transaction boundaries (@Transactional)
- ✅ Orchestrating multiple coordinators
- ✅ Delegating to domain for business logic
- ✅ Delegating to infrastructure via ports

**Application layer should NOT:**
- ❌ Contain business rules (belongs in Domain)
- ❌ Direct database access (use domain ports)
- ❌ Complex calculations (belongs in Domain)
- ❌ Know about HTTP/REST (belongs in Adapter)

---

## Orchestrator Pattern

### What is an Orchestrator?

**Orchestrator = Use Case Coordinator**

An orchestrator implements a complete use case by coordinating:
- Domain services and aggregates
- Multiple coordinators (separation of concerns)
- Infrastructure services via ports
- Transaction boundaries

### Orchestrator Template

**File**: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/plan/PlanIngestionOrchestrator.java`

```java
/**
 * Main orchestrator for plan ingestion flow.
 *
 * Coordinates six phases:
 * 1. Prepare planning context (config + window)
 * 2. Build plan expression
 * 3. Pre-validation
 * 4. Assemble plan/slices/tasks
 * 5. Check idempotency
 * 6. Persist and publish events
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {

  // Domain ports (defined in domain layer)
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

  @Override
  @Transactional  // ✅ Transaction boundary at orchestrator level
  public PlanIngestionResult ingestPlan(PlanIngestionCommand request) {
    logPlanIngestionStart(request);

    // Phase 1: Prepare context
    PlanningContext context = preparePlanningContext(request);

    // Phase 2: Build expression
    PlanExpressionDescriptor expressionDescriptor = buildPlanExpression(context);

    // Phase 3: Pre-validation
    performPreValidation(context);

    // Phase 4: Assemble plan
    PlanAssemblyResult assembly = assembleAndValidatePlan(context, expressionDescriptor);

    // Phase 5: Check idempotency
    PlanAggregate existingPlan = checkForExistingPlan(assembly.plan());
    if (existingPlan != null) {
      return idempotencyCoordinator.handleIdempotentPlanReuse(
          existingPlan, context.schedule(), assembly.plan().getPlanKey());
    }

    // Phase 6: Persist and publish
    return persistAndPublishNewPlan(
        assembly.plan(), assembly, context.schedule(), context.window());
  }

  private PlanningContext preparePlanningContext(PlanIngestionCommand request) {
    // Delegate to coordinator for schedule persistence
    ScheduleInstanceAggregate schedule =
        persistenceCoordinator.persistScheduleInstance(request);

    // Fetch configuration from registry (NO transaction - external call)
    ProvenanceConfigSnapshot configSnapshot =
        patraRegistryPort.fetchConfig(request.provenanceCode(), request.operationCode());

    // Query cursor watermark via port
    Instant cursorWatermark =
        lookupCursorWatermark(request.provenanceCode(), request.operationCode());

    // Delegate to domain service for window resolution
    PlannerWindow window =
        resolvePlannerWindow(norm, configSnapshot, cursorWatermark, request.triggeredAt());

    return new PlanningContext(schedule, configSnapshot, norm, window, /* ... */);
  }

  private PlanIngestionResult persistAndPublishNewPlan(
      PlanAggregate draftPlan,
      PlanAssemblyResult assembly,
      ScheduleInstanceAggregate schedule,
      PlannerWindow window) {

    // Delegate to persistence coordinator
    PlanAggregate persistedPlan = persistenceCoordinator.savePlan(draftPlan);
    List<PlanSliceAggregate> persistedSlices =
        persistenceCoordinator.persistSlices(persistedPlan, assembly.slices());
    List<TaskAggregate> persistedTasks =
        persistenceCoordinator.persistTasks(persistedPlan, persistedSlices, assembly.tasks());

    // Delegate to publishing coordinator
    List<TaskQueuedEvent> queuedEvents =
        publishingCoordinator.collectQueuedEvents(persistedTasks);
    publishingCoordinator.publishNewPlanEvents(queuedEvents, persistedPlan, schedule);

    return publishingCoordinator.buildIngestionResult(
        schedule, persistedPlan, persistedSlices, persistedTasks.size(), assembly.status().name());
  }
}
```

**Key Takeaways**:
- ✅ Orchestrate only, NO business rules
- ✅ @Transactional at orchestrator level (single boundary)
- ✅ Delegate to coordinators for separation of concerns
- ✅ Clear phases with descriptive method names
- ✅ Use domain ports (NOT direct infrastructure)
- ❌ NO business logic (delegate to domain)
- ❌ NO external API calls inside @Transactional

---

## Coordinator Pattern

### What is a Coordinator?

**Coordinator = Responsibility Separator**

A coordinator handles ONE specific concern within a use case:
- Persistence operations
- Idempotency handling
- Event publishing
- Validation coordination

### When to Use Coordinators?

✅ **Use coordinators when:**
- Complex orchestrator with multiple concerns
- Need to separate persistence/publishing/validation logic
- Want to reuse coordination logic across orchestrators
- Need clear transaction boundary visibility

❌ **Skip coordinators when:**
- Simple orchestrator with one responsibility
- Orchestrator already small (<100 lines)
- No reuse needed

---

### Coordinator Example 1: Persistence

**目的**: Coordinate all persistence operations with error handling.

**File**: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/plan/PlanPersistenceCoordinator.java`

```java
/**
 * Coordinator for plan persistence operations.
 *
 * Responsibilities:
 * - Safely persisting plan aggregates, slices, tasks
 * - Proper exception handling and wrapping
 * - Batch operations coordination
 *
 * Note: Does NOT use @Transactional - relies on outer boundary
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
      // ✅ Wrap with domain-specific exception
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.SCHEDULE_INSTANCE,
          "Failed to persist schedule instance",
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

    // ✅ Bind plan ID to each slice
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
   * Batch persists task aggregates and binds plan/slice IDs
   */
  public List<TaskAggregate> persistTasks(
      PlanAggregate plan,
      List<PlanSliceAggregate> persistedSlices,
      List<TaskAggregate> tasks) {
    if (CollUtil.isEmpty(tasks)) {
      return List.of();
    }

    // ✅ Create slice lookup map for efficient binding
    Map<Integer, PlanSliceAggregate> sliceBySeq = MapUtil.newHashMap(persistedSlices.size());
    for (PlanSliceAggregate slice : persistedSlices) {
      sliceBySeq.putIfAbsent(slice.getSliceNo(), slice);
    }

    // ✅ Bind plan and slice IDs to each task
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
- ✅ Separates persistence concerns from orchestration
- ✅ Wraps infrastructure exceptions with domain exceptions
- ✅ Batch operations for performance
- ✅ NO @Transactional (relies on orchestrator boundary)
- ✅ Clear logging for debugging
- ✅ Handles data binding (plan/slice IDs)

---

### Coordinator Example 2: Idempotency

**目的**: Handle duplicate detection and retry logic.

**File**: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/plan/PlanIdempotencyCoordinator.java`

```java
/**
 * Coordinator for plan idempotency and retry logic.
 *
 * Responsibilities:
 * - Handling duplicate plan detection
 * - Identifying tasks eligible for retry
 * - Coordinating retry operations
 *
 * Note: Does NOT use @Transactional - relies on outer boundary
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanIdempotencyCoordinator {

  private final PlanSliceRepository planSliceRepository;
  private final TaskRepository taskRepository;
  private final PlanPersistenceCoordinator persistenceCoordinator;
  private final PlanPublishingCoordinator publishingCoordinator;

  /**
   * Handles idempotent plan reuse when existing plan found.
   *
   * Steps:
   * 1. Load existing slices and tasks
   * 2. Identify tasks eligible for retry (FAILED status)
   * 3. Reset and re-queue retry tasks
   * 4. Publish retry events via outbox
   * 5. Return result based on existing plan
   */
  public PlanIngestionResult handleIdempotentPlanReuse(
      PlanAggregate existingPlan,
      ScheduleInstanceAggregate schedule,
      String planKey) {

    logDuplicatePlanDetection(existingPlan, planKey);

    // ✅ Load existing data
    List<PlanSliceAggregate> existingSlices =
        planSliceRepository.findByPlanId(existingPlan.getId());
    List<TaskAggregate> existingTasks =
        taskRepository.findByPlanId(existingPlan.getId());

    // ✅ Identify and prepare retry tasks
    List<TaskAggregate> retryTasks = prepareTasksForRetry(existingTasks);

    if (!retryTasks.isEmpty()) {
      processRetryTasks(existingPlan, schedule, retryTasks);
    } else {
      log.info(
          "No tasks require retry for existing plan [{}], returning existing state",
          existingPlan.getId());
    }

    return publishingCoordinator.buildIngestionResult(
        schedule, existingPlan, existingSlices, existingTasks);
  }

  /**
   * Prepares tasks for retry by resetting failed tasks
   */
  private List<TaskAggregate> prepareTasksForRetry(List<TaskAggregate> tasks) {
    List<TaskAggregate> retryTasks = new ArrayList<>();
    for (TaskAggregate task : tasks) {
      if (shouldRetry(task)) {
        // ✅ Delegate to domain aggregate for retry preparation
        task.prepareForRetry();
        persistenceCoordinator.saveTask(task);
        retryTasks.add(task);
      }
    }
    return retryTasks;
  }

  /**
   * Determines if task is eligible for retry
   */
  private boolean shouldRetry(TaskAggregate task) {
    TaskStatus status = task.getStatus();
    return status == TaskStatus.FAILED;
  }

  private void processRetryTasks(
      PlanAggregate existingPlan,
      ScheduleInstanceAggregate schedule,
      List<TaskAggregate> retryTasks) {
    // ✅ Delegate to publishing coordinator
    List<TaskQueuedEvent> retryEvents =
        publishingCoordinator.collectQueuedEvents(retryTasks);
    publishingCoordinator.publishRetryEvents(retryEvents, existingPlan, schedule);
  }
}
```

**Key Takeaways**:
- ✅ Encapsulates idempotency logic
- ✅ Delegates to domain aggregate for business logic (prepareForRetry)
- ✅ Coordinates with other coordinators (persistence, publishing)
- ✅ Clear separation of concerns

---

### Coordinator Example 3: Publishing

**目的**: Collect domain events and publish via Outbox pattern.

**File**: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/plan/PlanPublishingCoordinator.java`

```java
/**
 * Coordinator for plan publishing operations.
 *
 * Responsibilities:
 * - Collecting domain events from aggregates
 * - Publishing events via Outbox pattern
 * - Building ingestion results
 *
 * Note: Does NOT use @Transactional - relies on outer boundary
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanPublishingCoordinator {

  private final TaskOutboxPublisher taskOutboxPublisher;

  /**
   * Publishes task queued events for new plans
   */
  public void publishNewPlanEvents(
      List<TaskQueuedEvent> queuedEvents,
      PlanAggregate plan,
      ScheduleInstanceAggregate schedule) {

    log.debug(
        "Publishing {} task-ready events to outbox for plan [{}]",
        queuedEvents.size(),
        plan.getId());

    // ✅ Delegate to outbox publisher (transactional with persistence)
    taskOutboxPublisher.publish(queuedEvents, plan, schedule);
  }

  /**
   * Publishes retry events for existing plans
   */
  public void publishRetryEvents(
      List<TaskQueuedEvent> retryEvents,
      PlanAggregate plan,
      ScheduleInstanceAggregate schedule) {

    taskOutboxPublisher.publishRetry(retryEvents, plan, schedule);
    log.info("Re-queued {} failed tasks for existing plan [{}]",
        retryEvents.size(), plan.getId());
  }

  /**
   * Collects TaskQueuedEvent instances from task aggregates.
   *
   * Explicitly triggers raiseQueuedEvent() to ensure events exist.
   */
  public List<TaskQueuedEvent> collectQueuedEvents(List<TaskAggregate> tasks) {
    if (CollUtil.isEmpty(tasks)) {
      return List.of();
    }

    List<TaskQueuedEvent> events = new ArrayList<>(tasks.size());
    for (TaskAggregate task : tasks) {
      // ✅ Trigger domain event creation
      task.raiseQueuedEvent();

      // ✅ Pull and collect events from aggregate
      task.pullDomainEvents().stream()
          .filter(TaskQueuedEvent.class::isInstance)
          .map(TaskQueuedEvent.class::cast)
          .forEach(events::add);
    }
    return events;
  }

  /**
   * Builds plan ingestion result from plan data
   */
  public PlanIngestionResult buildIngestionResult(
      ScheduleInstanceAggregate schedule,
      PlanAggregate plan,
      List<PlanSliceAggregate> slices,
      List<TaskAggregate> tasks) {

    return new PlanIngestionResult(
        schedule.getId(),
        plan.getId(),
        slices.stream().map(PlanSliceAggregate::getId).collect(Collectors.toList()),
        tasks.size(),
        plan.getStatus().name());
  }
}
```

**Key Takeaways**:
- ✅ Encapsulates event publishing logic
- ✅ Collects events from domain aggregates
- ✅ Ensures Outbox pattern atomicity (transactional with DB)
- ✅ Builds result DTOs for orchestrator

---

## Pattern 1: Orchestrator + Coordinators

### When to Use

**Use this pattern when:**
- ✅ Single transaction boundary needed
- ✅ Complex internal flow with multiple concerns
- ✅ Want separation of concerns within application layer
- ✅ Multiple coordinators can be reused

**示例**: PlanIngestionOrchestrator + 3 Coordinators

```java
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator {

  private final PlanPersistenceCoordinator persistenceCoordinator;
  private final PlanIdempotencyCoordinator idempotencyCoordinator;
  private final PlanPublishingCoordinator publishingCoordinator;

  @Transactional  // ✅ Single transaction boundary
  public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
    // Phase 1: Prepare and validate
    PlanningContext context = preparePlanningContext(command);
    performPreValidation(context);

    // Phase 2: Assemble plan
    PlanAssemblyResult assembly = assembleAndValidatePlan(context, expression);

    // Phase 3: Check idempotency
    PlanAggregate existingPlan = checkForExistingPlan(assembly.plan());
    if (existingPlan != null) {
      // ✅ Delegate to idempotency coordinator
      return idempotencyCoordinator.handleIdempotentPlanReuse(
          existingPlan, context.schedule(), assembly.plan().getPlanKey());
    }

    // Phase 4: Persist (delegate to persistence coordinator)
    PlanAggregate persistedPlan = persistenceCoordinator.savePlan(assembly.plan());
    List<PlanSliceAggregate> persistedSlices =
        persistenceCoordinator.persistSlices(persistedPlan, assembly.slices());
    List<TaskAggregate> persistedTasks =
        persistenceCoordinator.persistTasks(persistedPlan, persistedSlices, assembly.tasks());

    // Phase 5: Publish events (delegate to publishing coordinator)
    List<TaskQueuedEvent> queuedEvents =
        publishingCoordinator.collectQueuedEvents(persistedTasks);
    publishingCoordinator.publishNewPlanEvents(queuedEvents, persistedPlan, schedule);

    return publishingCoordinator.buildIngestionResult(
        schedule, persistedPlan, persistedSlices, persistedTasks.size(), assembly.status().name());
  }
}
```

**Coordinators** (NO @Transactional):
```java
@Service
@RequiredArgsConstructor
public class PlanPersistenceCoordinator {
  // ❌ NO @Transactional - relies on outer boundary
  public PlanAggregate savePlan(PlanAggregate plan) { /* ... */ }
  public List<PlanSliceAggregate> persistSlices(/* ... */) { /* ... */ }
  public List<TaskAggregate> persistTasks(/* ... */) { /* ... */ }
}

@Service
@RequiredArgsConstructor
public class PlanIdempotencyCoordinator {
  // ❌ NO @Transactional - relies on outer boundary
  public PlanIngestionResult handleIdempotentPlanReuse(/* ... */) { /* ... */ }
}

@Service
@RequiredArgsConstructor
public class PlanPublishingCoordinator {
  // ❌ NO @Transactional - relies on outer boundary
  public void publishNewPlanEvents(/* ... */) { /* ... */ }
  public List<TaskQueuedEvent> collectQueuedEvents(/* ... */) { /* ... */ }
}
```

**Benefits**:
- ✅ Clear separation of concerns
- ✅ Coordinators are reusable
- ✅ Single transaction ensures atomicity
- ✅ Easy to test (mock coordinators)

**Trade-offs**:
- ⚠️ More classes to maintain
- ⚠️ Need to understand coordinator responsibilities

---

## Pattern 2: Main Orchestrator + Sub-UseCases

### When to Use

**Use this pattern when:**
- ✅ Multiple independent transactions needed
- ✅ External API calls between phases
- ✅ Long-running operations
- ✅ Sub-usecases can be reused independently

**示例**: OutboxRelayOrchestrator + Executor

**File**: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/relay/OutboxRelayOrchestrator.java`

```java
/**
 * Orchestrator for Outbox Relay use case.
 *
 * Flow:
 * 1. Check feature toggle
 * 2. Build relay plan (NO transaction)
 * 3. Execute relay (WITH transaction - updates message state)
 * 4. Publish events (WITH transaction)
 * 5. Assemble report
 *
 * Transaction semantics: @Transactional covers executor + event publishing
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayOrchestrator implements OutboxRelayUseCase {

  private final OutboxRelayProperties properties;
  private final RelayPlanBuilder planBuilder;
  private final OutboxRelayExecutor relayExecutor;
  private final RelayEventPublisher eventPublisher;

  @Override
  @Transactional  // ✅ Transaction boundary at orchestrator level
  public RelayReport relay(OutboxRelayCommand instruction) {
    // Phase 1: Feature toggle check (NO transaction needed)
    if (!properties.isEnabled()) {
      log.info("Outbox relay disabled, skip channel={}", instruction.channel());
      return RelayReport.empty(instruction.channel());
    }

    long start = System.currentTimeMillis();

    // Phase 2: Build plan (NO transaction needed)
    RelayPlan plan = planBuilder.build(instruction);
    log.debug("relay plan built channel={} batchSize={} leaseOwner={}",
        plan.channel(), plan.batchSize(), plan.leaseOwner());

    // Phase 3: Execute relay (WITHIN transaction - updates message status)
    RelayBatchResult result = relayExecutor.execute(plan);

    // Phase 4: Publish events (WITHIN same transaction)
    eventPublisher.publish(result.events());

    long elapsed = System.currentTimeMillis() - start;
    log.info("relay completed channel={} fetched={} published={} costMs={}",
        result.channel(), result.fetched(), result.published(), elapsed);

    return new RelayReport(
        result.channel(),
        result.fetched(),
        result.published(),
        result.retried(),
        result.failed(),
        result.leaseMissed());
  }
}
```

**Sub-UseCase: RelayExecutor** (NO @Transactional):
```java
/**
 * Executor for relay operations.
 *
 * Delegates to coordinators for:
 * - Lease acquisition
 * - Message publishing
 * - Log recording
 *
 * Note: NO @Transactional - relies on orchestrator boundary
 */
@Service
@RequiredArgsConstructor
public class OutboxRelayExecutor {

  private final RelayLeaseCoordinator leaseCoordinator;
  private final RelayPublishCoordinator publishCoordinator;
  private final RelayLogCoordinator logCoordinator;

  // ❌ NO @Transactional
  public RelayBatchResult execute(RelayPlan plan) {
    // Try acquire lease
    boolean leaseAcquired = leaseCoordinator.tryAcquireLease(plan);
    if (!leaseAcquired) {
      return RelayBatchResult.leaseMissed(plan.channel());
    }

    // Fetch messages
    List<OutboxMessage> messages = fetchMessages(plan);

    // Publish to RocketMQ
    RelayBatchResult result = publishCoordinator.publishBatch(messages, plan);

    // Log relay result
    logCoordinator.recordRelayLog(result);

    return result;
  }
}
```

**Benefits**:
- ✅ Clear phase separation
- ✅ Executor is reusable
- ✅ Transaction only covers DB operations
- ✅ Feature toggle check outside transaction

**Trade-offs**:
- ⚠️ More complex flow
- ⚠️ Need to understand transaction boundaries

---

## Transaction Management

### Rule 1: @Transactional at Orchestrator Level ONLY

```java
// ✅ GOOD: Transaction at orchestrator
@Service
public class PlanIngestionOrchestrator {
  @Transactional  // ✅ Single boundary
  public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
    // All coordinators participate in this transaction
    persistenceCoordinator.savePlan(plan);
    publishingCoordinator.publishEvents(events);
  }
}

// ✅ GOOD: Coordinator without @Transactional
@Service
public class PlanPersistenceCoordinator {
  // ❌ NO @Transactional - relies on outer boundary
  public PlanAggregate savePlan(PlanAggregate plan) {
    return planRepository.save(plan);
  }
}
```

```java
// ❌ BAD: @Transactional on coordinator
@Service
public class PlanPersistenceCoordinator {
  @Transactional  // ❌ Wrong! Creates nested transaction
  public PlanAggregate savePlan(PlanAggregate plan) {
    return planRepository.save(plan);
  }
}
```

**Why?**
- ✅ Single transaction boundary is clear
- ✅ Avoids nested transaction complexity
- ✅ Easier to reason about atomicity
- ✅ Coordinator reuse doesn't create unexpected transactions

---

### Rule 2: NEVER Call External APIs Inside @Transactional

```java
// ❌ BAD: External API inside transaction
@Transactional
public void execute() {
  prepare();           // DB query - holds transaction!
  callPubMedAPI();    // 10+ seconds - transaction blocked!
  saveResults();      // DB update
}
```

```java
// ✅ GOOD: External API outside transaction
public void execute() {
  // Phase 1: Prepare (NO transaction)
  ConfigSnapshot config = fetchConfigFromRegistry();  // External API

  // Phase 2: Execute (WITH transaction)
  executePlan(config);
}

@Transactional
private void executePlan(ConfigSnapshot config) {
  // Only DB operations inside transaction
  PlanAggregate plan = PlanAggregate.create(config);
  planRepository.save(plan);
  publishEvents(plan);
}
```

**Why?**
- ✅ Keeps transactions short
- ✅ Avoids holding DB connections
- ✅ Better performance
- ✅ Reduced deadlock risk

---

### Rule 3: Keep Transactions Short

```java
// ❌ BAD: Long transaction with complex logic
@Transactional
public void process() {
  loadData();           // DB query
  complexCalculation(); // 5 seconds CPU
  externalValidation(); // 3 seconds HTTP
  saveResults();        // DB update
}
```

```java
// ✅ GOOD: Short transaction, only DB operations
public void process() {
  // Phase 1: Prepare (NO transaction)
  Data data = loadData();
  Result calculated = complexCalculation(data);
  ValidationResult validated = externalValidation(calculated);

  // Phase 2: Persist (WITH transaction - short!)
  saveResults(validated);
}

@Transactional
private void saveResults(ValidationResult validated) {
  // Only DB writes - fast!
  repository.save(validated);
  outboxPublisher.publish(event);
}
```

**Why?**
- ✅ Minimizes transaction duration
- ✅ Reduces lock contention
- ✅ Better throughput
- ✅ Easier to handle errors

---

## Design Principles

### 1. Single Responsibility

**Each orchestrator should coordinate ONE use case**:

```java
// ✅ GOOD: One use case
class PlanIngestionOrchestrator {
  PlanIngestionResult ingestPlan(PlanIngestionCommand command) { /* ... */ }
}

// ✅ GOOD: Another use case
class OutboxRelayOrchestrator {
  RelayReport relay(OutboxRelayCommand command) { /* ... */ }
}
```

```java
// ❌ BAD: Multiple use cases in one orchestrator
class PlanOrchestrator {
  PlanIngestionResult ingestPlan(/* ... */) { /* ... */ }
  PlanUpdateResult updatePlan(/* ... */) { /* ... */ }
  PlanDeletionResult deletePlan(/* ... */) { /* ... */ }
}
```

---

### 2. Clear Method Names

**Method names should describe WHAT they do**:

```java
// ✅ GOOD: Clear intent
private PlanningContext preparePlanningContext(PlanIngestionCommand request)
private void performPreValidation(PlanningContext context)
private PlanAssemblyResult assembleAndValidatePlan(/* ... */)
private PlanIngestionResult persistAndPublishNewPlan(/* ... */)

// ❌ BAD: Vague or misleading
private PlanningContext prepare(/* ... */)
private void validate(/* ... */)
private PlanAssemblyResult assemble(/* ... */)
private PlanIngestionResult persist(/* ... */)
```

---

### 3. Delegate Business Logic to Domain

```java
// ❌ BAD: Business logic in orchestrator
@Transactional
public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
  // ❌ Business rule in orchestrator
  if (command.windowFrom().isAfter(command.windowTo())) {
    throw new ValidationException("Invalid window");
  }

  PlanAggregate plan = new PlanAggregate(/* ... */);
  planRepository.save(plan);
}
```

```java
// ✅ GOOD: Business logic in domain
@Transactional
public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
  // ✅ Delegate to domain aggregate factory
  PlanAggregate plan = PlanAggregate.create(
      command.scheduleInstanceId(),
      command.planKey(),
      command.provenanceCode(),
      command.operationCode(),
      command.windowFrom(),
      command.windowTo());

  // Domain aggregate validates business rules internally
  planRepository.save(plan);
}
```

```java
// ✅ Domain aggregate with validation
public class PlanAggregate {
  public static PlanAggregate create(/* parameters */) {
    // ✅ Business rule validation in domain
    if (windowFrom.isAfter(windowTo)) {
      throw new IllegalArgumentException("Invalid window: from must be before to");
    }
    return new PlanAggregate(/* ... */);
  }
}
```

---

### 4. Use Ports, NOT Direct Infrastructure

```java
// ❌ BAD: Direct infrastructure dependency
@Service
public class PlanIngestionOrchestrator {
  private final PlanMapper planMapper;  // ❌ Infrastructure type!

  @Transactional
  public void ingestPlan(/* ... */) {
    PlanDO planDO = new PlanDO();  // ❌ Infrastructure type!
    planMapper.insert(planDO);
  }
}
```

```java
// ✅ GOOD: Use domain port
@Service
public class PlanIngestionOrchestrator {
  private final PlanRepository planRepository;  // ✅ Domain port!

  @Transactional
  public void ingestPlan(/* ... */) {
    PlanAggregate plan = PlanAggregate.create(/* ... */);  // ✅ Domain type!
    planRepository.save(plan);  // ✅ Port method!
  }
}
```

---

## Testing Orchestrators

### Unit Tests: Mock Coordinators and Ports

**File**: `patra-ingest-app/src/test/java/com/patra/ingest/app/usecase/plan/PlanIngestionOrchestratorTest.java`

```java
/**
 * Unit tests for PlanIngestionOrchestrator.
 *
 * Mock all coordinators and ports to isolate orchestration logic.
 */
@ExtendWith(MockitoExtension.class)
class PlanIngestionOrchestratorTest {

  @Mock private PlanRepository planRepository;
  @Mock private CursorRepository cursorRepository;
  @Mock private TaskRepository taskRepository;
  @Mock private PatraRegistryPort patraRegistryPort;

  @Mock private PlanningWindowResolver planningWindowResolver;
  @Mock private PlannerValidator plannerValidator;
  @Mock private PlanAssembler planAssembler;
  @Mock private PlanExpressionBuilder planExpressionBuilder;

  @Mock private PlanPersistenceCoordinator persistenceCoordinator;
  @Mock private PlanIdempotencyCoordinator idempotencyCoordinator;
  @Mock private PlanPublishingCoordinator publishingCoordinator;

  @InjectMocks
  private PlanIngestionOrchestrator orchestrator;

  @Test
  void should_create_plan_successfully_when_no_existing_plan() {
    // Given
    PlanIngestionCommand command = createTestCommand();

    when(patraRegistryPort.fetchConfig(any(), any()))
        .thenReturn(createTestConfig());
    when(cursorRepository.findLatestGlobalTimeWatermark(any(), any()))
        .thenReturn(Optional.of(Instant.now()));
    when(planningWindowResolver.resolveWindow(any(), any(), any(), any()))
        .thenReturn(createTestWindow());
    when(planAssembler.assemble(any()))
        .thenReturn(createTestAssembly());
    when(planRepository.findByPlanKey(any()))
        .thenReturn(Optional.empty());  // No existing plan
    when(persistenceCoordinator.savePlan(any()))
        .thenReturn(createTestPlan());
    when(persistenceCoordinator.persistSlices(any(), any()))
        .thenReturn(List.of(createTestSlice()));
    when(persistenceCoordinator.persistTasks(any(), any(), any()))
        .thenReturn(List.of(createTestTask()));
    when(publishingCoordinator.collectQueuedEvents(any()))
        .thenReturn(List.of(createTestEvent()));

    // When
    PlanIngestionResult result = orchestrator.ingestPlan(command);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.planId()).isNotNull();

    // Verify coordinator interactions
    verify(persistenceCoordinator).savePlan(any());
    verify(persistenceCoordinator).persistSlices(any(), any());
    verify(persistenceCoordinator).persistTasks(any(), any(), any());
    verify(publishingCoordinator).publishNewPlanEvents(any(), any(), any());
    verifyNoInteractions(idempotencyCoordinator);
  }

  @Test
  void should_reuse_existing_plan_when_planKey_exists() {
    // Given
    PlanIngestionCommand command = createTestCommand();
    PlanAggregate existingPlan = createTestPlan();

    when(patraRegistryPort.fetchConfig(any(), any()))
        .thenReturn(createTestConfig());
    when(cursorRepository.findLatestGlobalTimeWatermark(any(), any()))
        .thenReturn(Optional.of(Instant.now()));
    when(planningWindowResolver.resolveWindow(any(), any(), any(), any()))
        .thenReturn(createTestWindow());
    when(planAssembler.assemble(any()))
        .thenReturn(createTestAssembly());
    when(planRepository.findByPlanKey(any()))
        .thenReturn(Optional.of(existingPlan));  // Existing plan found!
    when(idempotencyCoordinator.handleIdempotentPlanReuse(any(), any(), any()))
        .thenReturn(createTestResult());

    // When
    PlanIngestionResult result = orchestrator.ingestPlan(command);

    // Then
    assertThat(result).isNotNull();

    // Verify idempotency coordinator called
    verify(idempotencyCoordinator).handleIdempotentPlanReuse(
        eq(existingPlan), any(), any());

    // Verify persistence coordinators NOT called
    verifyNoInteractions(persistenceCoordinator);
    verify(publishingCoordinator, never()).publishNewPlanEvents(any(), any(), any());
  }
}
```

**Key Takeaways**:
- ✅ Mock all dependencies (coordinators + ports)
- ✅ Test orchestration logic only
- ✅ Verify coordinator interactions
- ✅ Test different paths (new plan vs existing plan)
- ✅ Fast tests (no DB, no Spring context)

---

**相关文件：**
- [SKILL.md](../SKILL.md) - Main guide
- [complete-examples.md](complete-examples.md) - Complete feature examples
- [domain-modeling-patterns.md](domain-modeling-patterns.md) - Domain layer patterns
- [transaction-error-handling.md](transaction-error-handling.md) - Transaction management
- [testing-guide.md](testing-guide.md) - Testing strategies

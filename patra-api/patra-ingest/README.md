# patra-ingest — 数据摄入编排服务

> **数据采集编排器**,将摄入任务分解为可执行的原子任务并管理其生命周期。

---

## 📌 核心职责

`patra-ingest` 负责:

1. **计划编排**: 根据调度器触发器创建执行计划
2. **任务生成**: 将计划分解为原子任务(带幂等性保证)
3. **窗口解析**: 确定数据采集的时间/容量窗口
4. **游标跟踪**: 维护增量采集的水位线
5. **Outbox 中继**: 通过 Outbox 模式可靠地发布任务事件
6. **执行协调**: 跟踪任务状态、重试和租约管理

**核心原则**: 确保**至少一次交付**和**幂等任务执行**。

---

## 🏗️ 模块结构

```
patra-ingest/
├─ patra-ingest-api/                # 外部契约(API层)
│  └─ src/main/java/.../api/
│     └─ (未来: 任务工作者 APIs)
│
├─ patra-ingest-domain/             # 纯 Java 领域模型(Domain层)
│  └─ src/main/java/.../domain/
│     ├─ model/
│     │  ├─ aggregate/              # 核心聚合根
│     │  │  ├─ PlanAggregate.java       # 计划蓝图 + 状态机
│     │  │  ├─ TaskAggregate.java       # 任务(带租约 + 执行时间线)
│     │  │  ├─ PlanSliceAggregate.java  # 计划切片(中间分组)
│     │  │  └─ ScheduleInstanceAggregate.java  # 调度运行跟踪
│     │  ├─ vo/                     # 值对象
│     │  │  ├─ WindowSpec.java          # 窗口规范(TIME/CURSOR/...)
│     │  │  ├─ Batch.java               # 批次定义
│     │  │  ├─ CursorWatermark.java     # 水位线跟踪
│     │  │  └─ ExecutionContext.java    # 任务执行上下文
│     │  ├─ entity/                 # 领域实体
│     │  │  └─ OutboxMessage.java       # Outbox 消息实体
│     │  ├─ enums/                  # 领域枚举
│     │  │  ├─ PlanStatus.java          # DRAFT/SLICING/READY/COMPLETED/FAILED
│     │  │  ├─ TaskStatus.java          # QUEUED/RUNNING/SUCCEEDED/FAILED/...
│     │  │  └─ OperationCode.java       # HARVEST/UPDATE/COMPENSATION
│     │  └─ snapshot/               # 配置快照
│     │     └─ ProvenanceConfigSnapshot.java
│     ├─ port/                      # 仓储端口
│     │  ├─ PlanRepository.java
│     │  ├─ TaskRepository.java
│     │  ├─ CursorRepository.java
│     │  ├─ OutboxRepository.java
│     │  └─ PatraRegistryPort.java      # 外部服务端口
│     ├─ event/                     # 领域事件
│     │  └─ TaskQueuedEvent.java
│     ├─ policy/                    # 领域策略
│     ├─ exception/                 # 领域异常
│     └─ messaging/                 # 消息契约
│
├─ patra-ingest-app/                # 应用层(Application层 - 编排)
│  └─ src/main/java/.../app/
│     └─ usecase/
│        ├─ plan/                   # 计划摄入用例
│        │  ├─ PlanIngestionOrchestrator.java    # 主编排器
│        │  ├─ PlanIngestionCommand.java         # 输入命令
│        │  ├─ PlanIngestionResult.java          # 输出结果
│        │  ├─ assembler/                        # 计划装配逻辑
│        │  │  └─ PlanAssembler.java
│        │  ├─ expression/                       # 表达式构建
│        │  │  └─ PlanExpressionBuilder.java
│        │  ├─ window/                           # 窗口解析
│        │  │  └─ PlanningWindowResolver.java
│        │  ├─ validator/                        # 预验证
│        │  │  └─ PlannerValidator.java
│        │  └─ publisher/                        # 事件发布
│        │     └─ TaskOutboxPublisher.java
│        └─ relay/                  # Outbox 中继用例
│           └─ OutboxRelayOrchestrator.java
│
├─ patra-ingest-infra/              # 基础设施层(Infrastructure层)
│  └─ src/main/java/.../infra/
│     ├─ persistence/
│     │  ├─ entity/                 # MyBatis-Plus DOs
│     │  │  ├─ IngestPlanDO.java
│     │  │  ├─ IngestTaskDO.java
│     │  │  ├─ IngestCursorDO.java
│     │  │  └─ IngestOutboxMessageDO.java
│     │  ├─ mapper/                 # MyBatis mappers
│     │  ├─ converter/              # DO ↔ Domain 转换器
│     │  └─ repository/             # 仓储实现
│     │     ├─ PlanRepositoryMpImpl.java
│     │     ├─ TaskRepositoryMpImpl.java
│     │     ├─ CursorRepositoryMpImpl.java
│     │     └─ OutboxRepositoryMpImpl.java
│     └─ rpc/                       # 外部服务适配器
│        └─ PatraRegistryPortImpl.java  # 通过 Feign 调用 patra-registry
│
├─ patra-ingest-adapter/            # 适配器层(Adapter层)
│  └─ src/main/java/.../adapter/
│     ├─ inbound/
│     │  ├─ scheduler/              # 定时任务调度器
│     │  │  └─ PlanScheduler.java       # Cron 触发的规划
│     │  └─ mq/                     # MQ 监听器(未来)
│     └─ config/                    # 错误映射、链路追踪
│
└─ patra-ingest-boot/               # 可执行模块
   └─ src/main/java/.../
      └─ PatraIngestApplication.java
```

---

## 🔑 核心领域概念

### 1. Plan (计划聚合根)

**定义**: 数据采集任务的蓝图,包含窗口规范、表达式快照和配置快照。

**状态机**:
```
DRAFT(草稿) → SLICING(切片中) → READY(就绪) → COMPLETED(已完成)
                                   ↓
                               PARTIAL(部分完成) → FAILED(失败)
```

**核心属性**:
- `planKey` (String): 幂等键 = hash(provenance + operation + window + strategy)
- `provenanceCode` (String): 来源代码(例如: `"pubmed"`)
- `operationCode` (OperationCode): HARVEST/UPDATE/COMPENSATION
- `windowSpec` (WindowSpec): 窗口边界(TIME/DATE/CURSOR/VOLUME/SINGLE)
- `sliceStrategyCode` (String): 如何将计划分解为切片(例如: `"TIME"`, `"DATE"`, `"SINGLE"`)
- `exprProtoSnapshotJson` (String): 捕获的表达式原型
- `provenanceConfigSnapshotJson` (String): 捕获的配置快照

**文件**: [`PlanAggregate.java`](patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/aggregate/PlanAggregate.java:1)

### 2. Task (任务聚合根)

**定义**: 数据采集的原子工作单元(例如:获取 PubMed 记录 1-1000)。

**状态机**:
```
QUEUED(排队) → RUNNING(运行中) → SUCCEEDED(成功)
                  ↓
              FAILED(失败) → (重试) → QUEUED(排队)
                  ↓
             CANCELLED(已取消)
```

**核心属性**:
- `idempotentKey` (String): 业务幂等键
- `provenanceCode` (String): 来源代码
- `operationCode` (String): 操作类型
- `paramsJson` (String): 任务参数(JSON)
- `priority` (Integer): 执行优先级
- `scheduledAt` (Instant): 执行时间
- `leaseInfo` (LeaseInfo): 租约所有权(owner, leasedUntil)
- `executionTimeline` (ExecutionTimeline): 开始/结束时间戳
- `retryCount` (Integer): 已尝试的重试次数

**文件**: [`TaskAggregate.java`](patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/aggregate/TaskAggregate.java:1)

### 3. WindowSpec (Sealed Interface)

**Definition**: Specification for collection window boundaries.

**Strategies**:
```java
public sealed interface WindowSpec {
    record Time(Instant from, Instant to) implements WindowSpec {}
    record IdRange(Long minId, Long maxId) implements WindowSpec {}
    record CursorLandmark(String cursorValue) implements WindowSpec {}
    record VolumeBudget(Integer maxRecords) implements WindowSpec {}
    record Single() implements WindowSpec {}  // No windowing
}
```

**File**: [`WindowSpec.java`](patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/vo/WindowSpec.java)

### 4. Cursor Watermark

**Definition**: Tracks progress of incremental collection (high-water mark).

**Types**:
- **Global Time Watermark**: Latest `collectedUntil` timestamp across all tasks
- **Cursor Value Watermark**: Last pagination cursor (for cursor-based APIs)

**File**: [`CursorWatermark.java`](patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/vo/CursorWatermark.java)

### 5. Outbox Pattern

**Components**:
1. **TaskQueuedEvent**: Domain event raised when task is created
2. **OutboxMessage**: Persistent event record (JSON payload)
3. **OutboxRelayOrchestrator**: Polls outbox table, publishes to MQ, marks as sent

**Guarantees**:
- **At-least-once delivery** (same transaction as task persistence)
- **Ordering** (via sequence number)
- **Idempotency** (consumer must deduplicate)

**File**: [`OutboxMessage.java`](patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/entity/OutboxMessage.java)

---

## 🔄 Plan Ingestion Flow

### High-Level Sequence

```
1. Scheduler triggers (cron or manual)
   ↓
2. PlanIngestionOrchestrator.ingestPlan(command)
   ↓
3. Phase 1: Persist schedule instance + load provenance config
   ↓
4. Phase 2: Query cursor watermark + resolve planning window
   ↓
5. Phase 3: Build plan expression (uncompiled snapshot)
   ↓
6. Phase 4: Pre-validation (window sanity, backpressure, capacity)
   ↓
7. Phase 5: Assemble plan/slices/tasks (with idempotency check)
   ↓
8. Phase 6: Persist plan → slices → tasks (in transaction)
   ↓
9. Phase 7: Collect TaskQueuedEvents → publish to Outbox
   ↓
10. Outbox relay picks up messages → publishes to MQ
   ↓
11. Task workers consume from MQ → execute → update task status
```

### Code Entry Point

[`PlanIngestionOrchestrator.ingestPlan()`](patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/plan/PlanIngestionOrchestrator.java:133)

### Key Phases Explained

#### Phase 1: Load Configuration

```java
// Persist schedule instance (idempotent)
ScheduleInstanceAggregate schedule = scheduleInstanceRepository.saveOrUpdateInstance(...);

// Fetch provenance config snapshot from registry
ProvenanceConfigSnapshot configSnapshot = patraRegistryPort.fetchConfig(
    provenanceCode, operationCode
);
```

**Purpose**: Capture config at plan creation time (immutable snapshot).

#### Phase 2: Window Resolution

```java
// Query latest watermark
Instant cursorWatermark = cursorRepository.findLatestGlobalTimeWatermark(...);

// Resolve window based on trigger params + config + cursor
PlannerWindow window = planningWindowResolver.resolveWindow(
    triggerNorm, configSnapshot, cursorWatermark, Instant.now()
);
```

**Example**:
- Trigger says: `windowFrom=2025-01-01`, `windowTo=2025-01-10`
- Cursor watermark: `2025-01-05`
- Resolved window: `[2025-01-05, 2025-01-10)` (start from cursor)

#### Phase 3: Expression Building

```java
// Build plan expression descriptor (hash + JSON snapshot)
PlanExpressionDescriptor expressionDescriptor = planExpressionBuilder.build(
    triggerNorm, configSnapshot
);
```

**Purpose**: Capture expression prototype before compilation (stored in plan).

#### Phase 4: Pre-Validation

```java
// Count queued tasks (backpressure check)
long queuedTasks = taskRepository.countQueuedTasks(provenanceCode, operationCode);

// Validate window, capacity, queue pressure
plannerValidator.validateBeforeAssemble(
    triggerNorm, configSnapshot, window, queuedTasks
);
```

**Checks**:
- Window not empty
- Window not too large (max range)
- Queue capacity not exceeded

#### Phase 5: Plan Assembly

```java
// Assemble plan/slices/tasks (in-memory, not persisted yet)
PlanAssemblyResult assembly = planAssembler.assemble(assemblyRequest);

// Check idempotency: does planKey already exist?
PlanAggregate existingPlan = planRepository.findByPlanKey(draftPlan.getPlanKey());
if (existingPlan != null) {
    // Reuse existing plan, retry failed tasks
    ...
}
```

**Idempotency**:
- `planKey` = hash(provenance + operation + window + strategy)
- If duplicate, skip creation and retry failed tasks

#### Phase 6: Persistence

```java
// Persist plan
PlanAggregate persistedPlan = planRepository.save(draftPlan);

// Persist slices (bind to plan)
List<PlanSliceAggregate> persistedSlices = planSliceRepository.saveAll(slices);

// Persist tasks (bind to plan + slices)
List<TaskAggregate> persistedTasks = taskRepository.saveAll(tasks);
```

**Transaction**: All 3 steps are in the same transaction.

#### Phase 7: Event Publishing

```java
// Collect domain events from tasks
List<TaskQueuedEvent> queuedEvents = collectQueuedEvents(persistedTasks);

// Publish to Outbox (in same transaction)
taskOutboxPublisher.publish(queuedEvents, persistedPlan, schedule);
```

**Outbox**: Converts events → `OutboxMessage` → persists to `ingest_outbox_message` table.

---

## 🔌 Cross-Module Integration

### Expression Compiler Integration (Adapter Binding)

`patra-ingest` compiles expressions at execution time and binds the compiled output (provider‑named params) directly into provider request models. No manual string concatenation.

Flow:
- `ExecutionContextLoader` restores snapshots (Task → Slice → Plan) and compiles the expression via `ExpressionCompilerPort`.
- The compiled result provides:
  - `compiledQuery` — aggregated boolean query string (bridged via `std_key=query`)
  - `compiledParams` — provider‑named params map (e.g., PubMed `mindate/maxdate/datetype`, Crossref `query/filter`)
- Provider adapters/assemblers read only from `compiledParams` (and `compiledQuery` when appropriate).

Example (PubMed count planning):

```java
// ExecutionContextLoaderImpl.loadContext(...) builds the context
return new ExecutionContext(
    taskId, runId, task.getProvenanceCode(), task.getOperationCode(),
    configSnapshot,
    task.getExprHash(),
    compilationResult.query(),        // compiledQuery (bridged via std_key=query)
    compilationResult.params(),       // compiledParams (provider-named)
    compilationResult.normalizedExpression(),
    windowSpec);

// PubmedBatchPlanner uses compiled outputs
PlanMetadata metadata =
    searchPort.preparePlanMetadata(compiledQuery, compiledParams, configSnapshot);
int total = metadata.totalCount();

// Infra adapter builds request from provider-named params only
ESearchRequest request = PubMedESearchRequestAssembler.buildList(compiledParams);
```

Rules of engagement:
- Do not reconstruct `query`/`filter` in adapters — always use compiler output.
- MULTI std_keys use join transforms by default. Repeated params require enabling `expr.multi.repeat-enabled=true` (not recommended by default).
- Respect STRICT mode in prod (`expr.strict=true`) to catch incomplete seed configs early.

### Calling patra-registry

**Port Interface**: [`PatraRegistryPort`](patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/PatraRegistryPort.java)

**Implementation**: [`PatraRegistryPortImpl`](patra-ingest-infra/src/main/java/com/patra/ingest/infra/rpc/PatraRegistryPortImpl.java)

**Example**:
```java
@Component
@RequiredArgsConstructor
public class PatraRegistryPortImpl implements PatraRegistryPort {

    private final ProvenanceClient provenanceClient;  // Feign client

    @Override
    public ProvenanceConfigSnapshot fetchConfig(ProvenanceCode code, OperationCode operation) {
        ProvenanceConfigResp resp = provenanceClient.getConfiguration(
            code,
            operation.getCode(),
            Instant.now()
        );
        return convertToSnapshot(resp);
    }
}
```

**Dependency**: `patra-registry-api` (Feign client interface).

---

## 🛠️ How to Extend

### Adding a New Slicing Strategy

**Example**: Add `ID_RANGE` slicing (split by ID ranges instead of time).

#### Step 1: Define Slicer

```java
// app/usecase/plan/slicer/IdRangeSlicingStrategy.java
@Component
public class IdRangeSlicingStrategy implements SlicingStrategy {

    @Override
    public boolean supports(String strategyCode) {
        return "ID_RANGE".equalsIgnoreCase(strategyCode);
    }

    @Override
    public List<SliceSpec> slice(PlannerWindow window, SliceParamsJson params) {
        // Parse window as WindowSpec.IdRange
        // Split into ranges: [1, 1000], [1001, 2000], ...
        // Return list of SliceSpec
    }
}
```

#### Step 2: Register in PlanAssembler

```java
// PlanAssembler.java
private final List<SlicingStrategy> slicingStrategies;  // Auto-wired

public PlanAssemblyResult assemble(PlanAssemblyRequest request) {
    SlicingStrategy strategy = slicingStrategies.stream()
        .filter(s -> s.supports(request.sliceStrategyCode()))
        .findFirst()
        .orElseThrow(() -> new PlanAssemblyException("Unsupported strategy: " + request.sliceStrategyCode()));

    List<SliceSpec> slices = strategy.slice(request.window(), request.sliceParams());
    // ...
}
```

#### Step 3: Update WindowSpec

```java
// WindowSpec.java
public sealed interface WindowSpec {
    record Time(Instant from, Instant to) implements WindowSpec {}
    record IdRange(Long minId, Long maxId) implements WindowSpec {}  // Use this
    // ...
}
```

### Adding a New Task Status

**Example**: Add `SKIPPED` status for tasks that don't need execution.

#### Step 1: Add to Enum

```java
// domain/model/enums/TaskStatus.java
public enum TaskStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    PARTIAL,
    CURSOR_PENDING,
    CANCELLED,
    SKIPPED  // NEW
}
```

#### Step 2: Add Aggregate Method

```java
// TaskAggregate.java
public void markSkipped(String reason) {
    this.status = TaskStatus.SKIPPED;
    this.lastErrorMsg = "Skipped: " + reason;
}
```

#### Step 3: Update State Machine Logic

```java
// After refactoring: Only FAILED tasks are retried
// Note: CANCELLED status removed (cancellation not supported in current design)
private boolean shouldRetry(TaskAggregate task) {
    TaskStatus status = task.getStatus();
    return status == TaskStatus.FAILED;
}
```

---

## 🗄️ Database Schema Overview

### Tables

| Table | Purpose |
|-------|---------|
| `ingest_schedule_instance` | Scheduler run tracking (idempotency) |
| `ingest_plan` | Plan blueprints |
| `ingest_plan_slice` | Plan slices (intermediate grouping) |
| `ingest_task` | Atomic tasks |
| `ingest_cursor` | Watermark tracking (global + slice-level) |
| `ingest_outbox_message` | Outbox pattern event queue |

**Key Relationships**:
- `ingest_plan.schedule_instance_id` → `ingest_schedule_instance.id`
- `ingest_plan_slice.plan_id` → `ingest_plan.id`
- `ingest_task.plan_id` → `ingest_plan.id`
- `ingest_task.slice_id` → `ingest_plan_slice.id`

**Indexes**:
- `idx_plan_key` on `ingest_plan(plan_key)` (idempotency)
- `idx_task_idempotent_key` on `ingest_task(idempotent_key)` (idempotency)
- `idx_task_status_scheduled` on `ingest_task(status, scheduled_at)` (worker polling)
- `idx_outbox_status_seq` on `ingest_outbox_message(status, seq)` (relay polling)

---

## 🧪 Testing

### Unit Tests (Domain)

```bash
mvn test -pl patra-ingest-domain
```

**Focus**: Aggregate state machines, window resolution logic.

### Integration Tests (Orchestrator)

```bash
mvn verify -pl patra-ingest-app
```

**Focus**: End-to-end plan ingestion, idempotency checks.

### Repository Tests (Infra)

```bash
mvn verify -pl patra-ingest-infra
```

**Focus**: MyBatis queries, cursor watermark updates.

---

## 📊 Observability

### Logs

- **INFO**: Major milestones (e.g., "Plan ingestion success, planId=123, taskCount=50")
- **DEBUG**: Diagnostic details (e.g., "Window resolved: [2025-01-01, 2025-01-10)")
- **ERROR**: Failures (e.g., "Plan assembly failed: WINDOW_INVALID")

### 🪵 Logging (Starter v1.0)

`patra-ingest` uses Spring Boot default logging; tracing is provided by SkyWalking agent.

Dependency (already included):
```xml
<dependency>
  <groupId>com.papertrace</groupId>
  <!-- logging handled by defaults -->
</dependency>
```

Batch orchestration example (correlationId + MDC):
```java
@Slf4j
@Service
public class PlanIngestionOrchestrator {
  @Autowired TraceContextHolder traceContextHolder;

  public void ingestPlan(String planKey) {
    var ctx = traceContextHolder.withCorrelationId(planKey);
    traceContextHolder.populateMDC(ctx);
    try {
      log.info("Plan ingestion started: planKey={}", planKey);
      // ...
      log.info("Plan ingestion completed: planKey={}", planKey);
    } finally {
      traceContextHolder.clearMDC();
    }
  }
}
```

Dynamic levels (Nacos `logging-patra-ingest.yml`):
```yaml
logging.level:
  root: INFO
  com.patra.ingest.app: DEBUG
  com.patra.ingest.infra: DEBUG
```

More examples: docs/logging/layer-specific-examples.md, specs/001-logging-starter/quickstart.md

### Metrics

**Dependencies**: `spring-boot-starter-actuator` is included in `patra-ingest-boot` for Micrometer metrics support.

**Outbox Metrics** (published by `OutboxMetrics`):
- `papertrace.outbox.publish.total` (Counter) — Total publish attempts with `aggregateType`, `opType`, `status` tags
- `papertrace.outbox.publish.duration` (Timer) — Publish operation duration
- `papertrace.outbox.publish.batch.size` (DistributionSummary) — Batch size distribution

**Planned Metrics**:
- `plan.ingestion.duration` (histogram)
- `task.queue.size` (gauge)
- `cursor.watermark.lag` (gauge) — Time between now and watermark
- `outbox.relay.lag` (gauge) — Number of pending outbox messages

**Access metrics**:
```bash
# Health check
curl http://localhost:8082/actuator/health

# View all metrics
curl http://localhost:8082/actuator/metrics

# View specific outbox metrics
curl http://localhost:8082/actuator/metrics/papertrace.outbox.publish.total
```

---

## 🚀 Running Locally

```bash
# Start MySQL + MQ
docker-compose up -d

# Run migrations
# ...

# Start service
cd patra-ingest/patra-ingest-boot
mvn spring-boot:run
```

**Default Port**: 8082

---

## 🔗 Related Documentation

- [Main README](../README.md)
- [Architecture Guide](../docs/ARCHITECTURE.md)
- [Development Guide](../docs/DEV-GUIDE.md)
- [patra-registry README](../patra-registry/README.md)

---

**Last Updated**: 2025-01-14

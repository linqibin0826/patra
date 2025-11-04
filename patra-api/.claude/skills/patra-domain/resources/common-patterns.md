# 通用模式

## 概述

Patra 中使用的常见架构和设计模式,基于六边形架构 + DDD 原则。

---

## 1. 组装器模式(复杂对象图创建)

### 目的
编排涉及多个聚合和依赖的复杂对象图创建。

### 实现: PlanAssembler

**位置**: `patra-ingest-app/.../plan/PlanAssemblerImpl.java`

**职责**:
1. 从 Registry 获取配置和表达式
2. 创建 PlanAggregate
3. 调用 SlicePlanner 生成 PlanSliceAggregates
4. 创建 TaskAggregates(与切片 1:1)
5. 事务性持久化所有聚合
6. 发出领域事件(TaskQueuedEvent)

**关键方法**:
```java
PlanAssemblyResult assemble(PlanAssemblyContext context)
  ↓
1. fetchConfigSnapshot(provenanceCode, operationCode)
2. fetchExprSnapshot(provenanceCode, operationCode)
3. PlanAggregate.create(...)
4. slicePlanner.slice(context) → SlicePlanningResult
5. createTasks(slices) → List<TaskAggregate>
6. persist(plan, slices, tasks) (事务性)
7. publishEvents(tasks)
```

**优势**:
- 复杂编排的单一职责
- 跨多个聚合的事务一致性
- 集中的依赖管理

---

## 2. 注册表模式(策略选择)

### 目的
根据输入参数选择适当的策略实现。

### 实现: SlicePlannerRegistry

**位置**: `patra-ingest-app/.../plan/SlicePlannerRegistry.java`

**结构**:
```java
@Component
public class SlicePlannerRegistry {
    private final Map<SliceStrategy, SlicePlanner> planners;

    public SlicePlannerRegistry(
        TimeSlicePlanner timePlanner,
        DateSlicePlanner datePlanner,
        SingleSlicePlanner singlePlanner
    ) {
        planners = Map.of(
            SliceStrategy.TIME, timePlanner,
            SliceStrategy.DATE, datePlanner,
            SliceStrategy.SINGLE, singlePlanner
        );
    }

    public SlicePlanner get(SliceStrategy strategy) {
        return planners.get(strategy);
    }
}
```

**使用方式**:
```java
SliceStrategy strategy = determineStrategy(operation, provenance);
SlicePlanner planner = slicePlannerRegistry.get(strategy);
SlicePlanningResult result = planner.slice(context);
```

**优势**:
- 集中的策略选择
- 易于添加新策略
- 类型安全的策略解析

---

## 3. 快照转换器模式

### 目的
将外部 API 响应转换为不可变的领域快照。

### 实现: ProvenanceConfigSnapshotConverter

**位置**: `patra-ingest-infra/.../converter/ProvenanceConfigSnapshotConverter.java`

**流程**:
```
ProvenanceConfigResp (API DTO)
  ↓ convert()
ProvenanceConfigSnapshot (领域 VO)
```

**转换逻辑**:
```java
@Component
public class ProvenanceConfigSnapshotConverter {
    public ProvenanceConfigSnapshot convert(ProvenanceConfigResp resp) {
        return new ProvenanceConfigSnapshot(
            convertWindowOffset(resp.windowOffset()),
            convertPagination(resp.pagination()),
            convertHttp(resp.http()),
            convertBatching(resp.batching()),
            convertRetry(resp.retry()),
            convertRateLimit(resp.rateLimit()),
            convertCircuitBreaker(resp.circuitBreaker())
        );
    }
}
```

**优势**:
- 与外部 API 变更隔离
- 领域特定的值对象
- 集中的转换逻辑

---

## 4. 规范化器模式(确定性序列化)

### 目的
为哈希和比较生成确定性的规范表示。

### 实现: ExprCanonicalizer

**位置**: `patra-expr-kernel/.../ExprCanonicalizer.java`

**目的**:
- 将表达式对象转换为规范 JSON
- 确保一致的字段顺序
- 移除空格/格式变化
- 生成稳定的哈希值

**使用方式**:
```java
ExprSnapshot expr = loadFromRegistry();
String canonicalJson = exprCanonicalizer.canonicalize(expr);
String hash = sha256(canonicalJson);
```

**优势**:
- 幂等性的稳定哈希
- 变更检测准确性
- 缓存键生成

---

## 5. Outbox 模式(事务性消息)

### 目的
通过事务性 outbox 确保可靠的事件发布。

### 实现

**Outbox 表**: `ing_outbox_message`

**流程**:
```
1. 业务事务:
   - TaskAggregate.create(...)
   - task.addDomainEvent(new TaskQueuedEvent(...))
   - taskRepository.save(task)
   - outboxRepository.save(extractOutboxMessage(task))
   [COMMIT]

2. Outbox 中继(独立进程):
   - SELECT * FROM ing_outbox_message WHERE status='PENDING'
   - 发布到 MQ (RabbitMQ/Kafka)
   - UPDATE status='PUBLISHED'
```

**领域事件发射**:
```java
public class TaskAggregate extends AggregateRoot<Long> {
    public static TaskAggregate create(...) {
        TaskAggregate task = new TaskAggregate(...);
        task.addDomainEvent(new TaskQueuedEvent(task.getId()));
        return task;
    }
}
```

**Outbox 提取**:
```java
public class OutboxMessageExtractor {
    public List<OutboxMessage> extract(AggregateRoot<?> aggregate) {
        return aggregate.getDomainEvents().stream()
            .map(this::convertToOutboxMessage)
            .collect(toList());
    }
}
```

**优势**:
- 保证事件交付
- 事务一致性
- 至少一次交付语义

---

## 6. 端口-适配器模式(六边形架构)

### 目的
将领域逻辑与外部依赖解耦。

### 实现: PatraRegistryPort

**领域端口** (`patra-ingest-domain`):
```java
public interface PatraRegistryPort {
    ProvenanceConfigSnapshot fetchConfig(
        String provenanceCode,
        String operationCode
    );

    ExprSnapshot fetchExprSnapshot(
        String provenanceCode,
        String operationCode,
        Instant at
    );
}
```

**基础设施适配器** (`patra-ingest-infra`):
```java
@Component
public class PatraRegistryAdapter implements PatraRegistryPort {
    private final ProvenanceClient provenanceClient;
    private final ExprClient exprClient;
    private final ProvenanceConfigSnapshotConverter configConverter;
    private final ExprSnapshotConverter exprConverter;

    @Override
    public ProvenanceConfigSnapshot fetchConfig(...) {
        ProvenanceConfigResp resp = provenanceClient.getConfiguration(...);
        return configConverter.convert(resp);
    }

    @Override
    public ExprSnapshot fetchExprSnapshot(...) {
        ExprSnapshotResp resp = exprClient.getSnapshot(...);
        return exprConverter.convert(resp);
    }
}
```

**优势**:
- 领域独立性
- 可测试性(模拟端口)
- 技术灵活性

---

## 7. Aggregate Root Pattern (DDD)

### Purpose
Enforce consistency boundaries and transactional integrity.

### Key Principles

**Identity**:
```java
public abstract class AggregateRoot<ID> {
    protected ID id;
    protected List<DomainEvent> domainEvents = new ArrayList<>();

    public ID getId() { return id; }

    protected void addDomainEvent(DomainEvent event) {
        domainEvents.add(event);
    }
}
```

**Aggregate Examples**:
- **PlanAggregate**: Owns PlanSliceAggregates (composition)
- **TaskAggregate**: Owns TaskRun entities (composition)
- **PlanSliceAggregate**: Independent aggregate (referenced by Task)

**Consistency Rules**:
1. Only aggregate roots have repositories
2. External references use IDs (not object references)
3. Transactions don't span multiple aggregates

---

## 8. Value Object Pattern (DDD)

### Purpose
Represent domain concepts without identity.

### Examples

**WindowSpec** (sealed interface with variants):
```java
public sealed interface WindowSpec permits
    Time, IdRange, CursorLandmark, VolumeBudget, Single
{
    record Time(Instant from, Instant to) implements WindowSpec {}
    record IdRange(Long from, Long to) implements WindowSpec {}
    // ...
}
```

**LeaseInfo**:
```java
public record LeaseInfo(
    String leaseOwner,
    Instant leasedUntil
) {
    public boolean isExpired() {
        return leasedUntil != null && Instant.now().isAfter(leasedUntil);
    }
}
```

**Characteristics**:
- Immutable (use `record`)
- No identity
- Value-based equality
- Self-validating (compact constructor)

---

## 9. Repository Pattern (Persistence Abstraction)

### Purpose
Abstract persistence logic from domain.

### Structure

**Domain Interface** (`patra-ingest-domain`):
```java
public interface PlanRepository {
    PlanAggregate save(PlanAggregate plan);
    Optional<PlanAggregate> findById(Long id);
    Optional<PlanAggregate> findByPlanKey(String planKey);
}
```

**Infrastructure Implementation** (`patra-ingest-infra`):
```java
@Repository
public class PlanRepositoryImpl implements PlanRepository {
    private final PlanMapper planMapper;  // MyBatis-Plus

    @Override
    public PlanAggregate save(PlanAggregate plan) {
        PlanPO po = toDataModel(plan);
        planMapper.insert(po);
        return toDomain(po);
    }
}
```

**Benefits**:
- Persistence technology independence
- Domain-centric interface
- Testability with in-memory implementations

---

## 10. MapStruct Conversion Pattern

### Purpose
Type-safe object mapping between layers.

### Usage

**Domain ↔ Persistence**:
```java
@Mapper(componentModel = "spring")
public interface PlanEntityMapper {
    PlanAggregate toDomain(PlanPO po);
    PlanPO toDataModel(PlanAggregate domain);
}
```

**API ↔ Domain**:
```java
@Mapper(componentModel = "spring")
public interface ProvenanceConfigMapper {
    ProvenanceConfigSnapshot toSnapshot(ProvenanceConfigResp resp);
}
```

**Benefits**:
- Compile-time type safety
- No reflection overhead
- Explicit mapping logic

---

## Best Practices

### 1. Use Assemblers for Complex Orchestration
**Good**: PlanAssembler orchestrates Plan + Slices + Tasks creation
**Bad**: Controller creates aggregates directly

### 2. Use Registries for Strategy Selection
**Good**: SlicePlannerRegistry selects strategy
**Bad**: if-else chains in service layer

### 3. Use Outbox for Event Publishing
**Good**: Transactional outbox guarantees delivery
**Bad**: Direct MQ publish (may lose events)

### 4. Use Ports for External Dependencies
**Good**: PatraRegistryPort abstracts registry client
**Bad**: Direct Feign client injection in domain

### 5. Use Canonical Forms for Hashing
**Good**: ExprCanonicalizer ensures deterministic JSON
**Bad**: Direct `JSON.stringify()` (unstable field order)

---

## Anti-Patterns to Avoid

### ❌ Anemic Domain Models
**Bad**:
```java
public class Task {
    public Long id;
    public String status;  // Public fields, no behavior
}
```

**Good**:
```java
public class TaskAggregate {
    private String statusCode;

    public void markAsSucceeded() {
        validateStateTransition(StatusCode.SUCCEEDED);
        this.statusCode = StatusCode.SUCCEEDED.name();
        addDomainEvent(new TaskSucceededEvent(id));
    }
}
```

### ❌ Transaction Spanning Multiple Aggregates
**Bad**:
```java
@Transactional
public void createPlanAndExecuteTasks(...) {
    Plan plan = planRepository.save(...);
    List<Task> tasks = createTasks(plan);
    taskRepository.saveAll(tasks);
    taskExecutor.executeAll(tasks);  // Long transaction!
}
```

**Good**:
```java
@Transactional
public void createPlan(...) {
    // Only persist aggregates
    planRepository.save(plan);
    sliceRepository.saveAll(slices);
    taskRepository.saveAll(tasks);
    outboxRepository.saveAll(events);
}
// Execution happens asynchronously via MQ
```

### ❌ Direct External API Calls from Domain
**Bad**:
```java
public class TaskAggregate {
    @Autowired
    private PubMedClient pubmedClient;  // Violation!
}
```

**Good**:
```java
// Domain defines port
public interface ProviderApiPort {
    ApiResponse fetch(ApiRequest request);
}

// Infra implements adapter
@Component
public class PubMedApiAdapter implements ProviderApiPort { ... }
```

---

## Summary

**Key Patterns**:
1. **Assembler**: Complex object graph creation (PlanAssembler)
2. **Registry**: Strategy selection (SlicePlannerRegistry)
3. **Snapshot Converter**: API ↔ Domain conversion
4. **Canonicalizer**: Deterministic serialization
5. **Outbox**: Transactional messaging
6. **Port-Adapter**: Hexagonal architecture
7. **Aggregate Root**: Consistency boundaries
8. **Value Object**: Identity-less domain concepts
9. **Repository**: Persistence abstraction
10. **MapStruct**: Layer-to-layer mapping

**Architecture Principles**:
- Domain independence (Hexagonal Architecture)
- Aggregate boundaries (DDD)
- Event-driven communication (Outbox pattern)
- Immutability (Records, Value Objects)
- Type safety (Sealed interfaces, MapStruct)

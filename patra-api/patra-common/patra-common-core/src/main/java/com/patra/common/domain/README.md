# 领域基础类 (patra-common)

本包为 Papertrace 所有微服务的 DDD 聚合根和事件实现提供**框架无关的基础类**。

---

## 包结构

```
domain/
├─ AggregateRoot.java        # 聚合根基类
├─ ReadOnlyAggregate.java    # 只读聚合基类(CQRS 读端)
└─ DomainEvent.java          # 领域事件接口
```

---

## 核心类

### 1. AggregateRoot<ID>

**目的**: 领域层所有聚合根的抽象基类。

**核心特性**:
- **身份管理**: 分配和追踪聚合 ID
- **乐观锁**: 用于并发控制的版本字段
- **领域事件**: 收集状态变化期间引发的事件
- **纯 Java**: 无框架依赖(仅 Serializable)

**源码**: [`AggregateRoot.java`](AggregateRoot.java:1)

#### 使用示例

```java
public class PlanAggregate extends AggregateRoot<Long> {

    private final String planKey;
    private PlanStatus status;

    // 私有构造器
    private PlanAggregate(Long id, String planKey, PlanStatus status) {
        super(id);  // 设置 ID
        this.planKey = planKey;
        this.status = status;
    }

    // 创建新聚合的工厂方法
    public static PlanAggregate create(String planKey) {
        return new PlanAggregate(null, planKey, PlanStatus.DRAFT);
    }

    // 从持久化恢复的工厂方法
    public static PlanAggregate restore(Long id, String planKey, PlanStatus status, long version) {
        PlanAggregate aggregate = new PlanAggregate(id, planKey, status);
        aggregate.assignVersion(version);  // 设置乐观锁版本
        return aggregate;
    }

    // 领域行为
    public void startSlicing() {
        if (this.status != PlanStatus.DRAFT) {
            throw new IllegalStateException("只能从 DRAFT 状态进行切片");
        }
        this.status = PlanStatus.SLICING;

        // 引发领域事件
        addDomainEvent(new PlanSlicingStartedEvent(getId(), Instant.now()));
    }
}
```

#### 核心方法

| 方法 | 目的 | 何时调用 |
|--------|---------|--------------|
| `assignId(ID id)` | 持久化后分配 ID | 仓储在 INSERT 后 |
| `assignVersion(long version)` | 设置乐观锁版本 | 仓储在 SELECT/UPDATE 后 |
| `isTransient()` | 检查是否尚未持久化 | 保存前 |
| `addDomainEvent(DomainEvent event)` | 注册领域事件 | 在领域行为中 |
| `pullDomainEvents()` | 提取事件用于发布 | 应用层在持久化后 |
| `peekDomainEvents()` | 查看事件(调试/测试) | 单元测试 |

#### 身份管理

**持久化前**:
```java
PlanAggregate plan = PlanAggregate.create("plan-123");
assertTrue(plan.isTransient());  // ID 为 null
assertNull(plan.getId());
```

**持久化后**:
```java
PlanAggregate saved = repository.save(plan);
assertFalse(saved.isTransient());  // ID 已分配
assertNotNull(saved.getId());
```

#### 乐观锁

**版本字段**防止更新丢失:

```java
// 线程 A 读取计划(version=1)
PlanAggregate planA = repository.findById(123L);

// 线程 B 读取同一计划(version=1)
PlanAggregate planB = repository.findById(123L);

// 线程 A 更新(version 变为 2)
planA.startSlicing();
repository.save(planA);  // 成功(version 1 → 2)

// 线程 B 更新(陈旧的 version=1)
planB.markReady();
repository.save(planB);  // ❌ OptimisticLockException(期望 version 2,实际为 1)
```

**MyBatis-Plus** 通过 `@Version` 注解处理此问题:
```java
@Data
@TableName("ingest_plan")
public class IngestPlanDO {
    @TableId
    private Long id;

    @Version  // UPDATE 时自动递增
    private Long version;
}
```

#### 领域事件

**生命周期**:
1. **引发**: 聚合在行为期间调用 `addDomainEvent()`
2. **收集**: 应用层在持久化后调用 `pullDomainEvents()`
3. **发布**: 应用层发布到 outbox/消息总线
4. **清空**: `pullDomainEvents()` 清空内部事件列表

**示例流程**:
```java
// 1. 聚合引发事件
public void startSlicing() {
    this.status = PlanStatus.SLICING;
    addDomainEvent(new PlanSlicingStartedEvent(getId(), Instant.now()));
}

// 2. 应用层收集并发布
@Transactional
public void executeUseCase() {
    PlanAggregate plan = repository.findById(123L);
    plan.startSlicing();

    repository.save(plan);  // 持久化状态变化

    List<DomainEvent> events = plan.pullDomainEvents();  // 提取事件
    eventPublisher.publish(events);  // 发布到 outbox
}
```

**为什么不从聚合直接发布?**
- 聚合是**纯领域**(无基础设施关注点)
- 发布需要事务协调(应用层职责)
- 可测试性(在应用层模拟发布器,而非领域层)

---

### 2. ReadOnlyAggregate<ID>

**目的**: 只读聚合基类(CQRS 读端)。

**与 `AggregateRoot` 的区别**:
- 无领域事件(只读)
- 无状态转换(创建后不可变)
- 用于针对读取优化的查询模型

**用法**:
```java
public class ProvenanceConfiguration extends ReadOnlyAggregate<Long> {

    private final Provenance provenance;
    private final HttpConfig httpConfig;
    // ... 其他配置

    // 无 setters,无状态变化
    public boolean isComplete() {
        return provenance != null && provenance.isActive();
    }
}
```

**源码**: [`ReadOnlyAggregate.java`](ReadOnlyAggregate.java)

---

### 3. DomainEvent

**目的**: 领域事件的标记接口。

**契约**:
```java
public interface DomainEvent extends Serializable {

    /**
     * 返回事件类型标识符(例如 "ingest.task.queued")。
     * 用于路由和反序列化。
     */
    String eventType();
}
```

**实现模式**(record):
```java
public record TaskQueuedEvent(
    Long taskId,
    Long planId,
    String provenanceCode,
    Instant queuedAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "ingest.task.queued";
    }

    // 工厂方法
    public static TaskQueuedEvent of(Long taskId, Long planId, String provenanceCode, Instant queuedAt) {
        return new TaskQueuedEvent(taskId, planId, provenanceCode,
            queuedAt != null ? queuedAt : Instant.now());
    }
}
```

**源码**: [`DomainEvent.java`](DomainEvent.java)

---

## 设计模式

### 1. 工厂方法优于构造器

**问题**: 聚合构造需要验证、默认值、业务规则。

**解决方案**: 私有构造器 + 静态工厂方法。

```java
public class TaskAggregate extends AggregateRoot<Long> {

    // 私有构造器(强制使用工厂)
    private TaskAggregate(...) { }

    // 创建新实例的工厂
    public static TaskAggregate create(...) {
        // 验证 + 默认值
        return new TaskAggregate(null, ...);
    }

    // 从数据库恢复的工厂
    public static TaskAggregate restore(Long id, ..., long version) {
        TaskAggregate aggregate = new TaskAggregate(id, ...);
        aggregate.assignVersion(version);
        return aggregate;
    }
}
```

**好处**:
- 明确意图(`create` vs. `restore`)
- 封装验证
- 防止无效构造

### 2. Tell, Don't Ask(告诉,别询问)

**问题**: 暴露内部状态导致业务逻辑泄漏到应用层。

**反模式**:
```java
// 错误: 应用层检查状态并变更聚合
if (plan.getStatus() == PlanStatus.DRAFT) {
    plan.setStatus(PlanStatus.SLICING);  // ❌ 暴露内部状态
}
```

**正确模式**:
```java
// 正确: 聚合封装状态转换
plan.startSlicing();  // ✅ 内部验证状态机
```

**规则**: 聚合应该**暴露行为**,而非状态。

### 3. 轻量级事件溯源

**问题**: 需要状态变化的审计跟踪,但不使用完整事件溯源。

**解决方案**: 为重要状态转换引发领域事件。

```java
public void markRunning(Instant startedAt) {
    this.status = TaskStatus.RUNNING;
    this.executionTimeline = executionTimeline.onStart(startedAt);

    // 用于可观测性/审计的事件
    addDomainEvent(new TaskStartedEvent(getId(), startedAt));
}
```

**好处**:
- 审计跟踪(谁在何时做了什么)
- 与其他服务集成(异步通知)
- 调试(事件日志显示状态转换)

---

## 测试指南

### 单元测试(聚合行为)

```java
@Test
void testAggregateStateTransition() {
    // Given
    PlanAggregate plan = PlanAggregate.create("plan-123");

    // When
    plan.startSlicing();

    // Then
    assertEquals(PlanStatus.SLICING, plan.getStatus());

    // 验证事件
    List<DomainEvent> events = plan.peekDomainEvents();
    assertEquals(1, events.size());
    assertTrue(events.get(0) instanceof PlanSlicingStartedEvent);
}
```

### 单元测试(事件提取)

```java
@Test
void testEventPulling() {
    // Given
    TaskAggregate task = TaskAggregate.create(...);
    task.markRunning(Instant.now());

    // When
    List<DomainEvent> events = task.pullDomainEvents();

    // Then
    assertEquals(1, events.size());

    // 第二次提取返回空(事件已清空)
    List<DomainEvent> events2 = task.pullDomainEvents();
    assertTrue(events2.isEmpty());
}
```

### 集成测试(乐观锁)

```java
@Test
void testOptimisticLockingConflict() {
    // Given: 持久化初始聚合
    PlanAggregate plan = PlanAggregate.create("plan-123");
    plan = repository.save(plan);
    Long planId = plan.getId();

    // When: 两个线程加载同一聚合
    PlanAggregate planA = repository.findById(planId).get();
    PlanAggregate planB = repository.findById(planId).get();

    // 线程 A 更新
    planA.startSlicing();
    repository.save(planA);  // 成功

    // 线程 B 更新(陈旧版本)
    planB.markReady();

    // Then: OptimisticLockException
    assertThrows(OptimisticLockException.class, () ->
        repository.save(planB)
    );
}
```

---

## 常见陷阱

### ❌ 不要: 绕过聚合封装

```java
// 错误: 应用层直接设置聚合字段
plan.setStatus(PlanStatus.SLICING);  // ❌ 违反封装
```

### ✅ 正确: 使用行为方法

```java
// 正确: 聚合验证状态转换
plan.startSlicing();  // ✅ 封装的行为
```

### ❌ 不要: 跨线程共享聚合

```java
// 错误: 聚合不是线程安全的
PlanAggregate plan = repository.findById(123L);
executor.submit(() -> plan.startSlicing());  // ❌ 竞态条件
executor.submit(() -> plan.markReady());     // ❌ 竞态条件
```

### ✅ 正确: 每个线程/事务一个聚合

```java
// 正确: 每个线程加载新副本
executor.submit(() -> {
    PlanAggregate plan = repository.findById(123L);
    plan.startSlicing();
    repository.save(plan);
});
```

### ❌ 不要: 在持久化聚合前发布事件

```java
// 错误: 事件已发布但聚合保存失败
plan.startSlicing();
eventPublisher.publish(plan.pullDomainEvents());  // ❌ 顺序错误
repository.save(plan);  // 可能失败,但事件已发送
```

### ✅ 正确: 先持久化再发布(在同一事务中)

```java
// 正确: 仅在保存成功后发布事件
plan.startSlicing();
repository.save(plan);  // 提交状态变化
eventPublisher.publish(plan.pullDomainEvents());  // 然后发布
```

---

## 框架独立性

**核心原则**: 领域层**零框架依赖**。

**允许**:
- ✅ Java SE 类(`java.time.*`、`java.util.*`)
- ✅ `Serializable`(用于远程调用/缓存)
- ✅ `patra-common`(共享领域工具)

**禁止**:
- ❌ Spring 注解(`@Component`、`@Transactional`、`@Autowired`)
- ❌ MyBatis 注解(`@TableName`、`@TableId`)
- ❌ Jackson 注解(`@JsonProperty`、`@JsonIgnore`)
- ❌ Lombok(除了私有字段上的 `@Getter`,可接受)

**为什么?**
- **可测试性**: 无需 Spring 上下文即可测试领域逻辑
- **可移植性**: 易于迁移框架(Spring → Quarkus)
- **专注**: 业务逻辑与基础设施关注点隔离

---

**另请参阅**:
- [架构指南](../../../../../docs/ARCHITECTURE.md)
- [开发指南](../../../../../docs/DEV-GUIDE.md)
- [patra-ingest 领域模型 README](../../../../../../../patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/README.md)

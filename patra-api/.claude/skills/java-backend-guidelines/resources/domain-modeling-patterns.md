# 领域建模模式指南

> **目的**: 使用 DDD 战术模式在领域层实现纯业务逻辑建模

## 🚀 快速开始

### 需要创建新聚合根？

```java
// 1. 定义聚合根类
public class PlanAggregate extends AggregateRoot<Long> {
    // 不可变业务键
    private final String planKey;
    private final String provenanceCode;

    // 可变状态
    private PlanStatus status;

    // 私有构造函数
    private PlanAggregate(...) {
        // 验证不变量
        this.planKey = Objects.requireNonNull(planKey);
        this.status = PlanStatus.DRAFT;
    }

    // 工厂方法
    public static PlanAggregate create(...) {
        return new PlanAggregate(null, ...);  // 新建时 ID 为 null
    }

    public static PlanAggregate restore(Long id, ..., long version) {
        PlanAggregate aggregate = new PlanAggregate(id, ...);
        aggregate.assignVersion(version);  // 乐观锁
        return aggregate;
    }

    // 业务方法
    public void startSlicing() {
        if (status != PlanStatus.DRAFT) {
            throw new IllegalStateException("只能从 DRAFT 状态开始切片");
        }
        status = PlanStatus.SLICING;
    }
}

// 2. 定义仓储接口（在 domain/port 包）
public interface PlanRepository {
    PlanAggregate save(PlanAggregate plan);
    Optional<PlanAggregate> findById(Long id);
    boolean existsByPlanKey(String planKey);
}
```

### 需要创建值对象？

```java
// 使用 record - 自动不可变、equals/hashCode
public record WindowSpec(Instant from, Instant to) {
    // 紧凑构造器验证
    public WindowSpec {
        if (from == null || to == null) {
            throw new IllegalArgumentException("时间窗口不能为空");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("开始时间必须早于结束时间");
        }
    }

    // 工厂方法
    public static WindowSpec of(Instant from, Instant to) {
        return new WindowSpec(from, to);
    }
}
```

---

## 📊 决策矩阵

### 选择正确的建模模式

| 场景 | 使用模式 | 示例 |
|------|---------|------|
| 有唯一标识，状态会变化 | **聚合根/实体** | PlanAggregate, TaskRun |
| 没有标识，通过属性比较 | **值对象** | WindowSpec, BatchPlan |
| 固定的业务选项 | **领域枚举** | PlanStatus, OperationCode |
| 重要的状态变化 | **领域事件** | TaskCompletedEvent |
| 跨聚合的业务逻辑 | **领域服务** | SliceStatusCalculator |
| 复杂对象创建 | **工厂** | OutboxRelayLogFactory |
| 外部依赖契约 | **端口** | PlanRepository |

### 聚合设计决策树

```
需要持久化吗？
  ├─ 否 → 值对象 (record)
  └─ 是 → 有独立生命周期吗？
          ├─ 否 → 实体 (聚合的一部分)
          └─ 是 → 是一致性边界吗？
                  ├─ 否 → 独立实体
                  └─ 是 → 聚合根
```

---

## 🎯 常见场景与模板

### 场景 1: 创建带状态机的聚合根

<details>
<summary>查看完整实现</summary>

```java
@Getter
public class TaskAggregate extends AggregateRoot<Long> {

    private final Long sliceId;
    private TaskStatus status;
    private Instant completedAt;

    private TaskAggregate(Long id, Long sliceId, TaskStatus status) {
        super(id);
        this.sliceId = Objects.requireNonNull(sliceId);
        this.status = status == null ? TaskStatus.PENDING : status;
    }

    // 创建新任务
    public static TaskAggregate create(Long sliceId) {
        return new TaskAggregate(null, sliceId, TaskStatus.PENDING);
    }

    // 从数据库恢复
    public static TaskAggregate restore(Long id, Long sliceId,
                                       TaskStatus status, long version) {
        TaskAggregate task = new TaskAggregate(id, sliceId, status);
        task.assignVersion(version);
        return task;
    }

    // 状态转换方法
    public void queue() {
        if (status != TaskStatus.PENDING) {
            throw new IllegalStateException("只能从 PENDING 状态入队");
        }
        status = TaskStatus.QUEUED;
    }

    public void markSucceeded() {
        if (status != TaskStatus.RUNNING) {
            throw new IllegalStateException("只能从 RUNNING 状态完成");
        }
        status = TaskStatus.SUCCEEDED;
        completedAt = Instant.now();

        // 发布领域事件
        registerEvent(TaskCompletedEvent.of(getId(), sliceId, "SUCCEEDED"));
    }
}
```

</details>

### 场景 2: 使用 sealed 接口实现多态值对象

<details>
<summary>查看 WindowSpec 实现</summary>

```java
public sealed interface WindowSpec
    permits WindowSpec.Time, WindowSpec.IdRange, WindowSpec.Single {

    SliceStrategy strategy();
    Map<String, Object> toMap();

    // 时间窗口
    record Time(Instant from, Instant to) implements WindowSpec {
        public Time {
            // 验证逻辑
            if (from.isAfter(to)) {
                throw new IllegalArgumentException("时间窗口无效");
            }
        }

        @Override
        public SliceStrategy strategy() {
            return SliceStrategy.TIME;
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of(
                "strategy", "TIME",
                "from", from.toString(),
                "to", to.toString()
            );
        }
    }

    // ID 范围窗口
    record IdRange(Long from, Long to) implements WindowSpec {
        // 类似实现...
    }

    // 单窗口
    record Single() implements WindowSpec {
        @Override
        public SliceStrategy strategy() {
            return SliceStrategy.SINGLE;
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("strategy", "SINGLE");
        }
    }

    // 工厂方法
    static WindowSpec ofTime(Instant from, Instant to) {
        return new Time(from, to);
    }

    static WindowSpec ofIdRange(Long from, Long to) {
        return new IdRange(from, to);
    }

    static WindowSpec single() {
        return new Single();
    }
}
```

</details>

### 场景 3: 实现复杂实体构建器

<details>
<summary>查看 OutboxMessage Builder 实现</summary>

```java
public final class OutboxMessage {

    private final Long id;
    private final String channel;
    private final String dedupKey;
    private final String payloadJson;
    private final String statusCode;
    private final Integer retryCount;
    // ... 更多字段

    private OutboxMessage(Builder builder) {
        // 必填字段验证
        this.channel = Objects.requireNonNull(builder.channel, "channel 必填");
        this.dedupKey = Objects.requireNonNull(builder.dedupKey, "dedupKey 必填");

        // 可选字段默认值
        this.id = builder.id;
        this.payloadJson = builder.payloadJson;
        this.statusCode = builder.statusCode == null ? "PENDING" : builder.statusCode;
        this.retryCount = builder.retryCount == null ? 0 : builder.retryCount;
    }

    // Builder
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long id;
        private String channel;
        private String dedupKey;
        private String payloadJson;
        private String statusCode;
        private Integer retryCount;

        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder dedupKey(String dedupKey) {
            this.dedupKey = dedupKey;
            return this;
        }

        public Builder payloadJson(String json) {
            this.payloadJson = json;
            return this;
        }

        public OutboxMessage build() {
            return new OutboxMessage(this);
        }
    }
}
```

</details>

---

## 📋 速查表

### 领域对象命名规范

| 类型 | 命名模式 | 示例 |
|------|---------|------|
| 聚合根 | XxxAggregate | PlanAggregate, TaskAggregate |
| 实体 | 名词 | TaskRun, OutboxMessage |
| 值对象 | 描述性名词 | WindowSpec, BatchPlan |
| 领域事件 | 过去时+Event | TaskCompletedEvent |
| 领域服务 | XxxService/Calculator | SliceStatusCalculator |
| 仓储端口 | XxxRepository | PlanRepository |
| 工厂 | XxxFactory | OutboxRelayLogFactory |

### 领域层约束

| ✅ 允许 | ❌ 禁止 |
|--------|---------|
| 纯 Java | Spring 注解 (@Component, @Service) |
| Lombok | 框架依赖 (Spring, JPA, MyBatis) |
| Hutool 工具 | 数据库注解 (@TableName, @TableId) |
| Java 17+ (record, sealed) | Jackson 注解 (@JsonProperty) |
| 领域事件 | 直接 HTTP/RPC 调用 |
| 端口接口 | 直接访问基础设施 |

### 常用模式组合

| 组合 | 用途 | 示例 |
|------|------|------|
| 聚合根 + 仓储端口 | 持久化业务实体 | PlanAggregate + PlanRepository |
| 实体 + Builder | 复杂对象创建 | OutboxMessage.builder() |
| 值对象 + record | 不可变数据 | record BatchPlan(...) |
| 枚举 + fromCode() | 外部输入解析 | OperationCode.fromCode("HARVEST") |
| 领域服务 + 静态方法 | 无状态业务逻辑 | SliceStatusCalculator.calculate() |

---

## 🏗️ 核心模式详解

### 聚合根 (Aggregate Root)

**定义**: 一组相关对象的集合，作为数据修改的单元。聚合根是外部访问的唯一入口。

**设计原则**:
- 一致性边界：每次操作后聚合内部必须保持一致
- 事务边界：整个聚合在一个事务中保存/更新
- 外部引用：其他聚合只能通过 ID 引用
- 小聚合：尽量保持聚合小而专注

**关键模式**:
```java
// 私有构造函数 - 强制使用工厂方法
private PlanAggregate(...) { }

// 工厂方法 - 明确意图
public static PlanAggregate create(...) { }  // 新建
public static PlanAggregate restore(...) { } // 恢复

// 不可变字段 - 业务键不变
private final String planKey;

// 状态机 - 验证状态转换
public void startSlicing() {
    if (status != DRAFT) throw new IllegalStateException();
    status = SLICING;
}

// 不变量验证 - 保证一致性
Objects.requireNonNull(planKey, "planKey 不能为空");
```

### 值对象 (Value Object)

**定义**: 没有唯一标识的不可变对象，通过属性值比较相等性。

**最佳实践**: 使用 Java record
```java
public record WindowSpec(Instant from, Instant to) {
    // 紧凑构造器验证
    public WindowSpec {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("无效窗口");
        }
    }
}
```

### 领域事件 (Domain Event)

**定义**: 捕获领域中发生的重要业务事件。

**命名规范**: 使用过去时
- ✅ TaskCompletedEvent
- ❌ TaskCompleteEvent

**实现模式**:
```java
public record TaskCompletedEvent(
    Long taskId,
    String status,
    Instant occurredAt
) implements DomainEvent {

    // 自动填充时间戳
    public TaskCompletedEvent {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    // 工厂方法
    public static TaskCompletedEvent of(Long taskId, String status) {
        return new TaskCompletedEvent(taskId, status, null);
    }
}
```

---

## ✅ 设计检查清单

创建领域模型前，确认：

### 通用规则
- [ ] 无框架依赖（纯 Java + Lombok + Hutool）
- [ ] 构造函数中验证不变量
- [ ] 适当使用不可变性（值对象用 record，字段用 final）

### 聚合根专项
- [ ] 提供 create() 和 restore() 工厂方法
- [ ] 状态转换有业务方法封装
- [ ] 重要变化发布领域事件

### 值对象专项
- [ ] 使用 record 实现
- [ ] 紧凑构造器中验证
- [ ] 提供工厂方法

### 领域事件专项
- [ ] 过去时命名
- [ ] 使用 record 实现
- [ ] 自动填充 occurredAt
- [ ] 包含完整上下文

---

## 📚 深入学习

<details>
<summary>高级主题</summary>

### 聚合设计的权衡

**大聚合 vs 小聚合**:
- 大聚合：强一致性，但并发性能差
- 小聚合：高并发，但需要最终一致性

**聚合间引用**:
- 只通过 ID 引用，不持有对象引用
- 使用领域事件协调聚合间交互

### 值对象的高级用法

**自定义类型**:
```java
public record Email(String value) {
    public Email {
        if (!value.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new IllegalArgumentException("无效邮箱");
        }
    }
}
```

**封装业务规则**:
```java
public record Money(BigDecimal amount, Currency currency) {
    public Money add(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("货币不匹配");
        }
        return new Money(amount.add(other.amount), currency);
    }
}
```

</details>

---

## 📚 相关文档

### 核心概念
- [architecture-overview.md](architecture-overview.md) - 六边形架构 + DDD 概览
- [event-driven-architecture.md](event-driven-architecture.md) - 事件驱动架构
- [mybatis-plus-patterns.md](mybatis-plus-patterns.md) - 持久化实现

### 测试指南
- [testing-guide.md](testing-guide.md) - 完整测试策略和实践
- [test-templates-domain.md](test-templates-domain.md) - 领域层测试模板
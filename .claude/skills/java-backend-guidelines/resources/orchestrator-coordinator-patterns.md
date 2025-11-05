# 编排器与协调器模式指南

> **目的**: 在应用层使用编排器和协调器组织用例的执行流程

## 🚀 快速开始

### 需要实现新的用例？

```java
// 1. 创建编排器 (Orchestrator)
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator {

    // 依赖注入
    private final PlanRepository planRepository;  // 领域端口
    private final PlanPersistenceCoordinator persistenceCoordinator;  // 协调器
    private final PlanPublishingCoordinator publishingCoordinator;

    @Transactional  // ✅ 事务边界在编排器层
    public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
        // 阶段 1: 准备上下文
        PlanningContext context = preparePlanningContext(command);

        // 阶段 2: 组装计划
        PlanAssemblyResult assembly = assembleAndValidatePlan(context);

        // 阶段 3: 检查幂等性
        PlanAggregate existingPlan = checkForExistingPlan(assembly.plan());
        if (existingPlan != null) {
            return handleIdempotentReuse(existingPlan);
        }

        // 阶段 4: 持久化 (委派给协调器)
        PlanAggregate savedPlan = persistenceCoordinator.savePlan(assembly.plan());
        List<PlanSliceAggregate> savedSlices =
            persistenceCoordinator.persistSlices(savedPlan, assembly.slices());

        // 阶段 5: 发布事件 (委派给协调器)
        publishingCoordinator.publishNewPlanEvents(savedPlan, savedSlices);

        return buildResult(savedPlan, savedSlices);
    }

    // 私有辅助方法
    private PlanningContext preparePlanningContext(PlanIngestionCommand command) {
        // 准备逻辑...
    }
}

// 2. 创建协调器 (Coordinator)
@Service
@RequiredArgsConstructor
public class PlanPersistenceCoordinator {

    private final PlanRepository planRepository;
    private final PlanSliceRepository sliceRepository;

    // ❌ 不使用 @Transactional - 依赖外层事务边界
    public PlanAggregate savePlan(PlanAggregate plan) {
        try {
            return planRepository.save(plan);
        } catch (Exception ex) {
            throw new PlanPersistenceException("持久化失败", ex);
        }
    }

    public List<PlanSliceAggregate> persistSlices(
        PlanAggregate plan,
        List<PlanSliceAggregate> slices
    ) {
        // 绑定 planId
        slices.forEach(slice -> slice.bindPlan(plan.getId()));

        try {
            return sliceRepository.saveAll(slices);
        } catch (Exception ex) {
            throw new PlanPersistenceException("切片持久化失败", ex);
        }
    }
}
```

---

## 📊 决策矩阵

### 何时使用编排器和协调器？

| 场景 | 推荐模式 | 原因 |
|------|---------|------|
| 简单用例 (<100 行) | 单个编排器 | 无需额外复杂度 |
| 复杂用例 (多关注点) | 编排器 + 协调器 | 职责分离 |
| 需要复用逻辑 | 协调器 | 跨编排器复用 |
| 多个独立事务 | 主编排器 + 子用例 | 事务边界清晰 |
| 长时间运行 | 主编排器 + 子用例 | 避免长事务 |

### 模式选择决策树

```
用例复杂度如何？
  ├─ 简单 (<100 行) → 单个编排器
  └─ 复杂 → 有多个关注点吗？
             ├─ 是 → 需要跨用例复用吗？
             │       ├─ 是 → 编排器 + 协调器
             │       └─ 否 → 单个编排器即可
             └─ 否 → 需要多个事务吗？
                     ├─ 是 → 主编排器 + 子用例
                     └─ 否 → 编排器 + 协调器
```

---

## 🎯 核心概念

### 编排器 (Orchestrator)

**定义**: 实现完整用例的应用层服务，协调工作流的执行。

**职责**:
- 协调用例的执行流程
- 定义事务边界 (@Transactional)
- 委派具体任务给协调器/领域服务
- 通过领域端口访问基础设施

**不应该做**:
- ❌ 包含业务规则（属于领域层）
- ❌ 直接访问数据库（使用端口）
- ❌ 复杂计算（属于领域层）
- ❌ 了解 HTTP/REST（属于适配器层）

### 协调器 (Coordinator)

**定义**: 处理用例中特定关注点的应用层组件。

**职责**:
- 封装特定职责（持久化、发布、验证等）
- 跨编排器复用逻辑
- 错误处理和异常转换
- 批量操作协调

**不应该做**:
- ❌ 定义事务边界（依赖外层）
- ❌ 包含业务规则（属于领域层）

---

## 🏗️ 常见模式

### 模式 1: 编排器 + 协调器

**适用场景**:
- 单一事务边界
- 复杂内部流程
- 多个可复用的关注点

<details>
<summary>查看完整实现</summary>

```java
// 编排器
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator {

    private final PlanPersistenceCoordinator persistenceCoordinator;
    private final PlanIdempotencyCoordinator idempotencyCoordinator;
    private final PlanPublishingCoordinator publishingCoordinator;

    @Transactional  // ✅ 单一事务边界
    public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
        // 准备和验证
        PlanningContext context = preparePlanningContext(command);
        performPreValidation(context);

        // 组装计划
        PlanAssemblyResult assembly = assembleAndValidatePlan(context);

        // 检查幂等性
        PlanAggregate existingPlan = checkForExistingPlan(assembly.plan());
        if (existingPlan != null) {
            // 委派给幂等性协调器
            return idempotencyCoordinator.handleIdempotentPlanReuse(existingPlan);
        }

        // 持久化 (委派给持久化协调器)
        PlanAggregate savedPlan = persistenceCoordinator.savePlan(assembly.plan());
        List<PlanSliceAggregate> savedSlices =
            persistenceCoordinator.persistSlices(savedPlan, assembly.slices());
        List<TaskAggregate> savedTasks =
            persistenceCoordinator.persistTasks(savedPlan, savedSlices, assembly.tasks());

        // 发布事件 (委派给发布协调器)
        List<TaskQueuedEvent> events = publishingCoordinator.collectQueuedEvents(savedTasks);
        publishingCoordinator.publishNewPlanEvents(events, savedPlan);

        return publishingCoordinator.buildIngestionResult(savedPlan, savedSlices, savedTasks);
    }
}

// 协调器 - 持久化
@Service
@RequiredArgsConstructor
public class PlanPersistenceCoordinator {
    // ❌ 不使用 @Transactional

    public PlanAggregate savePlan(PlanAggregate plan) {
        try {
            return planRepository.save(plan);
        } catch (Exception ex) {
            throw new PlanPersistenceException("持久化失败", ex);
        }
    }

    public List<PlanSliceAggregate> persistSlices(
        PlanAggregate plan,
        List<PlanSliceAggregate> slices
    ) {
        slices.forEach(slice -> slice.bindPlan(plan.getId()));
        try {
            return sliceRepository.saveAll(slices);
        } catch (Exception ex) {
            throw new PlanPersistenceException("切片持久化失败", ex);
        }
    }
}

// 协调器 - 幂等性
@Service
@RequiredArgsConstructor
public class PlanIdempotencyCoordinator {
    // ❌ 不使用 @Transactional

    public PlanIngestionResult handleIdempotentPlanReuse(PlanAggregate existingPlan) {
        // 加载现有数据
        List<TaskAggregate> existingTasks = taskRepository.findByPlanId(existingPlan.getId());

        // 识别需要重试的任务
        List<TaskAggregate> retryTasks = prepareTasksForRetry(existingTasks);

        if (!retryTasks.isEmpty()) {
            processRetryTasks(existingPlan, retryTasks);
        }

        return buildResult(existingPlan, existingTasks);
    }
}

// 协调器 - 发布
@Service
@RequiredArgsConstructor
public class PlanPublishingCoordinator {
    // ❌ 不使用 @Transactional

    public void publishNewPlanEvents(
        List<TaskQueuedEvent> events,
        PlanAggregate plan
    ) {
        // 委派给 Outbox 发布器 (与持久化同一事务)
        taskOutboxPublisher.publish(events, plan);
    }

    public List<TaskQueuedEvent> collectQueuedEvents(List<TaskAggregate> tasks) {
        List<TaskQueuedEvent> events = new ArrayList<>();
        for (TaskAggregate task : tasks) {
            task.raiseQueuedEvent();
            task.pullDomainEvents().stream()
                .filter(TaskQueuedEvent.class::isInstance)
                .map(TaskQueuedEvent.class::cast)
                .forEach(events::add);
        }
        return events;
    }
}
```

</details>

### 模式 2: 主编排器 + 子用例

**适用场景**:
- 需要多个独立事务
- 阶段间有外部 API 调用
- 长时间运行的操作

<details>
<summary>查看完整实现</summary>

```java
// 主编排器
@Service
@RequiredArgsConstructor
public class OutboxRelayOrchestrator {

    private final RelayPlanBuilder planBuilder;
    private final OutboxRelayExecutor relayExecutor;  // 子用例
    private final RelayEventPublisher eventPublisher;

    @Transactional  // ✅ 覆盖执行器 + 事件发布
    public RelayReport relay(OutboxRelayCommand command) {
        // 阶段 1: 构建计划 (无事务)
        RelayPlan plan = planBuilder.build(command);

        // 阶段 2: 执行中继 (在事务内 - 更新消息状态)
        RelayBatchResult result = relayExecutor.execute(plan);

        // 阶段 3: 发布事件 (同一事务)
        eventPublisher.publish(result.events());

        return buildReport(result);
    }
}

// 子用例 - 执行器
@Service
@RequiredArgsConstructor
public class OutboxRelayExecutor {
    // ❌ 不使用 @Transactional - 依赖外层边界

    public RelayBatchResult execute(RelayPlan plan) {
        // 尝试获取租约
        boolean leaseAcquired = leaseCoordinator.tryAcquireLease(plan);
        if (!leaseAcquired) {
            return RelayBatchResult.leaseMissed(plan.channel());
        }

        // 获取消息
        List<OutboxMessage> messages = fetchMessages(plan);

        // 发布到 RocketMQ
        RelayBatchResult result = publishCoordinator.publishBatch(messages, plan);

        // 记录中继日志
        logCoordinator.recordRelayLog(result);

        return result;
    }
}
```

</details>

---

## 📋 速查表

### 职责分配

| 层次 | 职责 | 示例 |
|------|------|------|
| **编排器** | 用例协调、事务边界 | `PlanIngestionOrchestrator` |
| **协调器** | 特定关注点处理 | `PlanPersistenceCoordinator` |
| **领域服务** | 跨聚合业务逻辑 | `SliceStatusCalculator` |
| **聚合根** | 实体业务规则 | `PlanAggregate.startSlicing()` |
| **端口** | 基础设施契约 | `PlanRepository` |

### 事务管理规则

| ✅ 应该 | ❌ 不应该 |
|---------|-----------|
| @Transactional 在编排器层 | @Transactional 在协调器层 |
| 保持事务短 | 在事务内调用外部 API |
| 只在事务内执行 DB 操作 | 在事务内执行复杂计算 |
| 使用 REQUIRES_NEW 处理独立事务 | 嵌套多层事务 |

### 命名约定

| 类型 | 命名模式 | 示例 |
|------|---------|------|
| 编排器 | XxxOrchestrator | `PlanIngestionOrchestrator` |
| 协调器 | XxxCoordinator | `PlanPersistenceCoordinator` |
| 用例接口 | XxxUseCase | `PlanIngestionUseCase` |
| 命令 | XxxCommand | `PlanIngestionCommand` |
| 结果 | XxxResult | `PlanIngestionResult` |

---

## ⚠️ 常见问题与解决

### 问题 1: 事务过长导致死锁

**症状**: 高并发下频繁死锁，事务持有锁时间过长

**解决方案**:
```java
// ❌ 错误: 事务内包含外部 API 调用
@Transactional
public void execute() {
    prepare();           // DB 查询 - 持有锁
    callExternalAPI();   // 10+ 秒 - 事务被阻塞！
    saveResults();       // DB 更新
}

// ✅ 正确: 外部 API 在事务外
public void execute() {
    // 阶段 1: 外部调用 (无事务)
    Config config = callExternalAPI();

    // 阶段 2: 持久化 (有事务 - 短！)
    persistResults(config);
}

@Transactional
private void persistResults(Config config) {
    // 只有 DB 操作 - 快速！
    repository.save(config);
}
```

### 问题 2: 协调器嵌套事务

**症状**: `UnexpectedRollbackException` 或事务行为不符合预期

**解决方案**:
```java
// ❌ 错误: 协调器定义事务
@Service
public class PlanPersistenceCoordinator {
    @Transactional  // ❌ 创建嵌套事务
    public PlanAggregate savePlan(PlanAggregate plan) {
        return planRepository.save(plan);
    }
}

// ✅ 正确: 协调器不定义事务
@Service
public class PlanPersistenceCoordinator {
    // ❌ 无 @Transactional - 依赖外层边界
    public PlanAggregate savePlan(PlanAggregate plan) {
        return planRepository.save(plan);
    }
}
```

### 问题 3: 编排器包含业务逻辑

**症状**: 编排器变得臃肿，难以测试

**解决方案**:
```java
// ❌ 错误: 业务规则在编排器
@Transactional
public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
    // ❌ 业务规则在编排器
    if (command.windowFrom().isAfter(command.windowTo())) {
        throw new ValidationException("无效窗口");
    }

    PlanAggregate plan = new PlanAggregate(...);
    planRepository.save(plan);
}

// ✅ 正确: 业务规则在领域层
@Transactional
public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
    // 委派给领域聚合根工厂方法
    PlanAggregate plan = PlanAggregate.create(
        command.windowFrom(),
        command.windowTo()  // ✅ 验证在领域层
    );

    planRepository.save(plan);
}

// 领域聚合根
public class PlanAggregate {
    public static PlanAggregate create(Instant from, Instant to) {
        // ✅ 业务规则验证在领域层
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("无效窗口: from 必须早于 to");
        }
        return new PlanAggregate(from, to);
    }
}
```

---

## ✅ 最佳实践清单

### 编排器设计
- [ ] 单一职责 - 一个编排器协调一个用例
- [ ] @Transactional 只在编排器层
- [ ] 使用领域端口，不直接访问基础设施
- [ ] 清晰的方法命名 (描述做什么)
- [ ] 委派业务逻辑给领域层

### 协调器设计
- [ ] 不使用 @Transactional (依赖外层)
- [ ] 封装单一关注点 (持久化、发布、验证)
- [ ] 包装基础设施异常为领域异常
- [ ] 可跨编排器复用

### 事务管理
- [ ] 保持事务短 (只包含 DB 操作)
- [ ] 外部 API 调用在事务外
- [ ] 避免嵌套事务
- [ ] 复杂计算在事务外

### 代码质量
- [ ] 编排器 <200 行
- [ ] 协调器 <100 行
- [ ] 清晰的阶段划分
- [ ] 单元测试覆盖

---

## 📚 相关文档

### 核心概念
- [architecture-overview.md](architecture-overview.md) - 六边形架构概览
- [domain-modeling-patterns.md](domain-modeling-patterns.md) - 领域建模模式
- [event-driven-architecture.md](event-driven-architecture.md) - 事件驱动架构

### 测试指南
- [testing-guide.md](testing-guide.md) - 完整测试策略
- [test-templates-application.md](test-templates-application.md) - 应用层测试模板
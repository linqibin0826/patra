# 六边形架构 + DDD 概览

> **5 分钟快速启动** → 理解 Patra 的架构原则并立即开始构建功能

---

## 🚀 快速启动

### 3 步创建新功能

```
步骤 1: 从 Domain 开始 →  建模聚合/实体/值对象
步骤 2: 添加 Application 层 → 创建编排者协调用例
步骤 3: 连接 Adapter 和 Infra → REST API + 数据库仓储
```

**示例: 创建 "Provenance 管理" 功能**

```java
// 步骤 1: Domain 层 (domain/model/vo/provenance/Provenance.java)
public record Provenance(
    Long id,
    String code,
    String name,
    boolean active
) {
    // ✅ 业务规则在此处
    public void validate() {
        if (code == null || code.isBlank()) {
            throw new ProvenanceException("Code cannot be blank");
        }
    }
}

// 步骤 2: Application 层 (app/usecase/provenance/ProvenanceManagementOrchestrator.java)
@Service
@RequiredArgsConstructor
public class ProvenanceManagementOrchestrator {
    private final ProvenanceRepository repository;

    @Transactional
    public ProvenanceResult create(CreateProvenanceCommand command) {
        Provenance provenance = Provenance.from(command);
        provenance.validate();  // ✅ 业务规则验证
        return repository.save(provenance);
    }
}

// 步骤 3a: Adapter 层 (adapter/rest/ProvenanceController.java)
@RestController
@RequestMapping("/api/v1/provenances")
@RequiredArgsConstructor
public class ProvenanceController {
    private final ProvenanceManagementOrchestrator orchestrator;

    @PostMapping
    public ResponseEntity<ProvenanceResponse> create(
        @Valid @RequestBody CreateProvenanceCommand command
    ) {
        return ResponseEntity.ok(orchestrator.create(command));
    }
}

// 步骤 3b: Infrastructure 层 (infra/persistence/repository/ProvenanceRepositoryImpl.java)
@Repository
@RequiredArgsConstructor
public class ProvenanceRepositoryImpl implements ProvenanceRepository {
    private final ProvenanceMapper mapper;
    private final ProvenanceConverter converter;

    @Override
    public Provenance save(Provenance provenance) {
        ProvenanceDO dataObject = converter.toDO(provenance);
        mapper.insert(dataObject);
        return converter.toDomain(dataObject);
    }
}
```

✅ **完成!** 完整的四层架构实现。

---

## 📊 决策矩阵

### 我的代码应该放在哪一层?

| 问题 | 答案 | 理由 |
|------|------|------|
| 包含业务规则? | Domain 层 | 业务逻辑必须框架无关 |
| 协调多个操作? | Application 层 | 编排者管理用例流程 |
| 接收 HTTP/MQ/Job? | Adapter 层 | 驱动适配器处理外部触发 |
| 访问数据库/API? | Infrastructure 层 | 被驱动适配器提供资源访问 |

### 依赖方向检查表

| 从 | 可以依赖 | 禁止依赖 |
|-----|----------|----------|
| **Adapter** | ✅ App, Api, Domain | ❌ Infra |
| **App** | ✅ Domain, patra-common | ❌ Adapter, Infra |
| **Domain** | ✅ patra-common, Lombok, Hutool | ❌ Spring, MyBatis, 任何框架 |
| **Infra** | ✅ Domain, MyBatis, patra-common | ❌ Adapter, App |

---

## 🎯 核心架构原则

### 原则 1: 六边形架构 (Ports & Adapters)

<details>
<summary><b>查看完整架构图</b></summary>

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Adapter Layer (Driving Side - Outermost)                │
│    Purpose: Receive external triggers                       │
│    Examples: REST Controllers, Jobs, Message Consumers      │
│    Module: patra-{service}-adapter                          │
│    Depends: app + api + web starters                        │
│    ↓ calls ↓                                                │
├─────────────────────────────────────────────────────────────┤
│ 2. Application Layer                                        │
│    Purpose: Orchestrate use cases, manage transactions      │
│    Examples: Orchestrators, Coordinators                    │
│    Module: patra-{service}-app                              │
│    Depends: domain + patra-common + core starter            │
│    ↓ calls ↓                                                │
├─────────────────────────────────────────────────────────────┤
│ 3. Domain Layer (Core - Pure Java)                         │
│    Purpose: Business logic and rules                        │
│    Examples: Aggregates, Entities, VOs, Events, Ports       │
│    Module: patra-{service}-domain                           │
│    Depends: ONLY patra-common + Lombok + Hutool             │
│    ↑ implemented by ↑                                       │
├─────────────────────────────────────────────────────────────┤
│ 4. Infrastructure Layer (Driven Side)                       │
│    Purpose: Access external resources                       │
│    Examples: DB Repositories, External APIs, MQ Publishers  │
│    Module: patra-{service}-infra                            │
│    Depends: domain + mybatis starter + core starter         │
└─────────────────────────────────────────────────────────────┘
```

**关键概念**:
- **领域 (Domain - 核心)**: 纯业务逻辑,无外部依赖
- **端口 (Ports)**: 由领域定义的接口 (例如 `ProvenancePort`)
- **适配器 (Adapters)**: 连接外部系统的实现
  - **驱动适配器 (Driving Adapters - 入站)**: 接收外部触发 → `adapter` 模块
  - **被驱动适配器 (Driven Adapters - 出站)**: 访问外部资源 → `infra` 模块

**优势**:
- 可测试的业务逻辑 (纯 Java,领域无需 mock)
- 可交换的实现 (例如更换数据库而无需触碰领域)
- 清晰的关注点分离
</details>

### 原则 2: 领域驱动设计 (DDD)

<details>
<summary><b>查看 DDD 战术模式</b></summary>

**关键战术模式**:
- **聚合 (Aggregate)**: 一致性边界,包含根实体
- **实体 (Entity)**: 基于身份的对象 (例如 `BatchPlan`)
- **值对象 (Value Object)**: 不可变,按值相等 (例如使用 `record` 的 `LiteratureId`)
- **领域事件 (Domain Event)**: 捕获业务事实 (例如 `PlanCompletedEvent`)
- **仓储 (Repository)**: 类集合接口用于持久化
- **工厂 (Factory)**: 复杂对象创建

**优势**:
- 统一语言 (Ubiquitous Language - 代码匹配业务术语)
- 业务规则在领域层
- 演进式设计
</details>

---

## 📋 分层职责详解

### 1. Adapter 层 (驱动端 - 接收触发)

**职责**: 接收外部触发并委托给应用层

<details>
<summary><b>查看 REST 控制器示例</b></summary>

```java
@RestController
@RequestMapping("/api/v1/provenances")
@RequiredArgsConstructor
public class ProvenanceController {
    private final ProvenanceManagementUseCase useCase;

    @PostMapping
    public ResponseEntity<ProvenanceResponse> create(
        @Valid @RequestBody CreateProvenanceCommand command
    ) {
        ProvenanceResult result = useCase.create(command);
        return ResponseEntity.ok(ProvenanceResponse.from(result));
    }
}
```

**应该包含**:
- REST 控制器 (`@RestController`)
- 定时任务 (XXL-Job, `@XxlJob`)
- 消息消费者 (RocketMQ, `@RocketMQMessageListener`)
- 请求/响应 DTO、验证 (`@Valid`)

**不应该包含**:
- ❌ 业务逻辑 (属于领域)
- ❌ 数据库访问 (属于基础设施)
- ❌ 事务管理 (属于应用)
</details>

### 2. Application 层 (编排用例)

**职责**: 编排用例,管理事务,协调领域逻辑

<details>
<summary><b>查看编排者示例</b></summary>

```java
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {
    private final PlanPersistenceCoordinator persistenceCoordinator;
    private final PlanIdempotencyCoordinator idempotencyCoordinator;

    @Override
    @Transactional  // 事务边界在编排者层
    public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
        // 阶段 1: 准备规划上下文
        PlanningContext context = preparePlanningContext(command);

        // 阶段 2: 组装 plan (委托给领域)
        PlanAggregate plan = assemblePlan(context);

        // 阶段 3: 检查幂等性
        if (idempotencyCoordinator.exists(plan.getPlanKey())) {
            return idempotencyCoordinator.handleIdempotentReuse(plan);
        }

        // 阶段 4: 持久化并发布
        return persistenceCoordinator.saveAndPublish(plan);
    }
}
```

**应该包含**:
- 编排者 (`*Orchestrator.java`)
- 协调者 (`*Coordinator.java`)
- 用例命令/DTO、领域事件处理器
- 事务边界 (`@Transactional`)

**不应该包含**:
- ❌ 业务规则 (属于领域)
- ❌ 数据库查询 (使用端口)
- ❌ 事务内的外部 API 调用
</details>

### 3. Domain 层 (纯 Java - 业务逻辑)

**职责**: 纯业务逻辑和规则

<details>
<summary><b>查看聚合示例</b></summary>

```java
@Slf4j
@Data
public class BatchPlan {
    private BatchPlanId id;
    private PlanStatus status;
    private List<Slice> slices;

    public void markAsCompleted() {
        if (this.status == PlanStatus.CANCELLED) {
            throw new BatchPlanException("Cannot complete cancelled plan");
        }
        this.status = PlanStatus.COMPLETED;
        DomainEventPublisher.publish(new PlanCompletedEvent(this.id));
    }
}
```

**值对象示例**:

```java
public record LiteratureId(String value) {
    public LiteratureId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("LiteratureId cannot be null");
        }
    }
}
```

**允许的依赖**:
- ✅ Lombok (`@Data`, `@Slf4j` 等)
- ✅ Hutool 工具
- ✅ patra-common 基础类
- ✅ Java 标准库

**不应该包含**:
- ❌ Spring 注解 (`@Service`, `@Repository`, `@Autowired`)
- ❌ 数据库实体 (DO 属于基础设施)
- ❌ HTTP DTO (属于适配器)
</details>

### 4. Infrastructure 层 (被驱动端 - 资源访问)

**职责**: 实现领域端口,访问外部资源

<details>
<summary><b>查看仓储实现示例</b></summary>

```java
@Repository
@RequiredArgsConstructor
public class ProvenanceRepositoryImpl implements ProvenancePort {
    private final ProvenanceMapper mapper;
    private final ProvenanceConverter converter;

    @Override
    public Provenance findById(ProvenanceId id) {
        ProvenanceDO dataObject = mapper.selectById(id.getValue());
        return converter.toDomain(dataObject);  // DO → Domain
    }

    @Override
    public void save(Provenance provenance) {
        ProvenanceDO dataObject = converter.toDO(provenance);  // Domain → DO
        if (dataObject.getId() == null) {
            mapper.insert(dataObject);
        } else {
            mapper.updateById(dataObject);
        }
    }
}
```

**应该包含**:
- 仓储实现 (`*RepositoryImpl.java`)
- MyBatis-Plus DO (`*DO.java`)、Mapper
- MapStruct 转换器 (`*Converter.java`)
- 外部 API 客户端、MQ 发布者
</details>

---

## 🏗️ 应用层组织模式

Patra 使用两种互补模式,每种适用于不同场景。

### 模式对比速查表

| 维度 | 编排者 + 协调者 | 主编排者 + 子用例 |
|-----------|----------------------------|---------------------|
| **拆分策略** | 按关注点 | 按阶段 |
| **组件接口** | 无 | 有 |
| **事务边界** | 统一 | 分布式 |
| **组件大小** | 小 (100-200 行) | 大 (200-300 行) |
| **可复用性** | 低 | 高 |
| **用例** | 单事务 | 多阶段含外部调用 |

### 决策树

```
流程是否包含外部 API 调用?
  ├─ 是 → 使用模式 2 (主编排者 + 子用例)
  │         理由: 避免长事务
  └─ 否  → 流程是否需要多个独立事务?
            ├─ 是 → 使用模式 2
            │         理由: 阶段隔离
            └─ 否  → 使用模式 1 (编排者 + 协调者)
                      理由: 更简单,统一事务
```

<details>
<summary><b>模式 1: 编排者 + 协调者详解</b></summary>

**结构:**
```
MainOrchestrator (@Transactional)
  ├─ PersistenceCoordinator    (关注点: 数据持久化)
  ├─ IdempotencyCoordinator    (关注点: 重复检测)
  └─ PublishingCoordinator     (关注点: 事件发布)
```

**何时使用:**
- ✅ 整个流程在单个事务内
- ✅ 无外部 API 调用 (或很少)
- ✅ 关注点分离比阶段隔离更重要
- ✅ 组件无复用需求

**示例**:
```java
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {
  private final PlanPersistenceCoordinator persistenceCoordinator;
  private final PlanIdempotencyCoordinator idempotencyCoordinator;

  @Override
  @Transactional  // 整个流程的单个事务
  public PlanIngestionResult ingestPlan(PlanIngestionCommand request) {
    // 编排: 准备 → 验证 → 组装 → 持久化 → 发布
  }
}
```
</details>

<details>
<summary><b>模式 2: 主编排者 + 子用例详解</b></summary>

**结构:**
```
MainOrchestrator (无 @Transactional)
  ├─ PrepareUseCase     (阶段: 准备,隔离事务)
  ├─ ExecuteUseCase     (阶段: 执行,无事务)
  └─ CompleteUseCase    (阶段: 完成,@Transactional)
```

**何时使用:**
- ✅ 多阶段事务 (仅部分需要事务)
- ✅ 包含外部 API 调用 (不能在事务内)
- ✅ 清晰的生命周期和状态转换
- ✅ 子用例可能被其他流程复用

**示例**:
```java
@Service
@RequiredArgsConstructor
public class TaskExecutionUseCaseImpl implements TaskExecutionUseCase {
  private final PrepareTaskExecutionUseCase prepareUseCase;      // 无 @Transactional
  private final ExecuteTaskBatchesUseCase executeUseCase;        // 无 @Transactional
  private final CompleteTaskExecutionUseCase completeUseCase;    // @Transactional

  @Override
  public void execute(TaskReadyCommand command) {
    // 阶段 0: 准备 (可能快速失败)
    // 阶段 1: 执行 (外部 API 调用)
    // 阶段 2: 完成 (原子性的最终更新)
  }
}
```
</details>

### ⚠️ 事务管理黄金法则

**关键原则:**

1. **永远不要在 `@Transactional` 方法内调用外部 API**
   - 外部 API 可能需要 10+ 秒,持有数据库连接和锁
   - 使用模式 2 将外部调用与事务隔离

2. **最小化事务范围**
   - 仅包含必须原子执行的操作
   - 保持事务短暂

<details>
<summary><b>查看正确与错误示例</b></summary>

**❌ 错误方法:**
```java
@Transactional  // ❌ 错误: 长事务
public void execute() {
  prepare();           // 数据库检查
  callPubMedAPI();    // 10+ 秒,阻塞事务!
  saveResults();      // 数据库更新
}
```

**✅ 正确方法:**
```java
public void execute() {
  prepare();              // 无事务
  callPubMedAPI();       // 无事务
  complete();            // @Transactional - 仅最终更新
}
```
</details>

---

## 📚 设计模式参考

<details>
<summary><b>DDD 模式</b></summary>

- **聚合 (Aggregate)**: 一致性边界,包含根实体,强制不变量
- **实体 (Entity)**: 基于身份的对象 (ID 驱动的相等性)
- **值对象 (Value Object)**: 不可变,按值相等 (使用 `record`)
- **领域事件 (Domain Event)**: 捕获业务事实,触发反应
- **仓储 (Repository)**: 聚合持久化的类集合接口
- **工厂 (Factory)**: 复杂聚合创建逻辑
- **领域服务 (Domain Service)**: 跨聚合的无状态操作
</details>

<details>
<summary><b>GoF 模式 (Patra 中常见)</b></summary>

- **策略 (Strategy)**: 多算法实现 (例如每个来源的解析器)
- **工厂 (Factory)**: 对象创建 (例如 Provenance 创建)
- **模板方法 (Template Method)**: 带钩子点的算法骨架 (例如 AbstractProvenanceScheduleJob)
- **观察者 (Observer)**: 事件驱动反应 (领域事件)
- **适配器 (Adapter)**: 转换接口 (上下文间的 ACL)
</details>

<details>
<summary><b>集成模式</b></summary>

- **Outbox 模式 (Outbox Pattern)**: 使用数据库事务的可靠事件发布
- **幂等键 (Idempotency Key)**: 防止重复处理
- **重试与退避 (Retry with Backoff)**: 瞬态故障恢复 (指数退避)
- **防腐层 (Anti-Corruption Layer - ACL)**: 保护领域免受外部模型影响
</details>

---

## 📖 快速参考

### 新代码应该放在哪里?

| 问题 | 答案 |
|----------|--------|
| 接收 HTTP 请求? | `adapter/rest/*Controller.java` |
| 定时任务? | `adapter/scheduler/job/*Job.java` |
| 消息消费者? | `adapter/stream/*Consumers.java` |
| 编排用例? | `app/usecase/{feature}/*Orchestrator.java` |
| 业务逻辑? | `domain/model/entity/*.java` |
| 数据库访问? | `infra/persistence/repository/*RepositoryImpl.java` |
| MyBatis-Plus 实体? | `infra/persistence/entity/*DO.java` |
| 端口接口? | `domain/port/*Port.java` |

---

## 📚 相关文档

- [adapter-layer-patterns.md](adapter-layer-patterns.md) - XXL-Job、Template Method 模式
- [orchestrator-coordinator-patterns.md](orchestrator-coordinator-patterns.md) - 应用层深入解析
- [domain-modeling-patterns.md](domain-modeling-patterns.md) - DDD 战术模式
- [mybatis-plus-patterns.md](mybatis-plus-patterns.md) - 基础设施层持久化
- [outbox-pattern.md](outbox-pattern.md) - 可靠事件发布
- [event-driven-architecture.md](event-driven-architecture.md) - 领域事件、处理器
- [dependency-rules.md](dependency-rules.md) - ArchUnit 验证和依赖规则

---

**此架构在 Patra 中不可协商。违规将被 ArchUnit 测试和代码审查捕获。**

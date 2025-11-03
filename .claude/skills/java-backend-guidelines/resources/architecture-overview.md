# 六边形架构 + DDD 概览

Papertrace 架构模式完整指南,结合六边形架构 (Ports & Adapters) 与领域驱动设计 (Domain-Driven Design)。

---

## 目录

1. [核心架构原则](#核心架构原则)
2. [四层架构](#四层架构)
3. [依赖规则](#依赖规则-必须遵守)
4. [层职责](#层职责)
5. [适配器层组织](#适配器层组织)
6. [应用层模式](#应用层组织模式)
7. [设计模式参考](#设计模式参考)
8. [测试策略](#测试策略)

---

## 核心架构原则

### 1. 六边形架构 (Hexagonal Architecture - Ports & Adapters)

**目标**: 将业务逻辑与外部关注点 (HTTP、数据库、消息传递) 隔离。

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

---

### 2. 领域驱动设计 (Domain-Driven Design - DDD)

**目标**: 使代码结构与业务领域对齐。

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

---

## 四层架构

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

---

## 依赖规则 (必须遵守)

### 黄金法则

**依赖必须从外层流向内层:**

```
adapter  →  app + api (+ web starters)
app      →  domain + patra-common + core starter
infra    →  domain + mybatis starter + core starter
domain   →  patra-common + Lombok + Hutool (禁止 Spring!)
api      →  无框架依赖 (外部契约)
boot     →  所有模块 + Spring Boot starters
```

**⚠️ 违反这些规则是不可接受的!**

---

### 禁止的依赖

```java
// ❌ 永远不要: 领域依赖基础设施
package com.patra.ingest.domain.model.entity;
import com.patra.ingest.infra.persistence.entity.BatchPlanDO;  // 错误!

// ❌ 永远不要: 领域依赖 Spring
package com.patra.ingest.domain.model.entity;
import org.springframework.stereotype.Service;  // 错误!

// ❌ 永远不要: 应用直接依赖基础设施
package com.patra.ingest.app.usecase.plan;
import com.patra.ingest.infra.persistence.repository.ProvenanceRepositoryImpl;  // 错误!
// 应该依赖领域端口:
import com.patra.ingest.domain.port.ProvenancePort;  // 正确!
```

---

## 层职责

### 1. 适配器层 (驱动端 - Adapter Layer - Driving)

**模块**: `patra-{service}-adapter`

**职责**: 接收外部触发并委托给应用层。

**应该包含的内容**:
- REST 控制器 (`@RestController`)
- 定时任务 (XXL-Job, `@XxlJob`)
- 消息消费者 (RocketMQ, `@RocketMQMessageListener`)
- 请求/响应 DTO、验证 (`@Valid`)

**不应该包含的内容**:
- ❌ 业务逻辑 (属于领域)
- ❌ 数据库访问 (属于基础设施)
- ❌ 事务管理 (属于应用)

**示例**:
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

---

### 2. 应用层 (Application Layer)

**模块**: `patra-{service}-app`

**职责**: 编排用例,管理事务,协调领域逻辑。

**应该包含的内容**:
- 编排者 (`*Orchestrator.java`)
- 协调者 (`*Coordinator.java`)
- 用例命令/DTO、领域事件处理器
- 事务边界 (`@Transactional`)

**不应该包含的内容**:
- ❌ 业务规则 (属于领域)
- ❌ 数据库查询 (使用端口)
- ❌ 事务内的外部 API 调用

**参见**: 下方 [应用层组织模式](#应用层组织模式) 部分获取详细模式。

---

### 3. 领域层 (核心 - Domain Layer - Core)

**模块**: `patra-{service}-domain`

**职责**: 纯业务逻辑和规则。

**允许的依赖**:
- ✅ Lombok (`@Data`, `@Slf4j` 等)
- ✅ Hutool 工具
- ✅ patra-common 基础类
- ✅ Java 标准库

**不应该包含的内容**:
- ❌ Spring 注解 (`@Service`, `@Repository`, `@Autowired`)
- ❌ 数据库实体 (DO 属于基础设施)
- ❌ HTTP DTO (属于适配器)

**聚合示例**:
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

---

### 4. 基础设施层 (被驱动端 - Infrastructure Layer - Driven)

**模块**: `patra-{service}-infra`

**职责**: 实现领域端口,访问外部资源。

**应该包含的内容**:
- 仓储实现 (`*RepositoryImpl.java`)
- MyBatis-Plus DO (`*DO.java`)、Mapper
- MapStruct 转换器 (`*Converter.java`)
- 外部 API 客户端、MQ 发布者

**仓储实现示例**:
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

---

## 适配器层组织

### 模块级分离 (驱动 vs 被驱动)

Papertrace 使用 **模块级边界** 分离驱动和被驱动适配器:

```
patra-{service}-adapter/     ← 仅驱动适配器 (接收触发)
├── REST API、定时任务、消息消费者
└── 方向: 外部世界 → 系统

patra-{service}-infra/       ← 仅被驱动适配器 (访问资源)
├── DB 仓储、外部 API 客户端、MQ 发布者
└── 方向: 系统 → 外部资源
```

**关键原则**: 模块边界提供清晰的分离。无需在 adapter 模块内使用 `inbound/outbound/` 包。

---

### 包组织标准

**直接在 `adapter/` 下按适配器技术类型组织**:

```
com.patra.{service}.adapter/
├── rest/           REST API (Spring MVC, OpenAPI)
│   ├── internal/   (可选: 微服务之间)
│   └── public/     (可选: 面向外部)
├── scheduler/      定时任务 (XXL-Job)
│   ├── config/
│   ├── job/
│   └── param/
├── stream/         消息消费者 (RocketMQ, Kafka)
│   └── dto/
├── graphql/        GraphQL API (未来)
└── grpc/           gRPC API (未来)
```

### 为什么不使用 `inbound/` 或 `driving/`?

1. **模块名提供上下文**: `patra-{service}-adapter` 已经表明是适配器层
2. **模块契约是明确的**: `-adapter` 仅包含驱动适配器 (根据设计)
3. **语义冗余**: `adapter.inbound.rest` 不必要地重复了 "inbound"
4. **避免未来混淆**: 所有出站集成属于 `-infra` 模块

### 命名约定

| 适配器类型 | 命名模式 | 示例 |
|--------------|----------------|---------|
| REST 控制器 | `*Controller` 或 `*EndpointImpl` | `ProvenanceController` |
| 定时任务 | `*Job` | `PubmedHarvestJob` |
| 消息消费者 | `*MessageListener` | `TaskReadyMessageListener` |

---

## 应用层组织模式

应用层使用两种互补模式,每种适用于不同场景。

### 模式 1: 编排者 + 协调者 (Orchestrator + Coordinators)

**结构:**
```
MainOrchestrator (@Transactional)
  ├─ PersistenceCoordinator    (关注点: 数据持久化)
  ├─ IdempotencyCoordinator    (关注点: 重复检测)
  └─ PublishingCoordinator     (关注点: 事件发布)
```

**特征:**
- **按关注点分离 (Separation by Concern)**: 按业务关注点拆分
- **统一事务 (Unified Transaction)**: 主编排者持有 `@Transactional`
- **轻量委托 (Lightweight Delegation)**: 协调者是辅助类 (无接口)
- **线性流程 (Linear Flow)**: 适合顺序流程

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

---

### 模式 2: 主编排者 + 子用例 (Main Orchestrator + Sub-UseCases)

**结构:**
```
MainOrchestrator (无 @Transactional)
  ├─ PrepareUseCase     (阶段: 准备,隔离事务)
  ├─ ExecuteUseCase     (阶段: 执行,无事务)
  └─ CompleteUseCase    (阶段: 完成,@Transactional)
```

**特征:**
- **阶段隔离 (Phase Isolation)**: 按执行阶段拆分
- **独立复用 (Independent Reuse)**: 每个子用例有接口
- **分布式事务 (Distributed Transactions)**: 每个阶段可以有自己的事务边界
- **错误隔离 (Error Isolation)**: 每个阶段独立处理异常

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

---

### 事务管理黄金法则

**⚠️ 关键原则:**

1. **永远不要在 `@Transactional` 方法内调用外部 API**
   - 外部 API 可能需要 10+ 秒,持有数据库连接和锁
   - 使用模式 2 将外部调用与事务隔离

2. **最小化事务范围**
   - 仅包含必须原子执行的操作
   - 保持事务短暂

3. **错误方法示例:**
   ```java
   @Transactional  // ❌ 错误: 长事务
   public void execute() {
     prepare();           // 数据库检查
     callPubMedAPI();    // 10+ 秒,阻塞事务!
     saveResults();      // 数据库更新
   }
   ```

4. **正确方法示例:**
   ```java
   public void execute() {
     prepare();              // 无事务
     callPubMedAPI();       // 无事务
     complete();            // @Transactional - 仅最终更新
   }
   ```

---

### 模式对比

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

---

### 命名约定

| 模式 | 命名 | 职责 | 接口 | 事务 |
|---------|--------|---------------|-----------|-------------|
| **子用例 (Sub-UseCase)** | `*UseCase` / `*UseCaseImpl` | 完整业务阶段 | ✅ 有 | ✅ 独立 |
| **协调者 (Coordinator)** | `*Coordinator` | 协助编排者 | ❌ 无 | ❌ 继承 |
| **编排者 (Orchestrator)** | `*Orchestrator` | 主流程编排 | ✅ 有 | ✅ 控制 |

---

## 设计模式参考

### DDD 模式 (Domain-Driven Design)
- **聚合 (Aggregate)**: 一致性边界,包含根实体,强制不变量
- **实体 (Entity)**: 基于身份的对象 (ID 驱动的相等性)
- **值对象 (Value Object)**: 不可变,按值相等 (使用 `record`)
- **领域事件 (Domain Event)**: 捕获业务事实,触发反应
- **仓储 (Repository)**: 聚合持久化的类集合接口
- **工厂 (Factory)**: 复杂聚合创建逻辑
- **领域服务 (Domain Service)**: 跨聚合的无状态操作

### GoF 模式 (Papertrace 中常见)
- **策略 (Strategy)**: 多算法实现 (例如每个来源的解析器)
- **工厂 (Factory)**: 对象创建 (例如 Provenance 创建)
- **模板方法 (Template Method)**: 带钩子点的算法骨架 (例如 AbstractProvenanceScheduleJob)
- **观察者 (Observer)**: 事件驱动反应 (领域事件)
- **适配器 (Adapter)**: 转换接口 (上下文间的 ACL)

### 企业模式 (Enterprise Patterns - Fowler)
- **服务层 (Service Layer)**: 应用服务编排者
- **仓储 (Repository)**: 数据访问抽象 (端口/适配器)
- **数据映射器 (Data Mapper)**: DTO/DO ↔ Domain 映射 (MapStruct)
- **工作单元 (Unit of Work)**: 事务边界管理

### 集成模式 (Integration Patterns)
- **Outbox 模式 (Outbox Pattern)**: 使用数据库事务的可靠事件发布
- **幂等键 (Idempotency Key)**: 防止重复处理
- **重试与退避 (Retry with Backoff)**: 瞬态故障恢复 (指数退避)
- **防腐层 (Anti-Corruption Layer - ACL)**: 保护领域免受外部模型影响

### 数据模式 (Data Patterns)
- **乐观锁 (Optimistic Locking)**: 基于版本的并发控制 (`@Version`)
- **最终一致性 (Eventual Consistency)**: 通过事件的异步跨聚合更新
- **聚合持久化 (Aggregate Persistence)**: 原子性保存整个聚合

---

## 设计原则与哲学

### 核心原则
- **自包含用例 (Self-contained use cases)**: 每个用例目录有 command/dto/logic
- **命名约定 (Naming conventions)**: `*Orchestrator`, `*Command`, `*Port`, `*DO`
- **契约优先 (Contract-first)**: 定义 `*-api` 契约 → Domain → App → Infra → Adapter
- **简单优先 (Simplicity first)**: 解决当前问题,避免过度工程化
- **YAGNI**: You Aren't Gonna Need It - 不要为假设的未来构建
- **快速失败 (Fail fast)**: 早期验证,使错误明显

### 模式选择指南
- **从简单开始 (Start simple)**: 使用解决问题的最简单模式
- **需要时重构 (Refactor when needed)**: 第三次重复后抽象 (Rule of Three - 三次法则)
- **匹配上下文 (Match context)**: 选择适合团队技能的模式
- **考虑权衡 (Consider trade-offs)**: 每个模式都有复杂性成本 vs 灵活性收益

---

## 测试策略

### 领域层
- 纯 Java 单元测试,无需 mock
- 隔离测试业务规则

```java
@Test
void should_throw_exception_when_completing_cancelled_plan() {
    BatchPlan plan = new BatchPlan();
    plan.setStatus(PlanStatus.CANCELLED);
    assertThrows(BatchPlanException.class, () -> plan.markAsCompleted());
}
```

### 应用层
- Mock 端口 (领域接口)
- 测试编排逻辑

```java
@ExtendWith(MockitoExtension.class)
class PlanIngestionOrchestratorTest {
    @Mock private ProvenancePort provenancePort;
    @InjectMocks private PlanIngestionOrchestrator orchestrator;

    @Test
    void should_create_plan_successfully() {
        when(provenancePort.findById(any())).thenReturn(provenance);
        PlanIngestionResult result = orchestrator.ingest(command);
        assertThat(result.isSuccess()).isTrue();
    }
}
```

### 基础设施层
- 在 boot 模块中使用 TestContainers 的集成测试

```java
@SpringBootTest
@Testcontainers
class ProvenanceRepositoryImplTest {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired private ProvenancePort provenancePort;

    @Test
    void should_save_and_find_provenance() {
        provenancePort.save(provenance);
        Provenance found = provenancePort.findById(provenance.getId());
        assertThat(found).isNotNull();
    }
}
```

### ArchUnit (依赖验证)
```java
@Test
void domain_should_not_depend_on_spring() {
    noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAPackage("org.springframework..")
        .check(importedClasses);
}
```

---

## 快速参考

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

## 相关文件

- [adapter-layer-patterns.md](adapter-layer-patterns.md) - XXL-Job、Template Method 模式
- [orchestrator-coordinator-patterns.md](orchestrator-coordinator-patterns.md) - 应用层深入解析
- [domain-modeling-patterns.md](domain-modeling-patterns.md) - DDD 战术模式
- [mybatis-plus-patterns.md](mybatis-plus-patterns.md) - 基础设施层持久化
- [outbox-pattern.md](outbox-pattern.md) - 可靠事件发布
- [event-driven-architecture.md](event-driven-architecture.md) - 领域事件、处理器

---

**此架构在 Papertrace 中不可协商。违规将被 ArchUnit 测试和代码审查捕获。**

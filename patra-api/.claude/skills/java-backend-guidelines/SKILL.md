---
name: java-backend-guidelines
description: Comprehensive Java backend development guide for Hexagonal Architecture + DDD with Spring Boot 3.5.7. Use when creating REST endpoints, orchestrators, coordinators, domain entities, aggregates, repositories, scheduled jobs (XXL-Job), event handlers (@TransactionalEventListener), or working with MyBatis-Plus, MapStruct, validation, Nacos configuration, Outbox pattern (relay, lease coordination, exponential backoff), event-driven architecture (domain events, event chains, AFTER_COMMIT, REQUIRES_NEW), or testing strategies. Covers four-layer architecture (Adapter → Application → Domain ← Infrastructure), dependency directions, transaction management (@Transactional), error handling (ProblemDetail, OptimisticLockingFailureException), idempotency, and performance optimization. Includes patra-ingest patterns: OutboxRelayOrchestrator, RelayCoordinator (lease/publish/log), TaskCompletedEvent, SliceStatusChangedEvent, PlanIngestionOrchestrator, AbstractProvenanceScheduleJob, Template Method pattern.
---

# Java 后端开发指南 (Papertrace)

## 目的

在 Papertrace 后端微服务(**patra-ingest**、**patra-registry**、**patra-gateway**)中建立一致性和最佳实践,使用 **六边形架构 + DDD(领域驱动设计)** 结合 Spring Boot 3.5.7、Java 25 和 MyBatis-Plus。

**核心原则**: 领域驱动设计,清晰的层次边界、纯粹的领域逻辑和依赖倒置。

---

## 何时使用此技能

当处理以下工作时自动激活:

**适配器层 (Adapter Layer):**
- 创建或修改 REST 端点、API (@RestController)
- 构建定时任务 (XXL-Job)
- 实现消息消费者 (RocketMQ, Kafka)

**应用层 (Application Layer):**
- 实现编排者 (use case coordination - 用例协调)
- 创建协调者 (concern separation - 关注点分离)
- 管理事务 (@Transactional)
- 跨聚合协调

**领域层 (Domain Layer):**
- 建模聚合、实体、值对象
- 定义端口接口
- 领域事件和业务规则
- 工厂模式

**基础设施层 (Infrastructure Layer):**
- MyBatis-Plus 仓储
- MapStruct DO ↔ Domain 映射
- 外部 API 集成
- 数据库访问模式

**横切关注点 (Cross-Cutting):**
- 验证和错误处理
- 配置管理 (Nacos)
- Outbox 模式实现
- 性能优化
- 测试 (单元测试、集成测试、ArchUnit 测试)

---

## 快速开始

### 新后端功能检查清单

```
□ 在应用层定义用例
  ├─ 创建 command/dto 类
  ├─ 实现带 @Transactional 的编排者
  └─ (可选) 创建协调者以分离关注点

□ 建模领域逻辑
  ├─ 设计聚合根和实体
  ├─ 定义值对象 (使用 record 表示不可变对象)
  ├─ 创建端口接口
  └─ 在领域中实现业务规则

□ 构建适配器 (驱动端 - Driving)
  ├─ 使用 @Valid 验证的 REST Controller
  ├─ 或定时任务 (XXL-Job)
  ├─ 或消息消费者 (RocketMQ)
  └─ 委托给编排者

□ 实现基础设施 (被驱动端 - Driven)
  ├─ 创建 MyBatis-Plus DO 实体
  ├─ 使用 MapStruct 转换器实现仓储
  ├─ 定义 XML mapper (用于复杂查询)
  └─ 永远不要在基础设施层外部暴露 DO

□ 测试
  ├─ 领域单元测试 (纯 Java,无 mock)
  ├─ 应用单元测试 (mock 端口)
  ├─ 在 boot 模块中集成测试 (TestContainers)
  └─ ArchUnit 测试验证依赖规则
```

### 新微服务检查清单

```
□ 模块结构
  ├─ patra-{service}-api (外部契约)
  ├─ patra-{service}-domain (纯 Java, Lombok, Hutool)
  ├─ patra-{service}-app (编排者, @Transactional)
  ├─ patra-{service}-infra (MyBatis-Plus, MapStruct)
  ├─ patra-{service}-adapter (REST, Jobs, Streams)
  └─ patra-{service}-boot (主应用, Spring 配置)

□ 核心组件
  ├─ Nacos 配置
  ├─ 错误映射 (ProblemDetail)
  ├─ 验证框架 (@Valid)
  ├─ Outbox 模式设置
  ├─ 可观测性 (SLF4J, Micrometer)
  └─ ArchUnit 验证

□ 复用 Starter
  ├─ patra-spring-boot-starter-core (基础配置)
  ├─ patra-spring-boot-starter-web (MVC, 验证)
  └─ patra-spring-boot-starter-mybatis (MyBatis-Plus)
```

---

## 架构概览

### 六边形架构 (Hexagonal Architecture - Ports & Adapters) + DDD

**四层结构** (外层 → 内层,依赖流向内部):

```
┌─────────────────────────────────────────────────┐
│  Adapter Layer (Driving Side - Outermost)      │
│  REST Controllers, Jobs, Message Consumers      │
│  ↓ depends on ↓                                 │
├─────────────────────────────────────────────────┤
│  Application Layer                              │
│  Orchestrators, Coordinators, Use Cases         │
│  ↓ depends on ↓                                 │
├─────────────────────────────────────────────────┤
│  Domain Layer (Core - Pure Java)                │
│  Aggregates, Entities, VOs, Events, Ports       │
│  ↑ implemented by ↑                             │
├─────────────────────────────────────────────────┤
│  Infrastructure Layer (Driven Side)             │
│  Repositories, External APIs, DB Access         │
└─────────────────────────────────────────────────┘
```

**关键原则:**
1. **依赖流向内部**: Adapter → App → Domain ← Infra
2. **领域层是纯 Java**: 不包含 Spring/框架依赖 (仅允许 Lombok, Hutool, patra-common)
3. **端口与适配器 (Ports & Adapters)**: 领域定义端口(接口),基础设施实现适配器
4. **编排者协调 (Orchestrators coordinate)**: 应用层编排,领域层决策

**参见:** [architecture-overview.md](resources/architecture-overview.md) 获取完整详情。

---

## 目录结构

### 多模块 Maven 项目

```
patra-{service}/
├── patra-{service}-api/               # 外部契约
│   ├── dto/                           # 请求/响应 DTO
│   ├── error/                         # 错误码
│   └── 无框架依赖
│
├── patra-{service}-domain/            # 领域层 (纯 Java)
│   ├── model/
│   │   ├── entity/                    # 聚合 & 实体
│   │   ├── vo/                        # 值对象 (record)
│   │   └── enums/                     # 领域枚举
│   ├── port/                          # 端口接口
│   ├── factory/                       # 领域工厂
│   ├── event/                         # 领域事件
│   └── 依赖: 仅 patra-common + Lombok + Hutool
│
├── patra-{service}-app/               # 应用层
│   ├── usecase/
│   │   └── {feature}/                 # 自包含用例
│   │       ├── *Orchestrator.java     # 主编排者 (@Transactional)
│   │       ├── *Coordinator.java      # 协调者 (可选)
│   │       ├── command/               # 命令
│   │       └── dto/                   # 内部 DTO
│   ├── eventhandler/                  # 领域事件处理器
│   ├── outbox/                        # Outbox 模式
│   └── 依赖: domain + patra-common + core starter
│
├── patra-{service}-infra/             # 基础设施层 (被驱动适配器)
│   ├── persistence/
│   │   ├── entity/                    # MyBatis-Plus DO (*DO.java)
│   │   ├── mapper/                    # MyBatis-Plus Mapper
│   │   ├── converter/                 # MapStruct 转换器
│   │   └── repository/                # 仓储实现 (*RepositoryImpl.java)
│   ├── integration/                   # 外部 API 适配器
│   ├── messaging/                     # MQ 发布者
│   └── 依赖: domain + mybatis starter + core starter
│
├── patra-{service}-adapter/           # 适配器层 (驱动适配器)
│   ├── rest/                          # REST API
│   │   ├── *Controller.java           # Spring MVC 控制器
│   │   └── dto/                       # 控制器 DTO
│   ├── scheduler/                     # 定时任务
│   │   ├── config/                    # XXL-Job 配置
│   │   ├── job/                       # *Job.java
│   │   └── param/                     # 任务参数
│   └── stream/                        # 消息消费者
│       └── *Consumers.java            # RocketMQ/Kafka 消费者
│
└── patra-{service}-boot/              # 主应用
    ├── config/                        # Spring 配置
    ├── PatraXxxApplication.java       # @SpringBootApplication
    └── application.yml                # 配置 (仅本地,使用 Nacos)
```

**模块依赖 (必须遵守):**

```
adapter  →  app + api (+ web starters)
app      →  domain + patra-common + core starter
infra    →  domain + mybatis starter + core starter
domain   →  patra-common + Lombok + Hutool (禁止 Spring!)
api      →  无框架依赖 (外部契约)
boot     →  所有模块 + Spring Boot starters
```

**⚠️ 违反依赖规则是不可接受的!**

---

## 命名约定

### Java 通用命名

| 元素 | 约定 | 示例 | 注释 |
|---------|-----------|---------|-------|
| **包名 (Package)** | 小写 | `com.patra.ingest.domain` | 无分隔符 |
| **类名 (Class)** | 大驼峰 | `BatchPlan`, `Provenance` | 名词 |
| **接口 (Interface)** | 大驼峰 | `ProvenancePort` | 无 `I` 前缀 |
| **方法 (Method)** | 小驼峰 | `createPlan()` | 动词, 布尔: `is/has/can` |
| **变量 (Variable)** | 小驼峰 | `provenanceId` | 名词 |
| **常量 (Constant)** | 大写下划线 | `MAX_BATCH_SIZE` | `static final` |
| **枚举类型 (Enum Type)** | 大驼峰 | `BatchStatus` | 名词 |
| **枚举值 (Enum Value)** | 大写下划线 | `PENDING`, `RUNNING` | - |

### DDD/六边形架构命名模式

| 层 | 模式 | 示例 | 注释 |
|-------|---------|---------|-------|
| **领域实体 (Domain Entity)** | 无后缀 | `BatchPlan`, `Provenance` | 聚合根和实体 |
| **值对象 (Value Object)** | 无后缀 | `LiteratureId` | 不可变对象使用 `record` |
| **端口接口 (Port Interface)** | `Port` | `ProvenancePort`, `LiteraturePort` | 领域定义的接口 |
| **领域事件 (Domain Event)** | `Event` | `PlanCreatedEvent` | 过去时态 |
| **工厂 (Factory)** | `Factory` | `OutboxMessageFactory` | - |
| **编排者 (Orchestrator)** | `Orchestrator` | `PlanIngestionOrchestrator` | 用例协调者 |
| **协调者 (Coordinator)** | `Coordinator` | `PlanPersistenceCoordinator` | 关注点分离 |
| **控制器 (Controller)** | `Controller` | `ProvenanceController` | REST 适配器 |
| **定时任务 (Job)** | `Job` | `PubmedHarvestJob` | 定时任务 |
| **消费者 (Consumer)** | `Consumers` | `IngestStreamConsumers` | 消息消费者 |
| **仓储实现 (Repository Impl)** | `RepositoryImpl` | `ProvenanceRepositoryImpl` | 端口实现 |
| **DO (数据对象 - Data Object)** | `DO` | `ProvenanceDO` | MyBatis-Plus 实体 |
| **转换器 (Converter)** | `Converter` | `ProvenanceConverter` | MapStruct mapper |
| **命令 (Command)** | `Command` | `CreateProvenanceCommand` | 请求 DTO |
| **响应 (Response)** | `Response` 或 `Result` | `ProvenanceResponse` | 响应 DTO |

### 文件命名示例

```
✅ 良好:
- ProvenanceController.java           (REST 适配器)
- PlanIngestionOrchestrator.java      (应用编排者)
- BatchPlan.java                      (领域聚合)
- ProvenancePort.java                 (领域端口)
- ProvenanceRepositoryImpl.java       (基础设施实现)
- ProvenanceDO.java                   (MyBatis-Plus 实体)
- ProvenanceConverter.java            (MapStruct 转换器)
- LiteratureId.java (record)          (值对象)
- PlanCreatedEvent.java               (领域事件)

❌ 不良:
- IProvenancePort.java                (不要使用 "I" 前缀)
- ProvenanceRepositoryImplementation  (过于冗长)
- ProvenanceEntity.java               (领域中不要使用 "Entity" 后缀)
- ProvenanceDAO.java                  (不要使用 "DAO",使用 "RepositoryImpl")
```

---

## 分层指南

### 适配器层 (驱动端 - Adapter Layer - Driving)

**目的:** 接收外部触发并委托给应用层

**REST 控制器:**
```java
@RestController
@RequestMapping("/api/v1/provenances")
@RequiredArgsConstructor
public class ProvenanceController {
    private final ProvenanceManagementUseCase provenanceManagementUseCase;

    @PostMapping
    public ResponseEntity<ProvenanceResponse> create(
        @Valid @RequestBody CreateProvenanceCommand command
    ) {
        // 通过 @Valid 进行验证
        // 委托给编排者
        ProvenanceResult result = provenanceManagementUseCase.create(command);
        return ResponseEntity.ok(ProvenanceResponse.from(result));
    }
}
```

**关键要点:**
- ✅ 使用 `@Valid` 进行验证
- ✅ 委托给编排者,不包含业务逻辑
- ✅ 将结果映射到响应 DTO
- ✅ 使用 ProblemDetail 进行错误响应
- ❌ 不直接调用基础设施层

**参见:** [adapter-layer-patterns.md](resources/adapter-layer-patterns.md)

---

### 应用层 (编排 - Application Layer - Orchestration)

**目的:** 协调用例,管理事务,不包含业务规则

**编排者模式 (Orchestrator Pattern):**
```java
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {
    private final PlanPersistenceCoordinator persistenceCoordinator;
    private final PlanIdempotencyCoordinator idempotencyCoordinator;
    private final PlanPublishingCoordinator publishingCoordinator;

    @Override
    @Transactional  // 事务边界在编排者层
    public PlanIngestionResult ingest(PlanIngestionCommand command) {
        // 阶段 1: 准备
        // 阶段 2: 验证
        // 阶段 3: 组装 (委托给领域)
        // 阶段 4: 检查幂等性
        // 阶段 5: 持久化 (委托给协调者)
        // 阶段 6: 发布事件 (委托给协调者)
    }
}
```

**关键要点:**
- ✅ 仅编排,将业务逻辑委托给领域
- ✅ 管理事务 (@Transactional)
- ✅ 协调多个协调者
- ❌ 不包含业务规则 (属于领域层)

**参见:** [orchestrator-coordinator-patterns.md](resources/orchestrator-coordinator-patterns.md)

---

### 领域层 (业务逻辑 - Domain Layer - Business Logic)

**目的:** 纯业务逻辑,无框架依赖

**聚合示例:**
```java
@Slf4j
@Data  // Lombok (允许)
public class BatchPlan {
    private BatchPlanId id;
    private ProvenanceId provenanceId;
    private PlanStatus status;
    private List<Slice> slices;

    // 业务逻辑方法
    public void markAsCompleted() {
        // 业务规则验证
        if (this.status == PlanStatus.CANCELLED) {
            throw new BatchPlanException("Cannot complete cancelled plan");
        }
        this.status = PlanStatus.COMPLETED;
        // 发出领域事件
        DomainEventPublisher.publish(new PlanCompletedEvent(this.id));
    }
}
```

**关键要点:**
- ✅ 纯 Java (允许 Lombok, Hutool)
- ✅ 业务规则在领域方法中
- ✅ 使用领域事件进行跨聚合通信
- ❌ 不使用 Spring 注解 (@Service, @Autowired 等)
- ❌ 无框架依赖

**参见:** [domain-modeling-patterns.md](resources/domain-modeling-patterns.md)

---

### 基础设施层 (被驱动端 - Infrastructure Layer - Driven)

**目的:** 实现领域端口,提供数据访问

**仓储实现:**
```java
@Repository
@RequiredArgsConstructor
public class ProvenanceRepositoryImpl implements ProvenancePort {
    private final ProvenanceMapper mapper;
    private final ProvenanceConverter converter;

    @Override
    public Provenance findById(ProvenanceId id) {
        ProvenanceDO dataObject = mapper.selectById(id.getValue());
        return converter.toDomain(dataObject);  // MapStruct 转换
    }

    @Override
    public void save(Provenance provenance) {
        ProvenanceDO dataObject = converter.toDO(provenance);
        mapper.insert(dataObject);
    }
}
```

**关键要点:**
- ✅ 实现领域端口接口
- ✅ 使用 MapStruct 进行 DO ↔ Domain 转换
- ✅ MyBatis-Plus 用于简单查询,XML 用于复杂查询
- ❌ 永远不要在基础设施层外部暴露 DO

**参见:** [mybatis-plus-patterns.md](resources/mybatis-plus-patterns.md)

---

## 资源文件

特定主题的详细指南:

### 架构与设计
- ✅ **[architecture-overview.md](resources/architecture-overview.md)** (704 行) - 完整的六边形架构 + DDD
- ✅ **[dependency-rules.md](resources/dependency-rules.md)** (577 行) - 层依赖和验证

### 分层模式
- ✅ **[adapter-layer-patterns.md](resources/adapter-layer-patterns.md)** (490 行) - REST、Jobs、Consumers (XXL-Job, Template Method 模式)
- ✅ **[orchestrator-coordinator-patterns.md](resources/orchestrator-coordinator-patterns.md)** (1,142 行) - 应用层组织
- ✅ **[domain-modeling-patterns.md](resources/domain-modeling-patterns.md)** (1,296 行) - 聚合、实体、值对象、事件
- ✅ **[mybatis-plus-patterns.md](resources/mybatis-plus-patterns.md)** (1,263 行) - 数据库访问和映射

### 横切关注点
- ✅ **[transaction-error-handling.md](resources/transaction-error-handling.md)** (682 行) - @Transactional、ProblemDetail、乐观锁
- ✅ **[observability-guide.md](resources/observability-guide.md)** (791 行) - SLF4J 日志、MDC、Micrometer 指标、错误处理、日志测试

### 高级主题
- ✅ **[outbox-pattern.md](resources/outbox-pattern.md)** (962 行) - 可靠事件发布与 patra-ingest outbox 实现
- ✅ **[event-driven-architecture.md](resources/event-driven-architecture.md)** (507 行) - 领域事件、@TransactionalEventListener、事件链
- ✅ **[testing-guide.md](resources/testing-guide.md)** (2,478 行) - 完整测试指南: 单元测试、集成测试、事件驱动测试、Outbox 模式测试、ArchUnit 测试

### 完整示例
- ✅ **[complete-examples.md](resources/complete-examples.md)** (1,167 行) - 来自 patra-ingest 的完整 Plan Ingestion 功能

---

## 常见反模式避免

### ❌ 领域层违规

```java
// ❌ 错误: 在领域中使用 Spring 注解
@Service  // 不! 领域是纯 Java
public class BatchPlan {
    @Autowired  // 不! 领域中不使用依赖注入
    private ProvenancePort provenancePort;
}

// ✅ 正确: 使用 Lombok 的纯 Java
@Data
public class BatchPlan {
    private ProvenanceId provenanceId;

    // 仅业务逻辑
    public void validate() { /* ... */ }
}
```

### ❌ 错误的依赖方向

```java
// ❌ 错误: 领域依赖基础设施
// domain/model/entity/BatchPlan.java
import com.patra.ingest.infra.persistence.entity.BatchPlanDO;  // 不!

// ✅ 正确: 基础设施依赖领域
// infra/persistence/repository/BatchPlanRepositoryImpl.java
import com.patra.ingest.domain.model.entity.BatchPlan;  // 是!
```

### ❌ 业务逻辑在错误的层

```java
// ❌ 错误: 在控制器中的业务逻辑
@PostMapping
public ResponseEntity<ProvenanceResponse> create(@RequestBody CreateCommand command) {
    if (command.getName().length() < 3) {  // 不! 这是业务逻辑
        throw new ValidationException("Name too short");
    }
    // ...
}

// ✅ 正确: 业务逻辑在领域中
// 领域层
public class Provenance {
    public void setName(String name) {
        if (name.length() < 3) {  // 是! 业务规则在领域中
            throw new ProvenanceException("Name must be at least 3 characters");
        }
        this.name = name;
    }
}
```

### ❌ 在基础设施外暴露 DO

```java
// ❌ 错误: 从仓储返回 DO
public interface ProvenancePort {
    ProvenanceDO findById(Long id);  // 不! 永远不要暴露 DO
}

// ✅ 正确: 返回领域实体
public interface ProvenancePort {
    Provenance findById(ProvenanceId id);  // 是! 返回领域实体
}
```

---

## 快速决策树

**需要添加新功能?**

1. **触发器来自哪里?**
   - REST API → 在 `adapter/rest/` 创建 Controller
   - 定时任务 → 在 `adapter/scheduler/job/` 创建 Job
   - 消息队列 → 在 `adapter/stream/` 创建 Consumer

2. **谁编排用例?**
   - 在 `app/usecase/{feature}/` 创建 Orchestrator
   - 如果关注点复杂则添加 Coordinator

3. **业务规则是什么?**
   - 在领域层建模 (`domain/model/entity/`)
   - 在 `domain/port/` 定义端口

4. **如何持久化数据?**
   - 在 `infra/persistence/entity/` 创建 DO
   - 在 `infra/persistence/repository/` 实现仓储
   - 使用 MapStruct 转换器

5. **如何测试?**
   - 领域: 纯 Java 单元测试
   - 应用: Mock 端口,测试编排
   - 基础设施: 在 boot 模块集成测试 (TestContainers)
   - 适配器: MockMvc 用于 REST,集成测试用于 jobs

---

## 获取帮助

**不确定代码放在哪里?**
1. 问: "这段代码包含业务规则吗?" → 领域层
2. 问: "这段代码协调用例吗?" → 应用层
3. 问: "这段代码接收外部触发吗?" → 适配器层
4. 问: "这段代码访问外部资源吗?" → 基础设施层

**依赖违规?**
- 检查: [dependency-rules.md](resources/dependency-rules.md)
- 运行: ArchUnit 测试进行验证

**事务管理?**
- 检查: [transaction-error-handling.md](resources/transaction-error-handling.md)
- 规则: @Transactional 仅在编排者层

**性能问题?**
- 检查: [mybatis-plus-patterns.md](resources/mybatis-plus-patterns.md) - 批量操作、分页
- 检查: [testing-guide.md](resources/testing-guide.md#test-coverage-strategy) - 性能测试策略
- 注意: N+1 查询、缺失索引、大批量大小

---

## 代码风格参考

**所有代码必须遵循 [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)**

**关键亮点:**
- 2 空格缩进 (不是 4)
- 最大行长度: 100 字符
- 控制结构始终使用大括号
- 每个声明一个变量
- 静态导入仅用于常量

**参见:** Papertrace 的 AGENTS-development.md 获取完整编码标准。

---

**下一步:**
1. 浏览资源文件进行深入研究
2. 查看 [complete-examples.md](resources/complete-examples.md) 获取完整功能实现
3. 运行 ArchUnit 测试验证层依赖
4. 参考 [testing-guide.md](resources/testing-guide.md) 获取 TDD 工作流

**此技能在编辑 patra-* 模块中的 Java 文件或提示中提到六边形架构、DDD、编排者、领域或相关关键字时自动激活。**

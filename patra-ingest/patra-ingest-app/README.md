# patra-ingest-app — 摄入应用层

## 概述

`patra-ingest-app` 是 **patra-ingest 服务的应用层(Application Layer)**,负责**用例编排(Use Case Orchestration)**和**应用服务协调**。遵循**六边形架构**原则,位于 Domain 层之上,协调领域对象完成业务流程。

本模块在六边形架构中位于**应用层**,主要职责包括:
- **用例编排**: PlanIngestionOrchestrator、OutboxRelayOrchestrator、TaskExecutionUseCase
- **事务边界管理**: 通过 `@Transactional` 确保业务操作的原子性
- **领域事件处理**: TaskCompletedEventHandler、SliceStatusChangedEventHandler
- **Outbox 发布**: TaskOutboxPublisher、MetadataRecordRetryPublisher
- **表达式编译集成**: 调用 patra-expr-kernel 编译表达式

**架构约束**: 应用层依赖 Domain 层,但不直接依赖 Infra 层(通过端口接口解耦)。

---

## 核心职责

- **Plan 摄入编排**: 协调配置加载、窗口解析、表达式编译、计划装配、任务发布全流程
- **Task 执行编排**: 管理任务执行会话、批次规划、租约管理、进度跟踪
- **Outbox 中继**: 轮询 Outbox 表,可靠地将领域事件发布到 MQ
- **事件驱动协调**: 处理领域事件,驱动跨聚合的状态更新
- **表达式编译**: 集成 patra-expr-kernel,编译 Plan 表达式为可执行参数

---

## 模块结构

```
patra-ingest-app/
└─ src/main/java/.../app/
   ├─ usecase/                          # 用例编排层
   │  ├─ plan/                           # Plan 摄入用例
   │  │  ├─ PlanIngestionOrchestrator.java      # 主编排器(事务边界)
   │  │  ├─ PlanIngestionUseCase.java           # 用例接口
   │  │  ├─ PlanPersistenceCoordinator.java     # 持久化协调器
   │  │  ├─ PlanIdempotencyCoordinator.java     # 幂等性协调器
   │  │  ├─ PlanPublishingCoordinator.java      # 发布协调器
   │  │  ├─ command/
   │  │  │  └─ PlanIngestionCommand.java        # 输入命令
   │  │  ├─ dto/
   │  │  │  ├─ PlanIngestionResult.java         # 输出结果
   │  │  │  └─ PlanAssemblyResult.java          # 装配结果
   │  │  ├─ assembler/
   │  │  │  ├─ PlanAssembler.java               # 计划装配器
   │  │  │  └─ PlanAssemblyRequest.java         # 装配请求
   │  │  ├─ slicer/                             # 切片规划器
   │  │  │  ├─ SlicePlanner.java                # 切片规划器接口
   │  │  │  ├─ SlicePlannerRegistry.java        # 规划器注册表
   │  │  │  ├─ TimeSlicePlanner.java            # 时间切片规划器
   │  │  │  ├─ DateSlicePlanner.java            # 日期切片规划器
   │  │  │  └─ SingleSlicePlanner.java          # 单切片规划器
   │  │  ├─ expression/
   │  │  │  ├─ PlanExpressionBuilder.java       # 表达式构建器
   │  │  │  └─ PlanExpressionDescriptor.java    # 表达式描述符
   │  │  ├─ window/
   │  │  │  ├─ PlanningWindowResolver.java      # 窗口解析器
   │  │  │  └─ PlanningWindowSupport.java       # 窗口解析支持
   │  │  ├─ validator/
   │  │  │  └─ PlannerValidator.java            # 预验证器
   │  │  └─ publisher/
   │  │     ├─ TaskOutboxPublisher.java         # 任务 Outbox 发布器
   │  │     ├─ TaskHeaders.java                 # 任务消息头
   │  │     └─ TaskPayload.java                 # 任务消息负载
   │  ├─ execution/                      # Task 执行用例
   │  │  ├─ TaskExecutionUseCase.java           # 任务执行用例接口
   │  │  ├─ prepare/
   │  │  │  └─ PrepareTaskExecutionUseCase.java # 准备任务执行
   │  │  ├─ complete/
   │  │  │  └─ CompleteTaskExecutionUseCase.java # 完成任务执行
   │  │  ├─ session/
   │  │  │  ├─ ExecutionSessionManager.java     # 执行会话管理器
   │  │  │  ├─ ExecutionContextLoader.java      # 执行上下文加载器
   │  │  │  └─ ExecutionSession.java            # 执行会话
   │  │  ├─ lease/
   │  │  │  ├─ LeaseManagementService.java      # 租约管理服务
   │  │  │  └─ HeartbeatRenewalService.java     # 心跳续约服务
   │  │  ├─ strategy/
   │  │  │  ├─ ExecuteTaskBatchesUseCase.java   # 批次执行用例
   │  │  │  └─ planner/
   │  │  │     ├─ BatchPlanner.java             # 批次规划器接口
   │  │  │     ├─ BatchPlannerRegistry.java     # 规划器注册表
   │  │  │     └─ PubmedBatchPlanner.java       # PubMed 批次规划器
   │  │  ├─ cursor/
   │  │  │  └─ CursorAdvancer.java              # 游标推进器
   │  │  ├─ coordination/
   │  │  │  ├─ GenericBatchExecutor.java        # 通用批次执行器
   │  │  │  └─ LiteraturePublisherOrchestrator.java # 文献发布编排器
   │  │  └─ publisher/
   │  │     └─ LiteratureEventPublisher.java    # 文献事件发布器
   │  └─ relay/                          # Outbox 中继用例
   │     ├─ OutboxRelayOrchestrator.java        # Outbox 中继编排器
   │     ├─ OutboxRelayUseCase.java             # 中继用例接口
   │     ├─ command/
   │     │  └─ OutboxRelayCommand.java          # 中继命令
   │     ├─ dto/
   │     │  └─ RelayReport.java                 # 中继报告
   │     ├─ coordinator/
   │     │  ├─ RelayLeaseCoordinator.java       # 中继租约协调器
   │     │  ├─ RelayLogCoordinator.java         # 中继日志协调器
   │     │  └─ RelayPublishCoordinator.java     # 中继发布协调器
   │     ├─ executor/
   │     │  └─ OutboxRelayExecutor.java         # 中继执行器
   │     ├─ planner/
   │     │  └─ RelayPlanBuilder.java            # 中继计划构建器
   │     ├─ publisher/
   │     │  ├─ RelayEventPublisher.java         # 中继事件发布器
   │     │  └─ LoggingRelayEventPublisher.java  # 日志发布器(测试用)
   │     ├─ classifier/
   │     │  └─ RelayErrorClassifierImpl.java    # 错误分类器实现
   │     ├─ metrics/
   │     │  └─ OutboxRelayMetrics.java          # 中继指标
   │     └─ config/
   │        ├─ OutboxRelayConfiguration.java    # 中继配置
   │        └─ OutboxRelayProperties.java       # 中继属性
   ├─ eventhandler/                      # 领域事件处理器
   │  ├─ TaskCompletedEventHandler.java         # 任务完成事件处理器
   │  └─ SliceStatusChangedEventHandler.java    # 切片状态变更处理器
   ├─ outbox/                            # Outbox 模式组件
   │  ├─ core/
   │  │  ├─ AbstractOutboxPublisher.java        # Outbox 发布器抽象基类
   │  │  ├─ OutboxPublishContext.java           # 发布上下文
   │  │  └─ OutboxPublishResult.java            # 发布结果
   │  ├─ publisher/
   │  │  └─ MetadataRecordRetryPublisher.java   # 元数据重试发布器
   │  ├─ constants/
   │  │  ├─ OutboxAggregateTypes.java           # 聚合类型常量
   │  │  ├─ OutboxChannels.java                 # 通道常量
   │  │  └─ OutboxBusinessTags.java             # 业务标签常量
   │  ├─ metrics/
   │  │  └─ OutboxMetrics.java                  # Outbox 指标
   │  └─ config/
   │     └─ OutboxPublisherProperties.java      # 发布器属性
   └─ config/
      └─ IngestAppConfig.java                   # 应用层配置类
```

---

## 主要组件

### 核心编排器

#### 1. PlanIngestionOrchestrator (计划摄入编排器)

**职责**: 协调 Plan 摄入的完整生命周期,从配置加载到任务发布。

**编排流程**:
1. **Phase 1**: 持久化调度实例 + 加载 Provenance 配置快照
2. **Phase 2**: 查询游标水位线 + 解析规划窗口
3. **Phase 3**: 构建 Plan 表达式(未编译快照)
4. **Phase 4**: 预验证(窗口合法性、背压检查、容量检查)
5. **Phase 5**: 装配 Plan/Slice/Task(带幂等性检查)
6. **Phase 6**: 持久化 Plan → Slice → Task(事务内)
7. **Phase 7**: 收集 TaskQueuedEvent → 发布到 Outbox

**事务边界**: 整个方法使用 `@Transactional`,确保 Plan 持久化和 Outbox 发布的原子性。

**关键方法**:
```java
@Transactional
public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
    // 1. 准备规划上下文
    PlanningContext context = preparePlanningContext(command);

    // 2. 装配计划(带幂等性处理)
    PlanAssemblyResult assembly = assemblePlanWithIdempotency(context);

    // 3. 持久化计划
    PlanAggregate persistedPlan = persistenceCoordinator.persistPlan(assembly);

    // 4. 发布任务事件
    publishingCoordinator.publishTaskEvents(persistedPlan, assembly.getTasks());

    return PlanIngestionResult.success(persistedPlan.getId(), assembly.getTaskCount());
}
```

**文件**: `usecase/plan/PlanIngestionOrchestrator.java`

#### 2. OutboxRelayOrchestrator (Outbox 中继编排器)

**职责**: 轮询 Outbox 表,可靠地将领域事件发布到 MQ。

**中继流程**:
1. **Phase 1**: 构建中继计划(查询待发布消息)
2. **Phase 2**: 获取租约(避免并发冲突)
3. **Phase 3**: 发布消息到 MQ
4. **Phase 4**: 标记消息为已发布
5. **Phase 5**: 记录中继日志

**关键特性**:
- **租约机制**: 避免多实例并发发布同一消息
- **批量发布**: 一次处理多个消息(可配置批大小)
- **错误分类**: 区分可重试和不可重试错误
- **幂等性**: MQ 消费者需要实现幂等性逻辑

**关键方法**:
```java
@Transactional
public RelayReport relay(OutboxRelayCommand command) {
    // 1. 构建中继计划
    RelayPlan plan = relayPlanBuilder.build(command.getBatchSize());

    // 2. 获取租约
    relayLeaseCoordinator.acquireLease(plan);

    // 3. 发布消息
    relayPublishCoordinator.publish(plan);

    // 4. 记录日志
    relayLogCoordinator.recordSuccess(plan);

    return RelayReport.success(plan.getMessageCount());
}
```

**文件**: `usecase/relay/OutboxRelayOrchestrator.java`

#### 3. TaskExecutionUseCase (任务执行用例)

**职责**: 管理任务执行的完整生命周期,包括准备、执行、完成。

**执行流程**:
1. **Prepare**: 加载执行上下文(编译表达式)、获取租约、开始心跳
2. **Execute**: 批次规划 → 批次执行 → 进度跟踪 → 游标推进
3. **Complete**: 标记任务完成、释放租约、发布完成事件

**关键子用例**:
- `PrepareTaskExecutionUseCase`: 准备任务执行
- `ExecuteTaskBatchesUseCase`: 执行任务批次
- `CompleteTaskExecutionUseCase`: 完成任务执行

**文件**: `usecase/execution/TaskExecutionUseCaseImpl.java`

### 关键协调器

#### 1. PlanPersistenceCoordinator (持久化协调器)

**职责**: 协调 Plan/Slice/Task 的持久化,确保顺序和一致性。

**持久化顺序**:
1. 持久化 Plan(获取 planId)
2. 持久化 Slice(绑定 planId,获取 sliceId)
3. 持久化 Task(绑定 planId + sliceId)

**文件**: `usecase/plan/PlanPersistenceCoordinator.java`

#### 2. PlanIdempotencyCoordinator (幂等性协调器)

**职责**: 检查 Plan 是否已存在(通过 planKey),处理幂等性冲突。

**处理策略**:
- 如果 planKey 已存在且 Plan 状态为 READY/COMPLETED → 直接返回现有 Plan
- 如果 planKey 已存在且 Plan 状态为 FAILED → 重试失败的 Task
- 如果 planKey 不存在 → 继续创建新 Plan

**文件**: `usecase/plan/PlanIdempotencyCoordinator.java`

#### 3. ExecutionContextLoader (执行上下文加载器)

**职责**: 加载任务执行所需的完整上下文,包括编译表达式。

**加载步骤**:
1. 加载 Task → Slice → Plan(3 层快照)
2. 解析配置快照
3. 调用 ExpressionCompilerPort 编译表达式
4. 构建 ExecutionContext(包含 compiledQuery, compiledParams)

**关键代码**:
```java
public ExecutionContext loadContext(Long taskId, Long runId) {
    // 1. 加载快照
    TaskAggregate task = taskRepository.findById(taskId).orElseThrow();
    PlanSliceAggregate slice = sliceRepository.findById(task.getSliceId()).orElseThrow();
    PlanAggregate plan = planRepository.findById(task.getPlanId()).orElseThrow();

    // 2. 编译表达式
    ExprCompilationResult compilationResult = expressionCompilerPort.compile(
        new ExprCompilationRequest(plan.getExprProtoSnapshotJson(), plan.getProvenanceConfigSnapshotJson())
    );

    // 3. 构建上下文
    return new ExecutionContext(
        taskId, runId,
        task.getProvenanceCode(),
        task.getOperationCode(),
        configSnapshot,
        plan.getExprHash(),
        compilationResult.query(),       // compiledQuery
        compilationResult.params(),      // compiledParams (provider-named)
        compilationResult.normalizedExpression(),
        slice.getWindowSpec()
    );
}
```

**文件**: `usecase/execution/session/ExecutionContextLoaderImpl.java`

### 切片规划器

#### SlicePlanner (切片规划器接口)

**职责**: 将大的 Plan 窗口分解为可管理的切片。

**实现策略**:
- `TimeSlicePlanner`: 按时间范围切片(如每天一个切片)
- `DateSlicePlanner`: 按日期切片(如每月一个切片)
- `SingleSlicePlanner`: 不切片(整个 Plan 作为一个切片)

**文件**: `usecase/plan/slicer/SlicePlanner.java`

### 事件处理器

#### TaskCompletedEventHandler (任务完成事件处理器)

**职责**: 处理任务完成事件,更新切片和计划状态。

**处理逻辑**:
1. 接收 TaskCompletedEvent
2. 查询同一切片下的所有任务状态
3. 如果所有任务完成 → 更新切片状态为 COMPLETED
4. 查询同一计划下的所有切片状态
5. 如果所有切片完成 → 更新计划状态为 COMPLETED

**文件**: `eventhandler/TaskCompletedEventHandler.java`

---

## 依赖关系

### 上游依赖
- `patra-ingest-domain`: 领域模型和端口接口
- `patra-ingest-api`: API 契约
- `patra-common-core`: 通用工具
- `patra-expr-kernel`: 表达式编译内核
- `patra-spring-boot-starter-expr`: 表达式编译 Starter
- `patra-spring-boot-starter-provenance`: Provenance Starter

### 下游消费者
- `patra-ingest-adapter`: 适配器层(定时任务、MQ 监听器)

**依赖方向**: App → Domain (符合六边形架构)

---

## 设计模式

### 1. 编排器模式 (Orchestrator Pattern)

编排器协调多个领域对象和服务完成业务流程:
- `PlanIngestionOrchestrator`: 协调 Plan 摄入流程
- `OutboxRelayOrchestrator`: 协调 Outbox 中继流程

### 2. 命令模式 (Command Pattern)

使用命令对象封装用例输入:
- `PlanIngestionCommand`: Plan 摄入命令
- `OutboxRelayCommand`: Outbox 中继命令

### 3. 策略模式 (Strategy Pattern)

使用策略接口支持多种实现:
- `SlicePlanner`: 切片策略(TIME/DATE/SINGLE)
- `BatchPlanner`: 批次规划策略(PubMed/EPMC/Crossref)

### 4. 模板方法模式 (Template Method Pattern)

`AbstractOutboxPublisher` 定义 Outbox 发布的通用流程,子类实现具体细节:
```java
public abstract class AbstractOutboxPublisher {
    public OutboxPublishResult publish(OutboxPublishContext context) {
        // 1. 准备负载
        OutboxPayload payload = preparePayload(context);

        // 2. 发布消息(抽象方法,由子类实现)
        publishMessage(payload);

        // 3. 记录指标
        recordMetrics(context);
    }

    protected abstract void publishMessage(OutboxPayload payload);
}
```

### 5. 事件驱动模式 (Event-Driven Pattern)

通过领域事件驱动跨聚合的最终一致性:
- `TaskCompletedEvent` → `TaskCompletedEventHandler` → 更新 Slice/Plan 状态

---

## 关键流程

### Plan 摄入流程

```
1. PlanScheduler(adapter) 触发定时任务
   ↓
2. PlanIngestionOrchestrator.ingestPlan(command)
   ↓
3. PatraRegistryPort 加载配置快照
   ↓
4. CursorRepository 查询水位线
   ↓
5. PlanningWindowResolver 解析窗口
   ↓
6. PlannerValidator 预验证
   ↓
7. PlanAssembler 装配 Plan/Slice/Task
   ↓
8. PlanIdempotencyCoordinator 检查幂等性
   ↓
9. PlanPersistenceCoordinator 持久化(事务内)
   ↓
10. TaskOutboxPublisher 发布到 Outbox
   ↓
11. OutboxRelayOrchestrator 轮询并发布到 MQ
```

### Task 执行流程

```
1. IngestStreamConsumers(adapter) 消费任务消息
   ↓
2. TaskExecutionUseCase.executeTask(taskId)
   ↓
3. PrepareTaskExecutionUseCase 准备执行
   - ExecutionContextLoader 加载上下文(编译表达式)
   - LeaseManagementService 获取租约
   - HeartbeatRenewalService 开始心跳
   ↓
4. ExecuteTaskBatchesUseCase 执行批次
   - BatchPlanner 规划批次
   - GenericBatchExecutor 执行批次
   - CursorAdvancer 推进游标
   ↓
5. LiteraturePublisherOrchestrator 发布文献数据
   ↓
6. CompleteTaskExecutionUseCase 完成任务
   - 标记任务状态
   - 释放租约
   - 发布 TaskCompletedEvent
```

---

## 配置说明

### Outbox 中继配置

```yaml
patra:
  ingest:
    outbox:
      relay:
        batch-size: 100              # 每次中继的消息数
        lease-duration: 5m           # 租约时长
        polling-interval: 10s        # 轮询间隔
        retry-max-attempts: 3        # 最大重试次数
        retry-backoff: 2s            # 重试退避时间
```

### Outbox 发布器配置

```yaml
patra:
  ingest:
    outbox:
      publisher:
        enabled: true
        batch-size: 50               # 批量发布大小
        timeout: 5s                  # 发布超时
```

---

## 使用示例

### 示例 1: 触发 Plan 摄入

```java
@Service
@RequiredArgsConstructor
public class PlanSchedulerService {

    private final PlanIngestionUseCase planIngestionUseCase;

    public void triggerPlanIngestion() {
        PlanIngestionCommand command = PlanIngestionCommand.builder()
            .provenanceCode("pubmed")
            .operationCode(OperationCode.HARVEST)
            .triggerType(TriggerType.SCHEDULED)
            .windowFrom(Instant.parse("2025-01-01T00:00:00Z"))
            .windowTo(Instant.parse("2025-01-10T00:00:00Z"))
            .sliceStrategyCode("TIME")
            .build();

        PlanIngestionResult result = planIngestionUseCase.ingestPlan(command);

        log.info("Plan ingestion completed: planId={}, taskCount={}",
            result.getPlanId(), result.getTaskCount());
    }
}
```

### 示例 2: 消费任务消息并执行

```java
@Component
@RequiredArgsConstructor
public class TaskMessageConsumer {

    private final TaskExecutionUseCase taskExecutionUseCase;

    @StreamListener(IngestPublishingChannels.TASK_READY)
    public void handleTaskReadyMessage(TaskReadyMessage message) {
        Long taskId = message.getTaskId();

        try {
            taskExecutionUseCase.executeTask(taskId);
            log.info("Task execution completed: taskId={}", taskId);
        } catch (Exception ex) {
            log.error("Task execution failed: taskId={}", taskId, ex);
        }
    }
}
```

### 示例 3: 触发 Outbox 中继

```java
@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    private final OutboxRelayUseCase outboxRelayUseCase;

    @Scheduled(fixedDelay = 10000)  // 每 10 秒执行一次
    public void relayOutboxMessages() {
        OutboxRelayCommand command = OutboxRelayCommand.builder()
            .batchSize(100)
            .build();

        RelayReport report = outboxRelayUseCase.relay(command);

        log.info("Outbox relay completed: published={}, failed={}",
            report.getPublishedCount(), report.getFailedCount());
    }
}
```

---

## 技术栈

- **Java**: 25
- **Spring Boot**: 3.5.7
- **Spring TX**: 事务管理
- **Spring AOP**: 面向切面编程
- **patra-expr-kernel**: 表达式编译内核
- **Micrometer**: 指标收集

---

**最后更新**: 2025-01-16
**Maven 坐标**: `com.papertrace:patra-ingest-app:0.1.0-SNAPSHOT`

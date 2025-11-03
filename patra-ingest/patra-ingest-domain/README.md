# patra-ingest-domain — 摄入领域模型

## 概述

`patra-ingest-domain` 是 **patra-ingest 服务的领域核心层**,包含纯 Java 业务逻辑,无任何框架依赖。遵循**六边形架构(Hexagonal Architecture)**和**领域驱动设计(DDD)**原则,封装数据摄入编排的核心业务规则。

本模块在六边形架构中位于**最内层**,定义了:
- **聚合根(Aggregates)**: Plan、Task、PlanSlice、ScheduleInstance
- **值对象(Value Objects)**: WindowSpec、CursorWatermark、ExecutionContext 等
- **领域事件(Domain Events)**: TaskQueuedEvent、TaskCompletedEvent 等
- **仓储端口(Repository Ports)**: 供外层实现的接口契约
- **外部服务端口(Service Ports)**: PatraRegistryPort、ExpressionCompilerPort 等

**架构约束**: 通过 `maven-enforcer-plugin` 强制执行领域层纯净性,禁止依赖 Spring、MyBatis、JPA 等框架。

---

## 核心职责

- **业务规则封装**: 定义 Plan 状态机、Task 生命周期、窗口解析逻辑
- **聚合根管理**: 维护聚合根的一致性边界和不变量
- **领域事件发布**: 定义业务事件以驱动跨聚合的最终一致性
- **端口定义**: 为基础设施层提供需要实现的接口契约

---

## 模块结构

```
patra-ingest-domain/
└─ src/main/java/.../domain/
   ├─ model/                          # 领域模型
   │  ├─ aggregate/                   # 聚合根
   │  │  ├─ PlanAggregate.java            # 计划聚合根(状态机 + 快照)
   │  │  ├─ TaskAggregate.java            # 任务聚合根(租约 + 执行时间线)
   │  │  ├─ PlanSliceAggregate.java       # 切片聚合根(中间分组)
   │  │  └─ ScheduleInstanceAggregate.java # 调度实例(幂等性跟踪)
   │  ├─ entity/                      # 领域实体
   │  │  ├─ OutboxMessage.java            # Outbox 消息实体
   │  │  ├─ Cursor.java                   # 游标实体
   │  │  ├─ CursorEvent.java              # 游标事件
   │  │  ├─ TaskRun.java                  # 任务执行记录
   │  │  └─ TaskRunBatch.java             # 批次执行记录
   │  ├─ vo/                          # 值对象
   │  │  ├─ plan/                         # 计划相关值对象
   │  │  │  ├─ WindowSpec.java                # 窗口规范(Sealed Interface)
   │  │  │  ├─ PlannerWindow.java             # 规划窗口
   │  │  │  ├─ PlanTriggerNorm.java           # 触发器归一化数据
   │  │  │  └─ TaskSchedulerContext.java      # 任务调度上下文
   │  │  ├─ cursor/                       # 游标相关值对象
   │  │  │  ├─ CursorWatermark.java           # 水位线
   │  │  │  ├─ CursorValue.java               # 游标值
   │  │  │  └─ CursorLineage.java             # 游标血缘
   │  │  ├─ execution/                    # 执行相关值对象
   │  │  │  ├─ ExecutionContext.java          # 执行上下文(含编译后表达式)
   │  │  │  ├─ ExecutionTimeline.java         # 执行时间线
   │  │  │  ├─ TaskParams.java                # 任务参数
   │  │  │  └─ RunContext.java                # 运行上下文
   │  │  ├─ batch/                        # 批次相关值对象
   │  │  │  ├─ Batch.java                     # 批次定义
   │  │  │  ├─ BatchPlan.java                 # 批次计划
   │  │  │  └─ BatchResult.java               # 批次结果
   │  │  ├─ shared/                       # 共享值对象
   │  │  │  ├─ IdempotentKey.java             # 幂等键
   │  │  │  ├─ LeaseInfo.java                 # 租约信息
   │  │  │  ├─ NamespaceKey.java              # 命名空间键
   │  │  │  └─ SliceSpec.java                 # 切片规范
   │  │  ├─ expression/                   # 表达式相关值对象
   │  │  │  ├─ ExprCompilationRequest.java    # 编译请求
   │  │  │  └─ ExprCompilationResult.java     # 编译结果
   │  │  ├─ relay/                        # 中继相关值对象
   │  │  │  └─ RelayPlan.java                 # 中继计划
   │  │  └─ storage/                      # 存储相关值对象
   │  │     ├─ StorageUploadRequest.java      # 上传请求
   │  │     └─ StorageUploadResult.java       # 上传结果
   │  ├─ enums/                       # 领域枚举
   │  │  ├─ PlanStatus.java               # 计划状态(DRAFT/SLICING/READY/COMPLETED)
   │  │  ├─ TaskStatus.java               # 任务状态(QUEUED/RUNNING/SUCCEEDED/FAILED)
   │  │  ├─ SliceStatus.java              # 切片状态
   │  │  ├─ OperationCode.java            # 操作代码(HARVEST/UPDATE/COMPENSATION)
   │  │  ├─ CursorType.java               # 游标类型
   │  │  └─ OutboxStatus.java             # Outbox 状态
   │  └─ snapshot/                    # 配置快照
   │     └─ ProvenanceConfigSnapshot.java # 来源配置快照(不可变)
   ├─ event/                          # 领域事件
   │  ├─ TaskQueuedEvent.java             # 任务入队事件
   │  ├─ TaskCompletedEvent.java          # 任务完成事件
   │  ├─ SliceStatusChangedEvent.java     # 切片状态变更事件
   │  └─ OutboxMessage*Event.java         # Outbox 相关事件
   ├─ port/                           # 仓储端口(由 infra 实现)
   │  ├─ PlanRepository.java              # 计划仓储
   │  ├─ TaskRepository.java              # 任务仓储
   │  ├─ PlanSliceRepository.java         # 切片仓储
   │  ├─ CursorRepository.java            # 游标仓储
   │  ├─ OutboxMessageRepository.java     # Outbox 仓储
   │  ├─ PatraRegistryPort.java           # Registry 外部端口
   │  ├─ ExpressionCompilerPort.java      # 表达式编译器端口
   │  └─ PubmedSearchPort.java            # PubMed 搜索端口
   ├─ service/                        # 领域服务
   │  ├─ PlanStatusCalculator.java        # 计划状态计算器
   │  └─ SliceStatusCalculator.java       # 切片状态计算器
   ├─ policy/                         # 领域策略
   │  ├─ RelayRetryPolicy.java            # 中继重试策略
   │  └─ RelayErrorClassifier.java        # 中继错误分类器
   ├─ exception/                      # 领域异常
   │  ├─ PlanAssemblyException.java       # 计划装配异常
   │  ├─ PlanValidationException.java     # 计划验证异常
   │  └─ OutboxPublishException.java      # Outbox 发布异常
   ├─ outbox/                         # Outbox 模式
   │  ├─ OutboxPayload.java               # Outbox 负载
   │  └─ OutboxHeaders.java               # Outbox 头部
   ├─ factory/                        # 领域工厂
   │  └─ OutboxRelayLogFactory.java       # Outbox 中继日志工厂
   └─ messaging/                      # 消息契约
      ├─ IngestPublishingChannels.java    # 发布通道常量
      └─ ConsumerGroups.java              # 消费者组常量
```

---

## 主要组件

### 聚合根

#### 1. PlanAggregate (计划聚合根)

**职责**: 数据采集任务的蓝图,包含窗口规范、表达式快照和配置快照。

**状态机**:
```
DRAFT → SLICING → READY → COMPLETED
                     ↓
                 PARTIAL → FAILED
```

**核心属性**:
- `planKey`: 幂等键 = hash(provenance + operation + window + strategy)
- `provenanceCode`: 来源代码(如 "pubmed")
- `operationCode`: 操作类型(HARVEST/UPDATE/COMPENSATION)
- `windowSpec`: 窗口边界(TIME/DATE/CURSOR/VOLUME/SINGLE)
- `sliceStrategyCode`: 切片策略(TIME/DATE/SINGLE)
- `exprProtoSnapshotJson`: 表达式原型快照(JSON)
- `provenanceConfigSnapshotJson`: 配置快照(JSON)

**关键方法**:
- `markAsSlicing()`: 转为切片中状态
- `markAsReady()`: 转为就绪状态
- `markAsCompleted()`: 标记为已完成
- `markAsFailed(String reason)`: 标记为失败

**文件**: `model/aggregate/PlanAggregate.java`

#### 2. TaskAggregate (任务聚合根)

**职责**: 数据采集的原子工作单元,包含租约管理和执行时间线。

**状态机**:
```
QUEUED → RUNNING → SUCCEEDED
           ↓
        FAILED → (重试) → QUEUED
           ↓
       PARTIAL(部分成功)
```

**核心属性**:
- `idempotentKey`: 业务幂等键
- `provenanceCode`: 来源代码
- `operationCode`: 操作类型
- `paramsJson`: 任务参数(JSON)
- `priority`: 执行优先级
- `scheduledAt`: 计划执行时间
- `leaseInfo`: 租约信息(owner, leasedUntil)
- `executionTimeline`: 执行时间线(startedAt, completedAt)
- `retryCount`: 已尝试的重试次数

**关键方法**:
- `markAsRunning()`: 标记为运行中
- `markAsSucceeded(String resultJson)`: 标记为成功
- `markAsFailed(String errorMsg)`: 标记为失败
- `acquireLease(String owner, Duration duration)`: 获取租约
- `renewLease(Duration extension)`: 续约
- `shouldRetry()`: 判断是否应重试

**文件**: `model/aggregate/TaskAggregate.java`

#### 3. PlanSliceAggregate (切片聚合根)

**职责**: 计划的中间分组单元,用于将大计划分解为可管理的切片。

**核心属性**:
- `planId`: 所属计划 ID
- `sliceIndex`: 切片索引
- `windowSpec`: 切片窗口
- `status`: 切片状态(PENDING/IN_PROGRESS/COMPLETED/FAILED)

**文件**: `model/aggregate/PlanSliceAggregate.java`

#### 4. ScheduleInstanceAggregate (调度实例聚合根)

**职责**: 跟踪调度器触发的执行实例,用于幂等性保证。

**核心属性**:
- `scheduleKey`: 调度幂等键
- `provenanceCode`: 来源代码
- `operationCode`: 操作代码
- `triggerAt`: 触发时间
- `status`: 执行状态

**文件**: `model/aggregate/ScheduleInstanceAggregate.java`

### 值对象

#### WindowSpec (窗口规范 - Sealed Interface)

**职责**: 定义数据采集的窗口边界,支持多种窗口类型。

**实现类型**:
```java
public sealed interface WindowSpec {
    record Time(Instant from, Instant to) implements WindowSpec {}      // 时间窗口
    record IdRange(Long minId, Long maxId) implements WindowSpec {}     // ID 范围窗口
    record CursorLandmark(String cursorValue) implements WindowSpec {}  // 游标窗口
    record VolumeBudget(Integer maxRecords) implements WindowSpec {}    // 容量窗口
    record Single() implements WindowSpec {}                             // 无窗口
}
```

**文件**: `model/vo/plan/WindowSpec.java`

#### ExecutionContext (执行上下文)

**职责**: 封装任务执行所需的完整上下文,包含编译后的表达式。

**核心属性**:
- `taskId`: 任务 ID
- `runId`: 运行 ID
- `provenanceCode`: 来源代码
- `operationCode`: 操作代码
- `configSnapshot`: 配置快照
- `exprHash`: 表达式哈希
- `compiledQuery`: 编译后的查询字符串
- `compiledParams`: 编译后的参数映射(provider-named)
- `normalizedExpression`: 归一化表达式
- `windowSpec`: 窗口规范

**文件**: `model/vo/execution/ExecutionContext.java`

#### CursorWatermark (游标水位线)

**职责**: 跟踪增量采集的进度(高水位标记)。

**类型**:
- **Global Time Watermark**: 全局时间水位线
- **Cursor Value Watermark**: 游标值水位线(用于分页)

**文件**: `model/vo/cursor/CursorWatermark.java`

### 领域事件

#### TaskQueuedEvent (任务入队事件)

**触发时机**: 任务创建并持久化后发布。

**用途**: 通过 Outbox 模式发布到 MQ,通知任务工作者执行。

**文件**: `event/TaskQueuedEvent.java`

#### TaskCompletedEvent (任务完成事件)

**触发时机**: 任务执行完成(成功或失败)后发布。

**用途**: 触发切片状态更新、计划状态计算等下游逻辑。

**文件**: `event/TaskCompletedEvent.java`

### 仓储端口

#### PlanRepository (计划仓储接口)

**职责**: 定义计划聚合根的持久化契约。

**核心方法**:
```java
public interface PlanRepository {
    PlanAggregate save(PlanAggregate plan);
    Optional<PlanAggregate> findById(Long planId);
    Optional<PlanAggregate> findByPlanKey(String planKey);
    List<PlanAggregate> findByStatus(PlanStatus status);
    void update(PlanAggregate plan);
}
```

**文件**: `port/PlanRepository.java`

#### TaskRepository (任务仓储接口)

**职责**: 定义任务聚合根的持久化契约。

**核心方法**:
```java
public interface TaskRepository {
    TaskAggregate save(TaskAggregate task);
    List<TaskAggregate> saveAll(List<TaskAggregate> tasks);
    Optional<TaskAggregate> findById(Long taskId);
    List<TaskAggregate> findQueuedTasks(String provenanceCode, int limit);
    long countQueuedTasks(String provenanceCode, OperationCode operationCode);
    void update(TaskAggregate task);
}
```

**文件**: `port/TaskRepository.java`

### 外部服务端口

#### PatraRegistryPort (Registry 外部端口)

**职责**: 从 patra-registry 获取 Provenance 配置快照。

**核心方法**:
```java
public interface PatraRegistryPort {
    ProvenanceConfigSnapshot fetchConfig(
        ProvenanceCode code,
        OperationCode operation
    );
}
```

**文件**: `port/PatraRegistryPort.java`

#### ExpressionCompilerPort (表达式编译器端口)

**职责**: 编译 Plan 表达式为可执行的查询字符串和参数映射。

**核心方法**:
```java
public interface ExpressionCompilerPort {
    ExprCompilationResult compile(ExprCompilationRequest request);
}
```

**文件**: `port/ExpressionCompilerPort.java`

---

## 依赖关系

### 上游依赖
- `patra-common-core`: 通用工具类和枚举(ProvenanceCode, OperationCode)
- `patra-common-model`: 通用领域模型基类

### 下游消费者
- `patra-ingest-app`: 应用层(用例编排器)
- `patra-ingest-infra`: 基础设施层(仓储实现)

**依赖方向**: Domain ← App ← Infra/Adapter (符合六边形架构)

---

## 设计模式

### 1. 聚合根模式 (Aggregate Pattern)

每个聚合根负责维护自己的一致性边界:
- Plan 聚合根管理计划状态机
- Task 聚合根管理任务生命周期和租约

### 2. 值对象模式 (Value Object Pattern)

不可变值对象封装业务概念:
- WindowSpec 封装窗口规范
- ExecutionContext 封装执行上下文

### 3. 仓储模式 (Repository Pattern)

通过接口定义持久化契约,由基础设施层实现:
- PlanRepository, TaskRepository 等

### 4. 领域事件模式 (Domain Event Pattern)

通过事件驱动跨聚合的最终一致性:
- TaskQueuedEvent 驱动任务执行
- TaskCompletedEvent 驱动状态更新

### 5. Outbox 模式 (Transactional Outbox Pattern)

通过 OutboxMessage 实体保证事件发布的可靠性:
- 事件与业务数据在同一事务中持久化
- 通过轮询发布到 MQ

---

## 架构约束

### Maven Enforcer 规则

通过 `maven-enforcer-plugin` 强制执行领域层纯净性:

```xml
<bannedDependencies>
    <excludes>
        <exclude>org.springframework:*</exclude>
        <exclude>org.springframework.boot:*</exclude>
        <exclude>jakarta.persistence:*</exclude>
        <exclude>com.baomidou:*</exclude>
        <exclude>org.mybatis:*</exclude>
    </excludes>
    <message>
❌ Domain layer MUST be framework-free (Hexagonal Architecture)!
   Only allowed: patra-common, Lombok, Hutool, Jackson
    </message>
</bannedDependencies>
```

**允许的依赖**:
- ✅ patra-common (通用工具)
- ✅ Lombok (编译时注解)
- ✅ Hutool (工具类)
- ✅ Jackson (JSON 序列化)

**禁止的依赖**:
- ❌ Spring Framework
- ❌ MyBatis / JPA
- ❌ Servlet API
- ❌ 任何基础设施框架

---

## 使用示例

### 示例 1: 创建计划聚合根

```java
// 构建计划聚合根
PlanAggregate plan = PlanAggregate.builder()
    .planKey("plan_pubmed_harvest_20250101_20250110")
    .provenanceCode("pubmed")
    .operationCode(OperationCode.HARVEST)
    .windowSpec(new WindowSpec.Time(
        Instant.parse("2025-01-01T00:00:00Z"),
        Instant.parse("2025-01-10T00:00:00Z")
    ))
    .sliceStrategyCode("TIME")
    .exprProtoSnapshotJson("{...}")
    .provenanceConfigSnapshotJson("{...}")
    .status(PlanStatus.DRAFT)
    .build();

// 状态转换
plan.markAsSlicing();  // DRAFT → SLICING
plan.markAsReady();    // SLICING → READY
```

### 示例 2: 任务租约管理

```java
// 获取任务
TaskAggregate task = taskRepository.findById(taskId).orElseThrow();

// 获取租约
task.acquireLease("worker-01", Duration.ofMinutes(5));

// 标记为运行中
task.markAsRunning();

// 执行任务...

// 标记为成功
task.markAsSucceeded("{\"recordCount\": 1000}");

// 持久化
taskRepository.update(task);
```

### 示例 3: 发布领域事件

```java
// 任务创建后发布事件
TaskAggregate task = TaskAggregate.builder()
    .idempotentKey("task_123")
    .status(TaskStatus.QUEUED)
    .build();

// 持久化任务
taskRepository.save(task);

// 收集领域事件
TaskQueuedEvent event = new TaskQueuedEvent(task.getId(), task.getIdempotentKey());

// 发布到 Outbox(由 app 层处理)
```

---

## 技术栈

- **Java**: 25 (利用 Record、Sealed Interface、Pattern Matching)
- **Lombok**: 编译时注解(@Data, @Builder, @RequiredArgsConstructor)
- **Hutool**: 工具类(StrUtil, CollUtil, DateUtil)
- **Jackson**: JSON 序列化/反序列化

---

**最后更新**: 2025-01-16
**Maven 坐标**: `com.papertrace:patra-ingest-domain:0.1.0-SNAPSHOT`

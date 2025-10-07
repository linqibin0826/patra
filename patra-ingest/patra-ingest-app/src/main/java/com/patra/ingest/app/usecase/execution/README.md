# 任务执行引擎（Task Execution Engine）

## 概述

任务执行引擎是 Papertrace 数据摄取系统的核心组件，负责可靠、幂等、可观测地执行数据采集任务。

### 核心特性

- **三阶段编排**：Prepare → Execute → Complete（遵循 ADR-001）
- **分布式租约管理**：基于 CAS 的乐观锁租约机制，支持心跳续约
- **幂等保障**：基于 idempotentKey 的去重机制
- **批次执行**：支持可配置的批次规划与执行策略
- **游标推进**：带乐观锁的游标管理，保证增量采集的一致性
- **优雅降级**：租约撤销自动检测、心跳失败阈值控制

---

## 架构设计

### 整体流程

```
TaskReadyCommand (MQ/Scheduler)
    ↓
TaskExecutionUseCase (顶层编排器)
    ↓
┌─────────────────────────────────────────────────────────┐
│  Prepare Phase (PrepareTaskExecutionUseCase)            │
│  - 幂等检查 (IdempotencyChecker)                         │
│  - 租约抢占 (LeaseManagementService)                     │
│  - 会话初始化 (ExecutionSessionManager → TaskRun + 心跳) │
│  - 上下文加载 (ExecutionContextLoader → Config + Expr)   │
└─────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────┐
│  Execute Phase (ExecuteTaskBatchesUseCase)              │
│  - 批次规划 (BatchPlanner)                               │
│  - 批次执行 (BatchExecutor)                              │
│  - 结果持久化 (TaskRunBatch)                             │
│  - 租约验证 (HeartbeatHandle.isLeaseRevoked)            │
└─────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────┐
│  Complete Phase (CompleteTaskExecutionUseCase)          │
│  - 游标推进 (CursorAdvancer)                             │
│  - 状态判断 (SUCCEEDED/FAILED/PARTIAL/CURSOR_PENDING)   │
│  - 资源清理 (停止心跳 + 释放租约)                        │
└─────────────────────────────────────────────────────────┘
```

### 核心组件

#### 1. 顶层编排器

- **TaskExecutionUseCaseImpl**: 统一入口，编排三个子用例，处理顶层异常与资源清理

#### 2. Prepare 阶段

- **IdempotencyChecker**: 检查任务是否已成功执行（幂等保障）
- **LeaseManagementService**: 租约抢占与释放（基于 DB 乐观锁）
- **ExecutionSessionManager**: 创建 TaskRun、启动心跳、返回 ExecutionSession
- **ExecutionContextLoader**: 加载任务配置、编译表达式、构建执行上下文

#### 3. Execute 阶段

- **BatchPlannerRegistry**: 根据 provenanceCode 选择批次规划器
- **BatchPlanner**: 生成批次计划（支持分页/token cursor）
- **BatchExecutorRegistry**: 根据 provenanceCode 选择批次执行器
- **BatchExecutor**: 执行单个批次（调用外部 API/SDK）
- **HeartbeatRenewalService**: 定期续约心跳，检测租约撤销

#### 4. Complete 阶段

- **CursorAdvancer**: 推进游标水位（带乐观锁，失败返回 false）
- **CompleteTaskExecutionUseCase**: 根据批次执行结果判断最终状态，清理资源

---

## 状态流转

### Task 状态

```
QUEUED (初始) 
  ↓ 
RUNNING (执行中)
  ↓
┌─────────────┬──────────────┬───────────────┬─────────────────┐
│  SUCCEEDED  │   PARTIAL    │    FAILED     │ CURSOR_PENDING  │
│ (全部成功)   │ (部分成功)    │ (全部失败)     │ (游标待重试)     │
└─────────────┴──────────────┴───────────────┴─────────────────┘
```

- **SUCCEEDED**: 全部批次成功 + 游标推进成功
- **CURSOR_PENDING**: 全部批次成功 + 游标推进失败（乐观锁冲突）
- **PARTIAL**: 部分批次成功、部分失败
- **FAILED**: 全部批次失败或无批次执行

### TaskRun 状态

与 Task 状态一致，但针对单次运行记录。

---

## 配置说明

### application.yaml

```yaml
task:
  execution:
    lease:
      duration: 60                 # 租约持续时间（秒）
      renewal-interval: 20         # 租约续约间隔（秒，建议为 duration 的 1/3）
    heartbeat:
      failure-threshold: 3         # 心跳连续失败阈值（次）
    max-batches: 1000              # 最大批次数限制
    fail-fast: false               # 批次失败是否立即中断（true=中断，false=继续）
```

### 配置说明

- **lease.duration**: 租约有效期，超期后其他节点可抢占
- **lease.renewal-interval**: 心跳续约间隔，过短增加 DB 压力，过长可能导致租约过期
- **heartbeat.failure-threshold**: 连续失败 N 次后触发租约验证，防止僵尸任务
- **max-batches**: 防止批次数过多导致执行时间过长
- **fail-fast**: 决定批次执行策略（快速失败 vs 尽力而为）

---

## 使用示例

### 1. 发送任务就绪命令（通过 MQ）

```java
TaskReadyCommand command = new TaskReadyCommand(
    taskId,
    idempotentKey,
    provenance,
    operation,
    schedulerRunId,
    correlationId,
    headers
);

// 通过 MQ 发送或直接调用
taskExecutionUseCase.execute(command);
```

### 2. 实现自定义 BatchPlanner

```java
@Component
public class PubMedBatchPlanner implements BatchPlanner {
    
    @Override
    public String getProvenanceCode() {
        return "PUBMED";
    }
    
    @Override
    public BatchPlan plan(ExecutionContext context, int maxBatches) {
        // 根据游标和执行窗口生成批次计划
        // 返回 BatchPlan(batches, totalBatches, exceedsLimit)
    }
}
```

### 3. 实现自定义 BatchExecutor

```java
@Component
public class PubMedBatchExecutor implements BatchExecutor {
    
    @Override
    public String getProvenanceCode() {
        return "PUBMED";
    }
    
    @Override
    public BatchResult execute(ExecutionContext context, Batch batch) {
        // 调用 PubMed API
        // 返回 BatchResult.success(batchNo, fetchedCount, nextCursor, storageKey)
        // 或 BatchResult.failure(batchNo, errorMessage)
    }
}
```

### 4. 处理 CURSOR_PENDING 状态

游标推进失败（乐观锁冲突）时，任务状态标记为 `CURSOR_PENDING`。建议：

- 由后台定时任务扫描 `CURSOR_PENDING` 状态的任务
- 重新尝试推进游标（调用 `CursorAdvancer.advance()`）
- 成功后更新任务状态为 `SUCCEEDED`

---

## 关键设计决策

### 1. 为什么使用三阶段编排？

- **关注点分离**: Prepare/Execute/Complete 各司其职，降低单个用例复杂度
- **可测试性**: 每个阶段可独立测试
- **可扩展性**: 新增数据源只需实现 BatchPlanner 和 BatchExecutor

### 2. 为什么引入 CURSOR_PENDING 状态？

- **乐观锁冲突**: 多节点同时推进同一游标时，只有一个成功
- **解耦重试**: 批次执行成功但游标失败时，不应重新执行批次
- **可观测性**: 明确标识需要异步重试的任务

### 3. 为什么使用分布式租约而非分布式锁？

- **非阻塞**: 租约抢占失败时直接跳过，不阻塞其他任务
- **续约机制**: 通过心跳续约，防止任务执行时间过长导致租约过期
- **自动撤销**: 节点宕机后租约自动过期，其他节点可接管

### 4. 为什么批次执行支持 fail-fast 和 continue-on-error？

- **数据完整性优先**: fail-fast=true，任何批次失败立即中断
- **尽力而为**: fail-fast=false，尽可能多地采集数据，适合大规模采集场景

---

## 监控与可观测性

### 关键日志

- **[INGEST][APP] task execution start**: 任务执行开始
- **[INGEST][APP] lease acquired**: 租约抢占成功
- **[INGEST][APP] session created**: 会话创建成功
- **[INGEST][APP] batch plan created**: 批次规划完成
- **[INGEST][APP] batch succeeded/failed**: 批次执行结果
- **[INGEST][APP] task succeeded/marked PARTIAL/marked FAILED**: 任务最终状态

### 关键指标

- 任务执行耗时（P50/P95/P99）
- 批次执行成功率
- 租约抢占成功率
- 游标推进成功率
- 心跳续约失败次数

---

## 故障排查

### 问题 1: 任务长时间处于 RUNNING 状态

**可能原因**:
- 批次执行时间过长（超过 `lease.duration`）
- 心跳续约失败（达到 `failure-threshold`）

**排查步骤**:
1. 检查日志中的 `[INGEST][APP] lease revoked` 或 `[INGEST][APP] heartbeat renewal failed`
2. 检查 `Task.leased_until` 和 `Task.last_heartbeat_at` 时间戳
3. 检查网络/DB 连接是否稳定

**解决方案**:
- 增加 `lease.duration` 或减少 `max-batches`
- 优化批次执行性能
- 调整 `renewal-interval` 和 `failure-threshold`

---

### 问题 2: 游标未推进（CURSOR_PENDING）

**可能原因**:
- 多节点并发执行同一任务，游标乐观锁冲突
- 游标版本号不匹配

**排查步骤**:
1. 检查日志中的 `[INGEST][APP] cursor advance conflict`
2. 查询 `Cursor` 表，检查 `version` 字段

**解决方案**:
- 后台定时任务扫描 `CURSOR_PENDING` 状态并重试
- 减少并发执行同一任务的节点数

---

### 问题 3: 幂等检查失败（任务被跳过）

**可能原因**:
- 任务已成功执行（`TaskRun.status = SUCCEEDED`）
- `idempotentKey` 重复

**排查步骤**:
1. 检查日志中的 `[INGEST][APP] task already succeeded, skip execution`
2. 查询 `TaskRun` 表，检查是否存在 `status = SUCCEEDED` 的记录

**解决方案**:
- 确认任务确实需要重新执行（修改配置/数据后）
- 重新生成 `idempotentKey`（如基于配置快照的 SHA256 hash）

---

## 扩展点

### 1. 新增数据源

1. 实现 `BatchPlanner` 接口，返回 `provenanceCode`
2. 实现 `BatchExecutor` 接口，调用外部 API
3. 注册为 Spring Bean（自动注入到 Registry）

### 2. 自定义租约管理

实现 `LeaseManagementService` 接口，替换默认实现（如使用 Redis 分布式锁）。

### 3. 自定义游标推进策略

实现 `CursorAdvancer` 接口，支持不同的游标存储（如 Redis/ES）。

---

## 参考文档

- [ADR-001: 任务执行编排模式](../../../../../.kiro/specs/task-execution-engine/adr-001.md)
- [任务执行引擎规范](../../../../../.kiro/specs/task-execution-engine/spec.md)
- [实现流水线](../../../../../.kiro/specs/task-execution-engine/pipeline.md)

---

## 联系方式

如有疑问，请联系：
- 作者：linqibin
- 版本：0.1.0
- 最后更新：2025-01-XX

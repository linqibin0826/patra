# Task Execution Engine

## Overview

The Task Execution Engine is a core component of the Papertrace ingestion system. It executes ingestion tasks reliably, idempotently, and with observability.

### Key Features

- Three-phase orchestration: Prepare → Execute → Complete (follows ADR-001)
- Distributed lease management: CAS-based optimistic lease with heartbeat renewal
- Idempotency: deduplication based on idempotentKey
- Batch execution: configurable batch planning and execution strategies
- Cursor advancement: optimistic cursor management for consistent incremental ingestion
- Graceful degradation: automatic lease revocation detection and heartbeat failure thresholds

---

## Architecture

### End-to-End Flow

```
TaskReadyCommand (MQ/Scheduler)
    ↓
TaskExecutionUseCase (top-level orchestrator)
    ↓
┌─────────────────────────────────────────────────────────┐
│  Prepare Phase (PrepareTaskExecutionUseCase)            │
│  - Idempotency check (IdempotencyChecker)               │
│  - Lease acquisition (LeaseManagementService)           │
│  - Session init (ExecutionSessionManager → TaskRun + HB)│
│  - Context load (ExecutionContextLoader → Config + Expr)│
└─────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────┐
│  Execute Phase (ExecuteTaskBatchesUseCase)              │
│  - Batch planning (BatchPlanner)                        │
│  - Batch execution (BatchExecutor)                      │
│  - Persist results (TaskRunBatch)                       │
│  - Lease verification (HeartbeatHandle.isLeaseRevoked)  │
└─────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────┐
│  Complete Phase (CompleteTaskExecutionUseCase)          │
│  - Cursor advancement (CursorAdvancer)                  │
│  - Final status (SUCCEEDED/FAILED/PARTIAL/CURSOR_PENDING)│
│  - Cleanup (stop heartbeat + release lease)             │
└─────────────────────────────────────────────────────────┘
```

### Core Components

1) Top-level orchestrator
- TaskExecutionUseCaseImpl: single entrypoint; orchestrates three sub-use-cases; handles top-level exceptions and cleanup

2) Prepare phase
- IdempotencyChecker: determines if task already completed
- LeaseManagementService: acquire/release lease (DB optimistic locking)
- ExecutionSessionManager: create TaskRun, start heartbeat, return session
- ExecutionContextLoader: load task config, compile expressions, build execution context

3) Execute phase
- BatchPlannerRegistry: select planner by provenanceCode
- BatchPlanner: build batch plan (supports pagination/token cursors)
- BatchExecutorRegistry: select executor by provenanceCode
- BatchExecutor: execute a batch (calls external API/SDK)
- HeartbeatRenewalService: renew lease via heartbeat; detect lease revocation

4) Complete phase
- CursorAdvancer: advance cursor watermark (optimistic lock; returns false on conflict)
- CompleteTaskExecutionUseCase: determine final status based on batch results; cleanup resources

---

## States

### Task status

```
QUEUED (initial)
  ↓
RUNNING (in progress)
  ↓
┌─────────────┬──────────────┬───────────────┬─────────────────┐
│  SUCCEEDED  │   PARTIAL    │    FAILED     │ CURSOR_PENDING  │
└─────────────┴──────────────┴───────────────┴─────────────────┘
```

- SUCCEEDED: all batches succeeded and cursor advanced
- CURSOR_PENDING: all batches succeeded but cursor advancement failed (optimistic conflict)
- PARTIAL: some batches succeeded, some failed
- FAILED: all batches failed or none executed

TaskRun status mirrors Task status but applies to a single attempt record.

---

## Configuration

Example (application.yaml):

```yaml
task:
  execution:
    lease:
      duration: 60                 # lease duration (seconds)
      renewal-interval: 20         # heartbeat renewal interval (seconds), ~1/3 of duration
    heartbeat:
      failure-threshold: 3         # consecutive heartbeat failure threshold
    max-batches: 1000              # upper bound on batch count
    fail-fast: false               # stop on first batch failure or continue
```

Notes:
- lease.duration: after expiry others may acquire
- lease.renewal-interval: too short stresses DB; too long risks expiry
- heartbeat.failure-threshold: prevents zombie tasks
- max-batches: bound execution time
- fail-fast: controls execution strategy (fail-fast vs best-effort)

---

## Usage Examples

1) Send TaskReadyCommand (via MQ or direct call)

```java
TaskReadyCommand command = new TaskReadyCommand(
    taskId,
    idempotentKey,
    headers
);

taskExecutionUseCase.execute(command);
```

2) Implement a custom BatchPlanner

```java
@Component
public class PubMedBatchPlanner implements BatchPlanner {
    @Override
    public ProvenanceCode getProvenanceCode() { return ProvenanceCode.PUBMED; }

    @Override
    public BatchPlan plan(ExecutionContext context, int maxBatches) {
        // Build batch plan from cursor and execution window
        // return new BatchPlan(batches, totalBatches, exceedsLimit);
    }
}
```

3) Implement a custom BatchExecutor

```java
@Component
public class PubMedBatchExecutor implements BatchExecutor {
    @Override
    public ProvenanceCode getProvenanceCode() { return ProvenanceCode.PUBMED; }

    @Override
    public BatchResult execute(ExecutionContext context, Batch batch) {
        // Call PubMed API
        // return BatchResult.success(batchNo, fetchedCount, nextCursor, storageKey);
        // or return BatchResult.failure(batchNo, errorMessage);
    }
}
```

4) Handling CURSOR_PENDING

When cursor advancement fails due to optimistic conflict, the task is marked CURSOR_PENDING.
Recommended:
- Background job scans CURSOR_PENDING tasks
- Retry advancing the cursor (CursorAdvancer.advance())
- On success mark task SUCCEEDED

---

## Key Design Decisions

1) Why three-phase orchestration?
- Separation of concerns lowers complexity
- Testability: each phase is testable in isolation
- Extensibility: new sources implement BatchPlanner and BatchExecutor

2) Why CURSOR_PENDING?
- Optimistic concurrency: only one node advances
- Decoupled retries: batches succeeded but cursor failed shouldn’t re-execute batches
- Observability: clearly marks asynchronous follow-up work

3) Why distributed leases instead of distributed locks?
- Non-blocking: failed acquisition skips work rather than blocking
- Renewal: heartbeat prevents long-running tasks from lease expiry
- Auto revoke: crashed nodes lose lease automatically

4) Why support both fail-fast and continue-on-error?
- Data integrity first (fail-fast=true)
- Best-effort collection at scale (fail-fast=false)

---

## Observability

Key logs:
- [INGEST][APP] task execution start
- [INGEST][APP] lease acquired
- [INGEST][APP] session created
- [INGEST][APP] batch plan created
- [INGEST][APP] batch succeeded/failed
- [INGEST][APP] task succeeded/marked PARTIAL/marked FAILED

Key metrics: to be defined per service SLOs.

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

# Papertrace Ingest Orchestration Architecture

面向采集链路的三段式编排：Plan（计划生成）→ Outbox Relay（出站发布）→ Task Execution（任务执行会话）。遵循六边形架构与 DDD，应用层负责编排与事务边界，领域层承载规则，基础设施层实现 CAS/续租等复杂 SQL。

## 1) Plan 编排（PlanIngestionOrchestrator）
- 入口：`ingestPlan(PlanIngestionCommand)`
- 职责阶段：
  1. 持久化调度实例（`ing_schedule_instance`）并读取来源配置快照（reg→中立模型）
  2. 查询最新游标水位并解析规划窗口（TIME 策略）
  3. 构建计划表达式原型（仅快照与哈希，不编译）
  4. 前置校验（窗口合理性/背压/能力边界）
  5. 组装 Plan/Slice/Task 蓝图
  6. 幂等复用：若 `plan_key` 已存在，执行失败任务补偿重试；否则落库并发布入队事件
- 关键幂等：
  - `ing_plan.plan_key` 唯一（人类可读/外部幂等）
  - `ing_task.idempotent_key` 唯一（sliceSig+exprHash+op+trigger+params 归一化）
- 快照与哈希：
  - Plan：`provenance_config_snapshot/hash`、表达式原型 `expr_proto_snapshot/hash`
  - Slice：局部表达式 `expr_snapshot/hash`；切片边界 `slice_spec`（时间/ID/token/预算）
- 出站：收集 `TaskQueuedEvent` → `ing_outbox_message`（Outbox 模式）

## 2) 出站发布（OutboxRelayOrchestrator）
- 入口：`relay(OutboxRelayCommand)`
- 职责：
  - 特性开关校验（关闭则返回空报告）
  - 构建 `RelayPlan`（批大小、租约持有者、到期时间、可选 channel=ALL）
  - 执行器扫描 `ing_outbox_message`，在发布租约保护下批量投递到 MQ
  - 汇总 `RelayBatchResult`，发布领域事件，记录统计与耗时
- Outbox 约束：
  - `(channel, dedup_key)` 唯一（源端幂等）
  - 状态与时间游标索引、发布器租约字段（`pub_lease_owner/pub_leased_until`）
  - 建议 `partition_key = provenance:operation`（顺序/分区并发）

## 3) 任务执行会话（TaskExecutionOrchestrator）
- 入口：消费 `INGEST_TASK_READY` 后 `startFromReady(TaskReadyCommand)`
- 幂等闸门：任务已 `SUCCEEDED` 且 `idempotentKey` 匹配则跳过
- 步骤 0：CAS 抢占租约（≤1s）
  - `tryAcquireLease(taskId, owner, now, ttl, idempotentKey)`
  - 租约参数：`papertrace.ingest.exec.lease-ttl-seconds`（默认 60s）
- 步骤 1：初始化执行会话（单事务）
  - `markRunningWithLease`（置 RUNNING + 刷新租约）
  - 计算 `attemptNo`，创建并保存 `ing_task_run`（绑定 schedulerRunId/correlationId、可选执行窗口）
- 心跳续租：按 `heartbeat-interval`（默认 TTL/3）定时 `renewLease`；丢租约仅告警
- 步骤 2：还原配置快照与一致性校验
  - 依据 `task.planId/sliceId` 装载 Plan/Slice
  - 反序列化 `plan.provenance_config_snapshot`，用标准化器计算运行时 hash，与 `plan.provenance_config_hash` 比对
  - 组合 `snapshotHash = sha256(canonicalJson|slice.exprHash)`；耗时>TTL/3 触发一次续租
  - 失败：`markFailed(runId, reason)` 并返回 invalid 结果
- 步骤 3：表达式渲染与编译准备
  - 基于 `slice.expr_snapshot` 还原 `Expr`，`exprCompiler.compile`；后续真正抓取/分页/入库/推进水位由下游执行器承担

## 4) SQL 表与职责映射（核心）
- `ing_schedule_instance`：调度实例（Plan 根）
- `ing_plan`：计划蓝图（窗口/策略；provenance 配置快照+hash；表达式原型）
- `ing_plan_slice`：计划切片（`slice_spec`、局部表达式快照/哈希；并行与幂等边界）
- `ing_task`：任务（租约/心跳/优先级/状态；`idempotent_key` 唯一；队列检索索引）
- `ing_task_run`：运行 attempt（状态/窗口/心跳/统计/错误；(taskId, attemptNo) 唯一）
- `ing_task_run_batch`：运行批次（页码/令牌步进账目；(runId,batchNo) 与 `idempotent_key` 唯一）
- `ing_cursor`：当前水位（(source,op,key,ns) 唯一；时间/ID 归一值便于排序与范围查询）
- `ing_cursor_event`：推进事件（append-only；`idempotent_key` 唯一，支持回放/全链路回溯）
- `ing_outbox_message`：通用 Outbox（去重、状态/时间索引、发布租约与重试）

## 5) 事务与边界
- 应用层事务：执行会话初始化（RUNNING + 新建 run）同事务提交
- 复杂 SQL：CAS 抢占/续租由 infra Mapper XML 实现
- 领域不依赖框架；应用层以编排/日志/异常语义转换为主

## 6) 观测与日志
- 关键节点统一 INFO（入口/复用/成功）+ DEBUG（窗口/表达式/哈希细节）
- 记录 `lease_count/heartbeat`、哈希比对结果与耗时（用于容量评估与告警）

## 7) 风险与改进建议（Roadmap）
- 心跳任务关闭：任务完成/失败后应取消对应心跳定时 future，避免无用续租与噪声
- 失败语义对齐：Step2 失败已 `markFailed(run)`，任务层 FAILED/CANCELLED 的终止语义由谁负责需明确（执行器或编排器）
- 哈希审计：可考虑把 `configHashRuntime/snapshotHash` 冗余入 run/batch，便于事后审计与回放一致性核对
- 租约参数调优：结合 P95 执行/分页时长与网络波动调优 `lease-ttl/heartbeat-interval`，观测 `lease_miss/renew_fail` 指标

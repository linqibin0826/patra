# Ingest 执行流程梳理与问题清单

> 面向应用层与基础设施层的执行链路说明；当前实现基于六边形架构与 DDD 分层。

## 1. 范围与背景
- 处理对象：从计划切片（plan_slice）产生的 task，在消费 MQ 后进入执行期。
- 数据来源：执行期直接使用 plan.provenance_config_snapshot_json（来源配置快照），不再实时访问 registry。
- 目标：明确步骤 0~3 的职责与边界，识别现状风险，给出改进里程碑。

## 2. 执行链路总览（步骤 0~3）
1) 入站消费
- `patra-ingest/patra-ingest-adapter/src/main/java/com/patra/ingest/adapter/inbound/stream/IngestStreamConsumers.java`
- 解析 RocketMQ headers/payload → 组装 `TaskReadyCommand` → 调用应用用例。

2) 步骤 0：CAS 抢占租约
- `TaskRepository.tryAcquireLease(taskId, owner, now, ttl, idemKey)`；失败则优雅退出。

3) 步骤 1：初始化执行会话（事务）
- `markRunningWithLease` 将任务置 RUNNING，并创建 `TaskRun`（attemptNo=latest+1），绑定执行窗口。
- `TaskRunRepository.save` 持久化；`TaskRun` 写入 started/heartbeat。

4) 心跳与续租
- `scheduleHeartbeat` 以固定周期续租，失败仅日志告警（TODO：后续事件/恢复）。

5) 步骤 2：还原配置快照
- 从 `PlanAggregate/PlanSliceAggregate` 取 `provenance_config_snapshot_json` 反序列化为 `ProvenanceConfigSnapshot`。
- 计算 planHash/runtimeHash 并校验；必要时触发一次续租；刷新 `TaskRun` 心跳。

6) 步骤 3：表达式编译（待对接执行引擎）
- `ExprCompiler.compile(expr, provenanceCode)`；尚未衔接 HTTP 执行、分页、限流与重试。

参考：
- `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/execution/TaskExecutionOrchestrator.java`
- `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/*.java`
- `patra-ingest/patra-ingest-infra/src/main/resources/mapper/TaskMapper.xml`

## 3. 关键数据与聚合
- TaskAggregate：调度/租约/状态与执行时间线。
- PlanAggregate/PlanSliceAggregate：计划蓝图、切片签名、expr 与配置快照哈希。
- TaskRun：一次运行 Attempt；承载窗口、心跳、错误与统计。
- ProvenanceConfigSnapshot：来源配置聚合（HTTP/分页/重试/限流/窗口）。

## 4. 事务与租约边界
- 步骤 1 在一个事务内完成：markRunning + insert TaskRun。
- 步骤 2 及后续为非事务步骤；在关键里程碑（心跳、续租、批次提交）点更新。

## 5. 已识别问题清单（优先级由高到低）
1) 任务状态一致性
- 步骤 2 失败仅 `TaskRun.markFailed`，未同步 `TaskAggregate`（FAILED/重入策略）。

2) 心跳生命周期
- 未在任务完成/失败后取消调度；存在空转续租风险。

3) 执行引擎缺失
- 未对接 HTTP 执行、分页/游标推进、限流/重试、检查点落库与落地管道。

4) 运行粒度过大
- 缺乏 `TaskRun` → `TaskRunBatch` 的二次拆分，无法控制单次运行规模与断点续跑。

5) 幂等与对外幂等头
- 缺省 Idempotency-Key 注入策略；重复调用风险由上游重试引起。

6) 观测与指标不足
- 未纳入批次维度指标、重试/失败码分布、续租次数、P95 等。

## 6. 建议与里程碑
- 里程碑 A：状态一致性与心跳取消
  - 失败/完成路径同步 Task 状态；取消心跳；发布事件（如租约丢失）。
- 里程碑 B：批次化执行（见 task_run 拆分设计）
  - 引入 `TaskRunBatchPlanner` 与默认分页/游标策略；执行器按批次推进并更新检查点。
- 里程碑 C：执行引擎与策略
  - 封装 HTTP、限流、重试、Retry-After、幂等头；来源适配器扩展（PubMed/EPMC）。
- 里程碑 D：观测与告警
  - 接入指标/Tracing/MDC；失败码聚合与阈值告警。

## 7. 相关文件参考
- `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/execution/TaskExecutionOrchestrator.java`
- `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/entity/TaskRun.java`
- `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/aggregate/PlanAggregate.java`
- `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/aggregate/PlanSliceAggregate.java`
- `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/snapshot/ProvenanceConfigSnapshot.java`

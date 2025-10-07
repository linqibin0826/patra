# Implementation Plan

## 任务概述

本实施计划将通用数据采集任务执行引擎的开发分为 8 个主要任务，每个任务对应执行流程的一个核心步骤或横切关注点。任务按照依赖关系排序，确保每个任务都能基于前序任务的成果进行开发。

**开发原则**：
- 遵循六边形架构 + DDD，严守依赖方向
- Domain 层不引入任何框架依赖
- 编排层按 ADR-001 拆分：Prepare/Execute/Complete 三个用例 + 轻量 Orchestrator
- 策略接口与注册表置于 Domain；具体实现置于 Infra（BatchPlanner/Executor、CursorAdvancer）
- 批次数超限直接拒绝（抛 `BatchPlanningException`），避免静默部分执行；批次记录分批插入
- 支持批量心跳续租（可配置开启），引入 CURSOR_PENDING 状态与异步重试服务
- 优先实现核心功能，测试任务标记为可选
- 每个任务都包含代码实现和必要的数据库迁移

**预计总工时**：80-100 小时（10-13 个工作日）

---

## 现状核对（patra-ingest）
- Flyway 基线已存在：`patra-ingest-infra/src/main/resources/db/migration/V0.1.0__init_ingest_schema.sql`（含 ing_task/ing_task_run/ing_task_run_batch/ing_cursor/ing_cursor_event/ing_outbox_message 全量表与索引/约束）。
- MyBatis-Plus DO/Mapper/Converter 已存在：Task/TaskRun/TaskRunBatch、Cursor/CursorEvent、OutboxMessage、Plan/PlanSlice、ScheduleInstance。
- 仓储实现已存在（MpImpl）：`TaskRepository`、`TaskRunRepository`、`TaskRunBatchRepository`、`CursorRepository`、`CursorEventRepository`、`OutboxMessageRepository`、`Plan*`、`ScheduleInstance*`。
- 出站发布已存在：`OutboxPublisherPort` + `RocketMqOutboxPublisher`；白名单属性 `papertrace.ingest.outbox.*` 已配置。
- 入站消费者已存在：`IngestStreamConsumers#ingestTaskReadyConsumer`（已解析 payload/headers，尚未调用执行用例）。
- 应用配置已存在：`application.yaml`（Flyway baseline 0.1.0、MQ 绑定、数据源、`papertrace.ingest.exec.*` 租约/心跳配置）；`TaskExecutionConfig` 提供心跳调度线程池。

结论：持久化层/消息出入站已具备；缺口集中在“任务执行引擎”的用例编排、执行上下文装载、表达式编译 Port/Adapter、批次规划/执行、游标推进与重试等。

---

## - [ ] 1. 建立项目结构和领域模型

创建 `patra-ingest` 模块的基础结构，定义核心领域模型、值对象和领域事件。

- [ ] 1.1 创建模块结构和 Maven 配置（已存在，核对依赖）
  - 现状：上述 5 个子模块均已存在；父 POM 依赖链已配置
  - 动作：核对 app/infra 对 domain 的依赖方向未越界；补充缺失依赖（patra-expr-kernel、starter-expr）
  - _Requirements: 所有需求的基础_

- [ ] 1.2 定义领域聚合/实体（已存在，按设计补齐）
  - 现状：`TaskAggregate`、`TaskRun`、`TaskRunBatch`、`Cursor`、`CursorEvent` 已存在
  - 动作：对齐设计命名与职责（沿用现有 `RunStats/BatchStats/LeaseInfo`，避免新增同义 VO）；补齐缺失领域方法（如批次幂等/状态流转的最小接口）
  - _Requirements: 1, 2, 3, 7, 8, 9_

- [ ] 1.3 定义值对象（部分已存在，按现有命名收敛）
  - 现状：`ExecutionWindow`、`BatchStats`、`RunStats`、`LeaseInfo` 已存在
  - 动作：避免新增 `LeaseOwner/ExecutionStats`，统一使用现有 `LeaseInfo/RunStats`；补充 `BatchParams`（如需），与 `TaskRunBatch` 字段对齐
  - _Requirements: 2, 3, 4, 6, 7, 9_

- [ ] 1.4 定义领域事件（可选）
  - 现状：已有 Outbox 事件模型；如需内部领域事件用于观察/记录，可视需要补充
  - _Requirements: 3, 7, 8, 9_

- [ ] 1.5 定义领域端口接口（大部分已存在，新增补齐）
  - 已有：`TaskRepository`、`TaskRunRepository`、`TaskRunBatchRepository`、`CursorRepository`、`CursorEventRepository`、`OutboxPublisherPort`、`Plan*`、`ScheduleInstance*`
  - 新增：`ExpressionCompilerPort`（编译表达式）
  - 预留：`StorageAdapter`（对象存储 Port，供批次执行上传使用）
  - _Requirements: 5, 7, 8, 9_

- [ ]* 1.6 编写领域模型单元测试
  - 测试 `TaskAggregate` 的租约管理和状态转换逻辑
  - 测试 `ExecutionStats` 的状态判断逻辑
  - 测试 `ConfigSnapshot` 的哈希校验逻辑
  - _Requirements: 1, 2, 4, 9_

- [ ] 1.7 用例层目录结构与命名
  - 在 `patra-ingest-app` 下创建 `usecase/execution` 包：`prepare/execute/complete/support/command`
  - 定义 `TaskExecutionOrchestrator` + `PrepareTaskExecutionUseCase`/`ExecuteTaskBatchesUseCase`/`CompleteTaskExecutionUseCase`
  - 支持服务：`LeaseManagementService`、`ExecutionSessionManager`、`ExecutionContextLoader`
  - _Requirements: 1, 2, 3, 4, 6, 7, 8, 9_

---

## - [ ] 2. 持久化层（现状校验与补齐）

- [ ] 2.1 校验 Flyway 迁移（已存在）
  - 现状：`V0.1.0__init_ingest_schema.sql` 已覆盖全部表/索引/唯一键
  - 动作：与设计对齐字段/索引；如需批量续租/优化，补充必要索引（如 ing_task 上 leased_until/status_code 组合覆盖）

- [ ] 2.2 校验/补齐数据对象（已存在）
  - 现状：Task/TaskRun/TaskRunBatch、Cursor/CursorEvent、OutboxMessage、Plan/PlanSlice、ScheduleInstance DO 已齐
  - 动作：按需要补齐缺失字段或注解（如 @Version），避免与 Baseline schema 偏差

- [ ] 2.3 校验 Mapper 与自定义 SQL（部分已存在）
  - 已有：`TaskMapper` 的 `tryAcquireLease/markRunningWithLease/renewLease`
  - 补齐（可选）：批量续租 `batchRenewLeases(taskIds, owner, duration)`
  - _Requirements: 2, 3, 8, 12_

- [ ] 2.4 校验转换器（已存在）
  - MapStruct Converter 已就绪：Task/TaskRun/TaskRunBatch、Cursor/CursorEvent、Plan/PlanSlice、ScheduleInstance、OutboxMessage

- [ ] 2.5 校验仓储实现（已存在 + 补齐）
  - 已有：`*RepositoryMpImpl`
  - 补齐：`TaskRepository#batchRenewLeases`（可选，性能优化）；必要时在 `CursorRepository` 增加幂等/乐观锁语义方法

- [ ]* 2.6 持久化层集成测试
  - 并发 CAS 抢占；续租；Outbox `(channel,dedup_key)` 唯一约束；（如启用）批量续租 SQL

---

## - [ ] 3. 实现幂等检查和租约管理

实现任务就绪通知的幂等检查、分布式租约抢占和心跳续租机制。

- [ ] 3.1 实现幂等检查器
  - 实现 `IdempotencyChecker` 接口
  - 实现 `IdempotencyCheckerImpl`：查询任务状态和幂等键匹配
  - _Requirements: 1, 10_

- [ ] 3.2 实现租约管理服务（封装现有仓储）
  - 实现 `LeaseManagementService`（App 支持服务）：基于 `TaskRepository.tryAcquireLease/renewLease/markRunningWithLease` 封装抢占/续租/释放
  - owner 标识建议：`instanceId:UUID`
  - 添加指标收集（租约抢占/续租/撤销计数）
  - _Requirements: 2, 12_

- [ ] 3.3 实现心跳续租服务
  - 实现 `HeartbeatRenewalService` 接口
  - 实现 `HeartbeatRenewalServiceImpl`：使用 ScheduledExecutorService 定期续租
  - 实现租约验证逻辑（验证 lease_owner 仍为当前节点）
  - 连续失败阈值（默认 3 次）后主动 `validateLease`；租约被接管则抛 `LeaseRevokedException` 并设置标志位 `leaseRevoked`
  - 实现心跳停止和资源释放逻辑
  - 添加 DEBUG 级别日志记录心跳续租结果
  - _Requirements: 3, 12_

- [ ] 3.4 实现执行会话管理器
  - 实现 `ExecutionSessionManager`：创建 TaskRun、启动心跳续租、封装清理（停止心跳+释放租约）
  - 在事务内创建 TaskRun 记录并更新 Task 状态为 RUNNING，attemptNo 自增
  - 绑定 correlationId、schedulerRunId
  - `ExecutionSession` 增强：包含 taskId、leaseOwner、心跳句柄与撤销标志位（leaseRevoked）
  - _Requirements: 3, 12_

- [ ]* 3.5 编写租约管理单元测试
  - 测试租约抢占的并发安全性（模拟多节点）
  - 测试心跳续租的定时执行
  - 测试租约超时后的自动释放
  - _Requirements: 2, 3, 11_

- [ ] 3.6 批量心跳续租服务（可选，性能优化）
  - 实现 `BatchHeartbeatRenewalService`：对当前节点持有的任务批量续租，按批量大小分段执行
  - 在 `TaskRepository` 增加 `batchRenewLeases(taskIds, leaseOwner, leaseDuration)` 自定义 SQL
  - 增加指标 `ingest.heartbeat.batch.renewal`（批量大小、续租成功数）
  - 通过配置项启用：`patra.ingest.task-execution.heartbeat.batch-renewal-enabled=true`
  - _Requirements: 3, 12_

---

## - [ ] 4. 实现配置还原和表达式编译

实现配置快照还原、哈希校验和表达式编译功能。

- [ ] 4.1 实现执行上下文加载器
  - 实现 `ExecutionContextLoader`：从 Task→Slice→Plan 还原配置快照与表达式快照
  - 校验 `exprHash/configHash`；失败抛 `ConfigurationTamperedException`
  - 通过 `ExpressionCompilerPort` 编译表达式，产出 `query/params/normalizedExpression`
  - 编译失败时返回 `isValid=false` 并在编排层抛 `ExpressionCompilationException`
  - 耗时超过租约时长的 1/3 主动续租（心跳）
  - _Requirements: 4, 5, 12_

- [ ] 4.2 实现表达式编译领域端口与适配器
  - 在 Domain 定义 `ExpressionCompilerPort`（record 模型 `ExprCompilationRequest/ExprCompilationResult`）
  - 在 Infra 实现 `ExpressionCompilerAdapter`：适配 `patra-spring-boot-starter-expr` 编译器
  - 失败时返回 `isValid=false` 和 `validationMessage`；由编排层转为 `ExpressionCompilationException`
  - _Requirements: 5_

- [ ] 4.3 对接/扩展 PubMed 表达式编译（如需）
  - 复用 `patra-spring-boot-starter-expr` 能力；必要时扩展 `PubMed` 规则（ESearch 参数：term/retmax/sort 等）
  - 覆盖关键词、时间范围、字段过滤等条件
  - _Requirements: 5_

- [ ] 4.4 对接/扩展 EPMC 表达式编译（如需）
  - 复用 `patra-spring-boot-starter-expr` 能力；必要时扩展 `EPMC` 规则（Search 参数：query/pageSize/sort 等）
  - 覆盖关键词、时间范围、字段过滤等条件
  - _Requirements: 5_

- [ ]* 4.5 编写配置还原和表达式编译单元测试
  - 测试配置哈希校验失败场景
  - 测试表达式编译的正确性
  - 测试不支持的数据源抛出异常
  - _Requirements: 4, 5_

---

## - [ ] 5. 实现批次规划功能

实现批次规划器注册表和不同数据源的批次规划策略。

- [ ] 5.1 实现批次规划器注册表
  - 实现 `BatchPlanner` 策略接口（Domain 层）
  - 实现 `BatchPlannerRegistry`（Domain 层）：根据 provenanceCode 选择规划器
  - 策略实现位于 Infra 层（如 `PubMedBatchPlanner`/`EpmcBatchPlanner`），通过 Spring 注入注册
  - 定义 `BatchPlanningContext`（规划上下文）
  - 定义 `BatchPlan`（批次计划结果）
  - _Requirements: 6_

- [ ] 5.2 实现 PubMed 批次规划器
  - 实现 `PubMedBatchPlanner`：调用 ESearch API 获取总数和 WebEnv
  - 计算批次数：ceil(总量 ÷ 每批大小)；若 > `maxBatchesPerExecution` 则抛出 `BatchPlanningException`（拒绝任务，避免静默丢数）
  - 生成批次记录：每批包含 retstart、retmax、webEnv 参数
  - 为每批生成幂等键（SHA256(runId + ":" + batchNo)）
  - 分批插入批次记录（按 `insertChunkSize`），每批独立事务并记录 DEBUG
  - _Requirements: 6, 10, 11, 12_

- [ ] 5.3 实现 EPMC 批次规划器
  - 实现 `EpmcBatchPlanner`：调用初始 Search API 获取第一页和 cursor token
  - 生成批次记录：每批包含 cursorToken 参数
  - 计算批次数；若 > `maxBatchesPerExecution` 则抛出 `BatchPlanningException`
  - 为每批生成幂等键
  - 分批插入批次记录（按 `insertChunkSize`），记录 DEBUG
  - _Requirements: 6, 10, 11, 12_

- [ ] 5.4 实现批次规划异常处理
  - 定义 `BatchPlanningException` 异常
  - 处理 API 调用失败、网络超时等场景
  - 添加 INFO 级别日志记录批次规划结果
  - _Requirements: 6, 11, 12_

- [ ]* 5.5 编写批次规划单元测试
  - 测试批次数计算的正确性
  - 测试批次参数的生成逻辑
  - 测试幂等键的唯一性
  - _Requirements: 6, 10_

---

## - [ ] 6. 实现批次执行功能

实现批次执行器注册表和不同数据源的批次执行策略，包括 API 调用、数据解析、压缩、上传和 Outbox 发布。

- [ ] 6.1 实现批次执行器注册表
  - 实现 `BatchExecutor` 策略接口（Domain 层）
  - 实现 `BatchExecutorRegistry`（Domain 层）：根据 provenanceCode 选择执行器
  - 策略实现位于 Infra 层（如 `PubMedBatchExecutor`/`EpmcBatchExecutor`）
  - 定义 `BatchExecutionContext`（执行上下文）
  - 定义 `BatchExecutionResult`（执行结果）
  - _Requirements: 7_

- [ ] 6.2 实现对象存储适配器
  - 实现 `StorageAdapter` 接口（Domain 层定义）
  - 实现 `MinIOStorageAdapter`：上传压缩数据到 MinIO
  - 实现路径生成逻辑：{bucket}/{provenanceCode-lower}/{yyyy}/{MM}/run_{runId}/batch_{batchNo(三位补零)}.json.gz
  - 实现重试逻辑：3 次指数退避重试
  - _Requirements: 7, 11_

- [ ] 6.3 实现 PubMed 批次执行器
  - 实现 `PubMedBatchExecutor`：调用 PubMedClient 的 EFetch API
  - 实现批次幂等检查（跳过已成功的批次）
  - 实现响应解析（XML → 领域对象）
  - 实现数据压缩（gzip）
  - 实现对象存储上传
  - 实现 Outbox 消息发布（在事务内）
  - 实现批次状态更新（RUNNING → SUCCEEDED/FAILED）
  - 实现统计信息记录（recordCount、fileSizeBytes、maxTimestamp 等）
  - 添加指标收集（批次执行耗时、记录数）
  - 支持执行模式：`SEQUENTIAL` 或 `PARALLEL`（受 `batch.parallel-threads` 控制）
  - _Requirements: 7, 10, 11, 12_

- [ ] 6.4 实现 EPMC 批次执行器
  - 实现 `EpmcBatchExecutor`：调用 EPMCClient 的 Search API
  - 实现批次幂等检查
  - 实现响应解析（JSON → 领域对象）
  - 实现数据压缩、上传、Outbox 发布、状态更新
  - 实现统计信息记录
  - 添加指标收集
  - 支持执行模式：`SEQUENTIAL` 或 `PARALLEL`
  - _Requirements: 7, 10, 11, 12_

- [ ] 6.5 实现批次执行异常处理
  - 定义 `BatchExecutionException` 异常
  - 处理 API 调用失败：标记批次 FAILED，继续执行其他批次
  - 处理对象存储上传失败：重试 3 次，仍失败则标记 FAILED
  - 处理数据库异常：抛出异常，触发 MQ 重试
  - 添加 INFO/ERROR 级别日志记录批次执行结果
  - _Requirements: 7, 11, 12_

- [ ]* 6.6 编写批次执行单元测试
  - 测试批次幂等检查逻辑
  - 测试部分批次失败不影响其他批次
  - 测试对象存储上传重试逻辑
  - _Requirements: 7, 10, 11_

---

## - [ ] 7. 实现游标推进功能

实现游标推进器注册表和不同操作类型的游标推进策略。

- [ ] 7.1 实现游标推进器注册表
  - 实现 `CursorAdvancer` 策略接口（Domain 层）
  - 实现 `CursorAdvancerRegistry`（Domain 层）：根据 operationType 选择推进器
  - 策略实现位于 Infra 层（如 `TimestampCursorAdvancer`/`IdCursorAdvancer`/`WindowCursorAdvancer`）
  - 定义 `CursorAdvancementContext`（推进上下文）
  - 定义 `CursorAdvancementResult`（推进结果）
  - _Requirements: 8_

- [ ] 7.2 实现时间型游标推进器
  - 实现 `TimestampCursorAdvancer`：从所有 SUCCEEDED 批次的 stats 中提取最大时间戳
  - 实现乐观锁更新游标表（使用 version 字段）
  - 实现游标事件插入（带幂等键）
  - 实现重试逻辑：乐观锁冲突时重试最多 3 次
  - 添加指标收集（游标推进计数）
  - _Requirements: 8, 10, 12_

- [ ] 7.3 实现 ID 型游标推进器
  - 实现 `IdCursorAdvancer`：从所有 SUCCEEDED 批次的 stats 中提取最大记录 ID
  - 实现乐观锁更新和游标事件插入
  - 实现重试逻辑
  - 添加指标收集
  - _Requirements: 8, 10, 12_

- [ ] 7.4 实现窗口型游标推进器（BACKFILL 场景）
  - 实现 `WindowCursorAdvancer`：记录已完成的时间窗口范围
  - 实现乐观锁更新和游标事件插入
  - _Requirements: 8, 10_

- [ ] 7.5 实现游标推进异常处理
  - 处理乐观锁冲突：重试最多 3 次
  - 失败时返回失败原因（记录 ERROR），由收尾器统一设置 `CURSOR_PENDING`
  - 添加 INFO 级别日志记录游标推进结果
  - _Requirements: 8, 11, 12_

- [ ]* 7.6 编写游标推进单元测试
  - 测试时间戳聚合的正确性
  - 测试乐观锁冲突重试逻辑
  - 测试游标事件幂等性
  - _Requirements: 8, 10_

- [ ] 7.7 实现游标推进重试服务（CURSOR_PENDING）
  - 实现 `CursorAdvancementRetryService`：重试 CURSOR_PENDING 任务的游标推进
  - 提供 `retryAdvancement(taskId, runId)` 与 `scanAndRetry()`（定时，每 60 秒）
  - 维护最多 5 次重试计数，超过阈值触发告警
  - 幂等保障：利用 `lastAttemptRunId` 与游标事件幂等键
  - _Requirements: 8, 11, 12_

---

## - [ ] 8. 实现任务编排和收尾

实现任务执行编排器、执行收尾器和 MQ 消费者适配器，完成端到端流程。

- [ ] 8.1 实现执行收尾器
  - 实现 `ExecutionFinalizer` 接口
  - 实现 `ExecutionFinalizerImpl`：聚合统计信息（总批次数、成功/失败批次数、RunStats/文件大小/耗时）
  - 实现最终状态判断逻辑：
    - 全部批次成功且游标推进成功 → SUCCEEDED
    - 全部批次成功但游标推进失败 → CURSOR_PENDING（触发异步重试）
    - 部分批次失败 → PARTIAL
    - 全部批次失败 → FAILED
  - 实现数据库更新（在事务内）：更新 TaskRun 和 Task 记录、清空租约
  - 添加 INFO 级别日志记录任务执行结果
  - _Requirements: 9, 12_

- [ ] 8.2 实现任务执行编排器（ADR-001 拆分落地）
  - 实现 `TaskExecutionUseCase` 接口
  - 实现轻量级 `TaskExecutionOrchestrator`：仅协调 3 个用例并做顶层异常处理/资源清理
    - 调用 `PrepareTaskExecutionUseCase.prepare()`：幂等检查、租约抢占、会话初始化、上下文加载
    - 调用 `ExecuteTaskBatchesUseCase.execute()`：批次规划 + 批次执行（含并发/幂等）
    - 调用 `CompleteTaskExecutionUseCase.complete()`：游标推进 + 状态更新
    - `finally`：`prepareResult.session().cleanup()` 停止心跳并释放租约
  - 处理 `LeaseRevokedException`：快速失败并返回 `leaseRevoked` 结果；记录 WARN
  - 其他异常：记录 ERROR 指标与日志
  - 添加 @Timed/@Counted 注解（Application 层），记录 `ingest.task.execution.result` 指标
  - _Requirements: 1-12_

- [ ] 8.3 实现 MQ 消费者适配器（现有函数式消费者接入 orchestrator）
  - 现状：`IngestStreamConsumers#ingestTaskReadyConsumer` 已解析 payload/headers
  - 动作：在消费者内调用 `TaskExecutionUseCase.execute(...)`（或 Orchestrator）
  - 实现 MDC 设置（correlationId、taskId），异常处理与清理
  - _Requirements: 1, 12_

- [ ] 8.4 实现 XXL-Job 调度器适配器（可选）
  - 实现 `TaskExecutionScheduler`：定时触发任务就绪消息
  - 发送消息到 RocketMQ 的 ingest.task.ready 主题
  - _Requirements: 1_

- [ ] 8.5 实现错误码和异常映射
  - 定义 `TaskExecutionErrorCode` 枚举（ING-1001..ING-1008：租约获取失败/配置哈希失败/表达式编译失败/批次规划失败/批次执行失败/游标推进失败/对象存储上传失败/数据库操作失败）
  - 实现 `ErrorMappingContributor`：将领域异常映射为 ProblemDetail
  - _Requirements: 所有需求_

- [ ]* 8.6 编写任务编排集成测试
  - 测试端到端流程（PubMed 数据源）
  - 测试部分失败场景
  - 测试幂等性（重复执行已成功的任务）
  - 测试租约抢占（多节点并发）
  - _Requirements: 1-12_

---

## - [ ] 9. 实现配置管理和自动配置

实现应用配置、配置属性类和 Spring Boot 自动配置。

- [ ] 9.1 实现配置属性类
  - 实现 `TaskExecutionProperties`：租约、批次、重试、心跳、对象存储等分组配置
  - 关键字段（与现有 `papertrace.ingest.exec.*` 对齐）：
    - `exec.lease-ttl-seconds`、`exec.heartbeat-interval-seconds`
    - `batch.max-batches-per-execution`、`batch.insert-chunk-size`、`batch.execution-mode`、`batch.parallel-threads`
    - `heartbeat.batch-renewal-enabled`、`heartbeat.batch-renewal-size`、`heartbeat.consecutive-failure-threshold`
    - `storage.bucket`、`storage.upload-retry-times`
  - 使用 `@ConfigurationProperties(prefix = "papertrace.ingest")`（分组 `exec/batch/heartbeat/storage`）
  - _Requirements: 2, 3, 6, 7, 11_

- [ ] 9.2 校验/补充应用配置文件
  - 现状：`application.yaml`、`ingest-error-config.yaml`、`ingest-mq-config.yaml` 已存在
  - 动作：补充 `papertrace.ingest.exec`/`batch`/`heartbeat`/`storage` 配置项默认值；必要时新增 `application-local.yaml`
  - _Requirements: 所有需求_

- [ ] 9.3 配置注册
  - 复用现有 `IngestAppConfig` 与 `TaskExecutionConfig`，补充 Task Execution 用例/支持服务 Bean 注册
  - 如需拆分 auto-config，可新增 `TaskExecutionAutoConfiguration` 与 imports 文件（非必须）
  - _Requirements: 所有需求_

- [ ] 9.4 Boot 启动类（已存在）
  - 现状：`PatraIngestApplication` 已存在并启用 Feign；Flyway/MyBatis/Stream 由配置文件启用
  - 动作：无

---

## - [ ] 10. 文档和部署准备

完善文档、创建 Docker Compose 配置和运维脚本。

- [ ] 10.1 编写模块 README
  - 创建 `patra-ingest/README.md`：模块概述、快速开始、核心功能
  - 说明如何启动服务、如何触发任务执行
  - _Requirements: 所有需求_

- [ ] 10.2 编写深度文档
  - 创建 `docs/modules/patra-ingest/deep-dive.md`：详细的架构设计、执行流程、容错机制
  - 包含流程图、时序图、状态机图
  - _Requirements: 所有需求_

- [ ] 10.3 创建 Docker Compose 配置
  - 更新 `docker/compose/docker-compose.dev.yaml`：添加 patra-ingest 服务
  - 配置环境变量、端口映射、依赖关系
  - _Requirements: 所有需求_

- [ ] 10.4 创建运维脚本
  - 创建数据库清理脚本（清理历史执行记录）
  - 创建游标重置脚本（用于重新采集）
  - 创建任务重试脚本（手动重试失败任务）
  - _Requirements: 9, 11_

- [ ] 10.5 更新文档索引
  - 更新 `docs/README.md`：添加 patra-ingest 模块链接
  - 更新 `product.md`：标记 task-execution-engine 为已完成
  - _Requirements: 所有需求_

- [ ] 10.6 运维型作业：孤儿批次清理
  - 实现 `OrphanBatchCleanupTask`：每 6 小时扫描并清理孤儿批次（创建时间 > 24h 且 run 不活跃）
  - 分批删除（每批最多 1000 条），记录清理指标 `ingest.cleanup.orphan_batches`
  - 审计日志：操作者/作业ID/清理数量/时间窗口/关联条件
  - _Requirements: 13_


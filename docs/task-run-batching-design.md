# TaskRun 批次化（task_run_batch）设计

> 目标：将单个 TaskRun 按来源特性拆分为多个可提交的批次（分页/游标/窗口），实现可控执行粒度、断点续跑与并发控制。

## 1. 背景与目标
- 计划阶段已将任务拆到 plan_slice 与 task；执行阶段单个 TaskRun 可能过大。
- 通过批次化：
  - 控制单批规模与耗时，降低租约与超时风险；
  - 支持分页/游标推进与检查点；
  - 提升失败可恢复性与观测精度。

## 2. 设计原则与范围
- 六边形架构：策略/执行封装在应用与适配层，领域对象仅承载状态。
- 幂等优先：批次唯一键严格避免重复插入与重复提交。
- 可观测：批次级指标、日志、Tracing；错误语义标准化。
- 范围：面向 HTTP 拉取型数据源（PubMed/EPMC/...），后续可扩展。

## 3. 概念与术语
- TaskRun：一次任务运行尝试（attempt）。
- TaskRunBatch：TaskRun 内的最小可提交单元（页/令牌/窗口步进）。
- BatchPlan：批次规划结果（可预生成或流式）。
- BatchDescriptor：批次描述（batchNo/page/token/size 期望值等）。
- PaginationState：分页/游标推进状态（pageNo/nextToken 等）。
## 4. 架构与组件
- TaskRunBatchPlanner（应用层）：根据 Task/Run/ConfigSnapshot 生成 BatchPlan。
- SourceBatchStrategy（策略）：PAGE_NUMBER / CURSOR / WINDOW；支持来源自定义。
- TaskRunBatchFactory（领域工厂）：从 BatchDescriptor 生成 TaskRunBatch（RUNNING）。
- TaskRunBatchRepository（仓储）：批量写入/查询/更新批次。
- SourceHttpExecutor（执行器）：按批次执行 HTTP 请求，记录统计并推进检查点。
- RetryCoordinator/RateLimiter：基于 snapshot.retry / snapshot.rateLimit 执行重试与限流。

## 5. 批次规划流程（顺序）
1) 还原快照成功后，Orchestrator 调用 Planner：
   - 若 `findByRunId(runId)` 已有批次 → 走“恢复现场”（幂等）。
   - 否则根据策略产出 BatchPlan 并 `saveAll`。
2) 执行器读取待处理批次（按 batchNo 排序），循环：
   - 构建请求（headers/query/body），应用限流/重试；
   - 解析响应，统计 `recordCount`，写入 `afterToken`；
   - 更新批次状态（SUCCEEDED/FAILED），刷新心跳与（可选）续租；
   - CURSOR 模式可基于 afterToken 生成下一批并落库。
## 6. 策略模式
- PageNumberBatchStrategy：
  - 输入：pageSizeValue、maxPagesPerExecution；
  - 产出：按 pageNo 递增的批次；
  - 适用：PubMed 等页号分页。
- CursorBatchStrategy：
  - 输入：initial token（可为 null）、每页大小（若有）；
  - 产出：依 nextToken 生成的批次链；
  - 适用：CURSOR/TOKEN。
- WindowBatchStrategy：
  - 输入：ExecutionWindow + windowOffset；
  - 产出：按时间切片的批次；
  - 适用：无分页但窗口大。

## 7. 数据模型与字段
- TaskRunBatch（领域）：
  - 基本：id, runId, taskId, sliceId, planId, provenanceCode, operationCode;
  - 批次：batchNo, pageNo, pageSize, beforeToken, afterToken, exprHash;
  - 幂等：idempotentKey（UK）；
  - 状态：status（RUNNING/SUCCEEDED/FAILED/SKIPPED）, stats(recordCount), committedAt, error。

## 8. 幂等键规则
- 建议：`SHA256(taskId + runId + batchNo + beforeToken + windowFrom + windowTo)`；
- 游标为空场景可以 pageNo 代入；重复插入以数据库唯一约束兜底。

## 9. 执行引擎对接
- 执行成功：`TaskRunBatch.succeed(count, afterToken, now)`；
- 执行失败：`TaskRunBatch.fail(error, now)`；
- 每批完成：`TaskRunRepository.touchHeartbeat(runId, now)`；必要时 `TaskRepository.renewLease`；
- 全部批次成功 → `TaskRun.succeed(now)` 并同步 Task；存在失败 → `TaskRun.fail`。

## 10. 状态流转与一致性
- 生成：RUNNING（插入时）→ SUCCEEDED/FAILED/SKIPPED；
- TaskRun 完成条件：所有批次均 SUCCEEDED；
- 失败路径：同步 Task 状态、取消心跳、记录错误码。
## 11. 失败与补偿
- 可重试错误（5xx/网络/429）：按 snapshot.retry 执行退避与重试，耗尽后标记失败；
- 不可重试错误（4xx 配置/权限）：直接失败并告警；
- 补偿：支持基于失败批次重建补偿批次或回队列。

## 12. 观测与日志
- 指标：每批耗时、记录数、重试次数、失败码分布、续租次数；
- 日志：统一 MDC（taskId/runId/batchNo/workerId/correlationId）；
- Tracing：每批请求与解析创建 span。

## 13. 迭代计划
- v1：Planner + PageNumber/Cursor 策略 + 执行循环 + 状态/心跳；
- v2：Retry-After、幂等头、RateLimiter（maxConcurrent/perCredentialQps）；
- v3：Window 批次与跨窗口回放；
- v4：指标/告警与压测优化（批量写、并发度）。

## 14. 风险与规避
- 大量批次插入：引入批量 SQL 或分段生成；
- Token 语义差异：来源自定义策略隔离；
- 重试风暴：限流+熔断（snapshot.retry.circuit*）。

## 15. 开发任务清单（DoD）
- [ ] 新增 `TaskRunBatchPlanner` 与默认策略实现；
- [ ] Orchestrator 接入 Planner 并持久化批次；
- [ ] 执行器循环批次并更新状态+心跳；
- [ ] PubMed 适配验证分页；
- [ ] 指标与日志接入；
- [ ] 失败补偿与告警策略。

## 16. 参考文件
- `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/entity/TaskRunBatch.java`
- `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/TaskRunBatchRepository.java`
- `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/entity/TaskRunBatchDO.java`
- `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/repository/TaskRunBatchRepositoryMpImpl.java`

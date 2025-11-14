/**
 * Task 执行用例协调包。
 *
 * <p>本包实现任务执行的完整生命周期管理，从准备、执行到完成。 这是 patra-ingest 服务的核心用例之一，负责消费任务消息并执行数据采集。
 *
 * <h2>核心职责</h2>
 *
 * <ul>
 *   <li>准备执行上下文（编译表达式、获取租约、启动心跳）
 *   <li>批次构建和执行（根据数据源策略分批处理）
 *   <li>游标推进（记录采集进度）
 *   <li>文献发布（将采集结果发布到下游）
 *   <li>任务完成（更新状态、释放租约、发布完成事件）
 * </ul>
 *
 * <h2>模块结构</h2>
 *
 * <ul>
 *   <li>{@code TaskExecutionUseCase} - 任务执行用例接口（供 Adapter 调用）
 *   <li>{@code TaskExecutionUseCaseImpl} - 顶层编排器（三阶段流程）
 *   <li>{@link com.patra.ingest.app.usecase.execution.prepare} - 准备执行阶段
 *   <li>{@link com.patra.ingest.app.usecase.execution.strategy} - 批次执行策略
 *   <li>{@link com.patra.ingest.app.usecase.execution.complete} - 完成执行阶段
 *   <li>{@link com.patra.ingest.app.usecase.execution.session} - 执行会话管理
 *   <li>{@link com.patra.ingest.app.usecase.execution.lease} - 租约管理
 *   <li>{@link com.patra.ingest.app.usecase.execution.cursor} - 游标推进
 *   <li>{@link com.patra.ingest.app.usecase.execution.coordination} - 批次协调器
 *   <li>{@link com.patra.ingest.app.usecase.execution.publisher} - 文献发布器
 *   <li>{@link com.patra.ingest.app.usecase.execution.idempotency} - 幂等性检查
 *   <li>{@link com.patra.ingest.app.usecase.execution.command} - 输入命令
 *   <li>{@link com.patra.ingest.app.usecase.execution.converter} - 数据转换器
 * </ul>
 *
 * <h2>三阶段编排流程</h2>
 *
 * <pre>
 * Phase 1: 准备阶段（PrepareTaskExecutionUseCase）
 *   ├─ 幂等性检查（如果任务已成功完成 → 跳过）
 *   ├─ 获取任务租约（防止并发执行）
 *   ├─ 加载执行上下文（编译表达式）
 *   ├─ 创建执行会话（Session）
 *   └─ 启动心跳续约（保持租约）
 *
 * Phase 2: 执行阶段（ExecuteTaskBatchesUseCase）
 *   ├─ 批次构建（BatchPlanner：根据数据源策略分批）
 *   ├─ 批次执行（GenericBatchExecutor：调用 Provider API）
 *   ├─ 游标推进（CursorAdvancer：记录进度）
 *   └─ 文献发布（LiteraturePublisherOrchestrator：发布到 Outbox）
 *
 * Phase 3: 完成阶段（CompleteTaskExecutionUseCase）
 *   ├─ 更新任务状态（SUCCEEDED/FAILED）
 *   ├─ 停止心跳续约
 *   ├─ 释放任务租约
 *   └─ 发布 TaskCompletedEvent
 * </pre>
 *
 * <h2>关键设计</h2>
 *
 * <h3>租约机制</h3>
 *
 * <ul>
 *   <li>每个任务执行前必须获取租约（存储在 Redis/DB）
 *   <li>租约有过期时间（如 5 分钟），防止执行节点宕机导致任务永久锁定
 *   <li>执行过程中通过心跳续约延长租约
 *   <li>执行完成后主动释放租约
 * </ul>
 *
 * <h3>幂等性保证</h3>
 *
 * <ul>
 *   <li>准备阶段检查任务状态，如果已成功完成 → 直接返回（幂等跳过）
 *   <li>使用唯一的 {@code idempotentKey}（如 planKey + taskSeq）防止重复消费
 *   <li>任务状态转换是幂等的（使用乐观锁）
 * </ul>
 *
 * <h3>批次执行策略</h3>
 *
 * <ul>
 *   <li><strong>PubMed</strong>: 按 retmax 参数分批（如每批 10000 条）
 *   <li><strong>EPMC</strong>: 按 pageSize 参数分批（如每批 1000 条）
 *   <li><strong>Crossref</strong>: 按 rows 参数分批（如每批 5000 条）
 * </ul>
 *
 * <h3>游标推进</h3>
 *
 * <ul>
 *   <li>每个批次执行成功后推进游标
 *   <li>游标记录最新的采集时间点（highWatermark）
 *   <li>下次采集从游标位置继续（实现增量采集）
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <h3>从 MQ 消息触发执行</h3>
 *
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * @RocketMQMessageListener(
 *     topic = "${patra.ingest.mq.topics.task-ready}",
 *     consumerGroup = "${patra.ingest.mq.consumer-groups.task-ready}"
 * )
 * public class TaskReadyMessageListener implements RocketMQListener<MessageExt> {
 *     private final TaskExecutionUseCase taskExecutionUseCase;
 *
 *     @Override
 *     public void onMessage(MessageExt message) {
 *         // 解析消息
 *         var payload = parsePayload(message);
 *         var headers = parseHeaders(message);
 *
 *         // 构建命令
 *         var command = TaskReadyCommand.builder()
 *             .taskId(payload.getTaskId())
 *             .provenanceCode(payload.getProvenanceCode())
 *             .operationCode(payload.getOperationCode())
 *             .idempotentKey(headers.getPlanKey() + ":" + payload.getTaskSeq())
 *             .build();
 *
 *         // 执行任务
 *         try {
 *             taskExecutionUseCase.execute(command);
 *             log.info("Task execution completed: taskId={}", payload.getTaskId());
 *         } catch (TaskAlreadySucceededException e) {
 *             log.warn("Task already completed (idempotent skip): taskId={}", payload.getTaskId());
 *         } catch (LeaseAcquisitionFailedException e) {
 *             log.warn("Lease acquisition failed (concurrent execution): taskId={}", payload.getTaskId());
 *         } catch (Exception e) {
 *             log.error("Task execution failed: taskId={}", payload.getTaskId(), e);
 *             throw e;  // 触发 MQ 重试
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>手动触发任务执行</h3>
 *
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/ingest/tasks")
 * @RequiredArgsConstructor
 * public class TaskController {
 *     private final TaskExecutionUseCase taskExecutionUseCase;
 *
 *     @PostMapping("/{taskId}/execute")
 *     public ResponseEntity<Void> executeTask(@PathVariable Long taskId) {
 *         var command = TaskReadyCommand.builder()
 *             .taskId(taskId)
 *             .provenanceCode("pubmed")  // 从数据库加载
 *             .operationCode(OperationCode.HARVEST)
 *             .idempotentKey("manual-" + taskId)
 *             .build();
 *
 *         taskExecutionUseCase.execute(command);
 *
 *         return ResponseEntity.ok().build();
 *     }
 * }
 * }</pre>
 *
 * <h2>错误处理</h2>
 *
 * <ul>
 *   <li>{@code TaskAlreadySucceededException}: 任务已成功完成（幂等跳过）
 *   <li>{@code LeaseAcquisitionFailedException}: 租约获取失败（并发冲突）
 *   <li>{@code ExpressionCompilationException}: 表达式编译失败
 *   <li>{@code ProviderApiException}: Provider API 调用失败
 *   <li>{@code TaskExecutionException}: 通用执行失败
 * </ul>
 *
 * <h2>可观测性</h2>
 *
 * <ul>
 *   <li><strong>日志</strong>: 关键阶段记录日志（准备、批次执行、完成）
 *   <li><strong>指标</strong>: 执行时长、批次数、采集数量、失败率
 *   <li><strong>链路追踪</strong>: 通过 SkyWalking 追踪完整流程
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 * @see com.patra.ingest.domain.model.aggregate.TaskAggregate Task 聚合根
 * @see com.patra.ingest.domain.service.TaskDomainService Task 领域服务
 */
package com.patra.ingest.app.usecase.execution;

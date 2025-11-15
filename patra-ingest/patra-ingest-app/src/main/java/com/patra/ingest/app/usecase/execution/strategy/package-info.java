/**
 * 批次执行策略包。
 *
 * <p>本包提供任务的批次执行逻辑，根据数据源策略分批调用 Provider API。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>根据数据源构建批次（如 PubMed 每批 10000 条）
 *   <li>循环执行批次（调用 Provider API）
 *   <li>推进游标（记录采集进度）
 *   <li>发布文献数据到下游
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code ExecuteTaskBatchesUseCase} - 批次执行用例接口
 *   <li>{@code ExecuteTaskBatchesUseCaseImpl} - 批次执行用例实现
 *       <ul>
 *         <li>使用 {@code BatchScheduleBuilder} 构建批次调度
 *         <li>循环执行批次（调用 GenericBatchExecutor）
 *         <li>持久化批次结果（TaskRunBatchRepository）
 *         <li>更新心跳时间戳（TaskRunRepository）
 *       </ul>
 *   <li>{@link com.patra.ingest.app.usecase.execution.strategy.builder} - 批次构建器包
 *       <ul>
 *         <li>{@code BatchScheduleBuilder} - 批次调度构建器（策略模式）
 *         <li>{@code BatchGenerationStrategy} - 批次生成策略接口（domain 层）
 *       </ul>
 * </ul>
 *
 * <h2>批次执行流程</h2>
 *
 * <pre>
 * 1. 批次调度构建（BatchScheduleBuilder）
 *    ├─ 准备查询会话（ProvenanceDataPort）
 *    ├─ 根据 ProvenanceCode 选择对应策略
 *    └─ 生成批次列表
 *
 * 2. 循环执行批次
 *    ├─ 检查租约状态（LeaseHandle）
 *    ├─ 调用 GenericBatchExecutor
 *    ├─ 调用 Provider API（如 PubMed ESearch）
 *    ├─ 解析响应数据
 *    ├─ 持久化批次结果（TaskRunBatchRepository）
 *    ├─ 更新心跳时间戳（TaskRunRepository）
 *    └─ 发布文献数据（LiteraturePublisherOrchestrator）
 *
 * 3. 检查是否有更多批次
 *    ├─ 如果有 → 继续执行
 *    └─ 如果没有 → 返回执行结果
 * </pre>
 *
 * <h2>批次构建示例</h2>
 *
 * <h3>PubMed 批次构建</h3>
 *
 * <pre>
 * 输入：
 *   - 总数: 25000 条
 *   - 每批: 10000 条
 *
 * 输出：
 *   Batch 1: retstart=0, retmax=10000
 *   Batch 2: retstart=10000, retmax=10000
 *   Batch 3: retstart=20000, retmax=5000
 * </pre>
 *
 * <h3>EPMC 批次构建</h3>
 *
 * <pre>
 * 输入：
 *   - 总数: 5500 条
 *   - 每批: 1000 条
 *
 * 输出：
 *   Batch 1: cursorMark=*, pageSize=1000
 *   Batch 2: cursorMark=abc123, pageSize=1000
 *   Batch 3: cursorMark=def456, pageSize=1000
 *   ...（使用 cursorMark 分页）
 * </pre>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class ExecuteTaskBatchesUseCaseImpl implements ExecuteTaskBatchesUseCase {
 *     private final BatchScheduleBuilder batchScheduleBuilder;
 *     private final GenericBatchExecutor batchExecutor;
 *     private final TaskRunBatchRepository batchRepository;
 *     private final TaskRunRepository taskRunRepository;
 *
 *     @Override
 *     public ExecuteResult execute(ExecutionSession session, ExecutionContext context) {
 *         Long taskId = session.taskId();
 *         Long runId = session.runId();
 *
 *         // 1. 构建批次调度
 *         BatchSchedule schedule = batchScheduleBuilder.build(context);
 *
 *         // 2. 验证批次数量
 *         if (schedule.exceedsLimit()) {
 *             throw new BatchLimitExceededException("批次数量超过限制");
 *         }
 *
 *         // 3. 循环执行批次
 *         int succeededCount = 0;
 *         int failedCount = 0;
 *
 *         for (Batch batch : schedule.batches()) {
 *             // 3.1 检查租约状态
 *             if (session.heartbeatHandle() != null &&
 *                 session.heartbeatHandle().isLeaseRevoked()) {
 *                 log.warn("租约已撤销,中止批次执行");
 *                 break;
 *             }
 *
 *             // 3.2 执行批次
 *             BatchResult result;
 *             try {
 *                 result = batchExecutor.execute(context, batch);
 *             } catch (Exception e) {
 *                 result = BatchResult.failure(batch.batchNo(), e.getMessage());
 *             }
 *
 *             // 3.3 持久化批次结果
 *             TaskRunBatch batchEntity = TaskRunBatch.create(context, batch, result);
 *             batchRepository.save(batchEntity);
 *
 *             // 3.4 更新心跳时间戳
 *             taskRunRepository.touchHeartbeat(runId, Instant.now());
 *
 *             // 3.5 更新统计
 *             if (result.success()) {
 *                 succeededCount++;
 *             } else {
 *                 failedCount++;
 *             }
 *         }
 *
 *         return new ExecuteResult(schedule.totalBatches(), succeededCount, failedCount);
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.execution.strategy;

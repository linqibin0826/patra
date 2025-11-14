/**
 * 批次执行策略包。
 *
 * <p>本包提供任务的批次执行逻辑，根据数据源策略分批调用 Provider API。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>根据数据源规划批次（如 PubMed 每批 10000 条）
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
 *   <li>{@link com.patra.ingest.app.usecase.execution.strategy.planner} - 批次规划器
 * </ul>
 *
 * <h2>批次执行流程</h2>
 *
 * <pre>
 * 1. 批次规划（BatchPlanner）
 *    └─ 根据数据源策略生成批次列表
 *
 * 2. 循环执行批次
 *    ├─ 调用 GenericBatchExecutor
 *    ├─ 调用 Provider API（如 PubMed ESearch）
 *    ├─ 解析响应数据
 *    ├─ 推进游标（CursorAdvancer）
 *    └─ 发布文献数据（LiteraturePublisherOrchestrator）
 *
 * 3. 检查是否有更多批次
 *    ├─ 如果有 → 继续执行
 *    └─ 如果没有 → 完成
 * </pre>
 *
 * <h2>批次规划示例</h2>
 *
 * <h3>PubMed 批次规划</h3>
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
 * <h3>EPMC 批次规划</h3>
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
 * @Component
 * @RequiredArgsConstructor
 * public class ExecuteTaskBatchesUseCaseImpl implements ExecuteTaskBatchesUseCase {
 *     private final BatchPlannerRegistry plannerRegistry;
 *     private final GenericBatchExecutor batchExecutor;
 *     private final CursorAdvancer cursorAdvancer;
 *     private final LiteraturePublisherOrchestrator literaturePublisher;
 *
 *     @Override
 *     public void execute(ExecutionSession session) {
 *         var context = session.getContext();
 *
 *         // 1. 获取批次规划器
 *         var planner = plannerRegistry.getPlanner(context.getProvenanceCode());
 *
 *         // 2. 规划批次
 *         var batchPlan = planner.plan(context);
 *
 *         // 3. 循环执行批次
 *         for (var batch : batchPlan.getBatches()) {
 *             // 3.1 执行批次
 *             var batchResult = batchExecutor.execute(batch, context);
 *
 *             // 3.2 推进游标
 *             cursorAdvancer.advance(context.getTaskId(), batchResult.getCursorPosition());
 *
 *             // 3.3 发布文献数据
 *             literaturePublisher.publish(batchResult.getLiteratures(), context);
 *
 *             log.info("Batch completed: taskId={}, batchSeq={}, recordCount={}",
 *                 context.getTaskId(), batch.getSeq(), batchResult.getRecordCount());
 *         }
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.execution.strategy;

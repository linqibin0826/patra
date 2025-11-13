/**
 * 批次规划器包。
 *
 * <p>本包提供不同数据源的批次规划策略，根据数据源特性生成批次执行计划。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>根据数据源策略规划批次（如 PubMed、EPMC、Crossref）
 *   <li>计算批次大小和偏移量
 *   <li>处理分页参数（offset-based 或 cursor-based）
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@code BatchPlanner} - 批次规划器接口
 *   <li>{@code BatchPlannerRegistry} - 批次规划器注册表
 *   <li>{@code UnifiedBatchPlanner} - 统一批次规划器（支持多数据源）
 *       <ul>
 *         <li>使用策略模式委派批次生成逻辑
 *         <li>通过 BatchGenerationStrategy 自动注入所有策略
 *       </ul>
 * </ul>
 *
 * <h2>批次规划策略</h2>
 * <table border="1">
 *   <tr>
 *     <th>数据源</th>
 *     <th>实现类</th>
 *     <th>分页方式</th>
 *     <th>批次大小</th>
 *   </tr>
 *   <tr>
 *     <td>PubMed</td>
 *     <td>UnifiedBatchPlanner + PubmedBatchGenerationStrategy</td>
 *     <td>Offset-based</td>
 *     <td>10000（retmax 参数）</td>
 *   </tr>
 *   <tr>
 *     <td>EPMC</td>
 *     <td>EpmcBatchPlanner</td>
 *     <td>Cursor-based</td>
 *     <td>1000（pageSize 参数）</td>
 *   </tr>
 *   <tr>
 *     <td>Crossref</td>
 *     <td>CrossrefBatchPlanner</td>
 *     <td>Offset-based</td>
 *     <td>5000（rows 参数）</td>
 *   </tr>
 * </table>
 *
 * <h2>分页方式对比</h2>
 * <h3>Offset-based（如 PubMed）</h3>
 * <ul>
 *   <li><strong>优点</strong>: 简单、可预测
 *   <li><strong>缺点</strong>: 大偏移量性能差、数据变化可能导致漏数据
 *   <li><strong>示例</strong>: {@code retstart=0&retmax=10000}
 * </ul>
 *
 * <h3>Cursor-based（如 EPMC）</h3>
 * <ul>
 *   <li><strong>优点</strong>: 性能稳定、不受数据变化影响
 *   <li><strong>缺点</strong>: 无法跳页、cursor 有时效性
 *   <li><strong>示例</strong>: {@code cursorMark=abc123&pageSize=1000}
 * </ul>
 *
 * <h2>使用示例</h2>
 * <h3>统一批次规划器（策略模式）</h3>
 * <pre>{@code
 * // 1. 定义批次生成策略
 * @Component
 * public class PubmedBatchGenerationStrategy implements BatchGenerationStrategy {
 *
 *     @Override
 *     public Class<? extends PlanMetadata> getSupportedType() {
 *         return PubmedPlanMetadata.class;
 *     }
 *
 *     @Override
 *     public List<Batch> generateBatches(PlanMetadata plan, ExecutionContext ctx) {
 *         PubmedPlanMetadata pubmedPlan = (PubmedPlanMetadata) plan;
 *         int batchSize = ctx.provenanceConfigSnapshot().pagination().pageSizeValue();
 *         int totalCount = pubmedPlan.totalCount();
 *         // 生成批次逻辑...
 *         return batches;
 *     }
 * }
 *
 * // 2. UnifiedBatchPlanner 自动注入所有策略
 * @Component
 * public class UnifiedBatchPlanner implements BatchPlanner {
 *
 *     public UnifiedBatchPlanner(
 *             DataSourcePort dataSourcePort,
 *             List<BatchGenerationStrategy> strategies) {
 *         this.dataSourcePort = dataSourcePort;
 *         this.strategyMap = buildStrategyMap(strategies);  // 自动发现策略
 *     }
 *
 *     @Override
 *     public BatchPlan plan(ExecutionContext ctx) {
 *         // 1. 准备计划元数据
 *         PlanMetadata planMetadata = dataSourcePort.preparePlan(ctx, DataType.LITERATURE);
 *
 *         // 2. 根据 PlanMetadata 类型选择对应策略
 *         BatchGenerationStrategy strategy = strategyMap.get(planMetadata.getClass());
 *
 *         // 3. 使用策略生成批次
 *         List<Batch> batches = strategy.generateBatches(planMetadata, ctx);
 *
 *         // 4. 将 planMetadata 附加到 context，供执行阶段使用
 *         ExecutionContext enrichedContext = ctx.withPlanMetadata(planMetadata);
 *
 *         return new BatchPlan(batches, enrichedContext);
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.execution.strategy.planner;

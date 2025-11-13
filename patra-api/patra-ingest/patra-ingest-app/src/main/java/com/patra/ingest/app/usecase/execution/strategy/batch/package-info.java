/**
 * 批次生成策略实现包
 *
 * <p>本包包含各数据源的批次生成策略具体实现。
 *
 * <h2>策略实现类</h2>
 * <ul>
 *   <li>{@link com.patra.ingest.app.usecase.execution.strategy.batch.PubmedBatchGenerationStrategy} - PubMed 批次生成策略（支持 History Server）</li>
 *   <li>{@link com.patra.ingest.app.usecase.execution.strategy.batch.EpmcBatchGenerationStrategy} - EPMC 批次生成策略（支持 cursorMark 游标分页）</li>
 *   <li>{@link com.patra.ingest.app.usecase.execution.strategy.batch.DoajBatchGenerationStrategy} - DOAJ 批次生成策略（支持 Elasticsearch Scroll API）</li>
 * </ul>
 *
 * <h2>扩展指南</h2>
 * <p>添加新数据源的批次生成策略：
 * <ol>
 *   <li>实现 {@link com.patra.ingest.domain.strategy.BatchGenerationStrategy} 接口</li>
 *   <li>使用 {@code @Component} 注解标记为 Spring Bean</li>
 *   <li>实现 {@code getSupportedType()} 返回对应的 PlanMetadata 类型</li>
 *   <li>实现 {@code generateBatches()} 方法生成批次</li>
 * </ol>
 *
 * <h2>示例</h2>
 * <pre>{@code
 * @Component
 * public class DoajBatchGenerationStrategy implements BatchGenerationStrategy {
 *     @Override
 *     public Class<? extends PlanMetadata> getSupportedType() {
 *         return DoajPlanMetadata.class;
 *     }
 *
 *     @Override
 *     public List<Batch> generateBatches(PlanMetadata plan, ExecutionContext ctx) {
 *         DoajPlanMetadata doajPlan = (DoajPlanMetadata) plan;
 *         // 生成批次逻辑
 *     }
 * }
 * }</pre>
 *
 * @author Patra Architecture Team
 * @since 0.1.0
 */
package com.patra.ingest.app.usecase.execution.strategy.batch;

/**
 * 批次构建器包。
 *
 * <p>本包提供批次构建器，根据数据源特性生成批次执行调度表。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>准备抓取元数据（通过 ProvenanceDataPort）
 *   <li>路由到对应的批次生成策略
 *   <li>构建完整的批次调度表
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code BatchScheduleBuilder} - 批次调度构建器（具体类）
 *       <ul>
 *         <li>使用策略模式管理多个 BatchGenerationStrategy
 *         <li>自动注册所有策略实现（通过 Spring 构造器注入）
 *         <li>根据 ProvenanceCode 路由到对应策略
 *         <li>封装通用的构建逻辑（准备元数据、生成批次、包装结果）
 *       </ul>
 *   <li>{@code BatchGenerationStrategy} - 批次生成策略接口（domain 层）
 *       <ul>
 *         <li>每个数据源对应一个策略实现（如 PubmedBatchGenerationStrategy）
 *         <li>声明支持的 ProvenanceCode
 *         <li>生成特定于数据源的批次列表
 *       </ul>
 * </ul>
 *
 * <h2>批次构建策略</h2>
 *
 * <table border="1">
 *   <tr>
 *     <th>数据源</th>
 *     <th>实现类</th>
 *     <th>分页方式</th>
 *     <th>批次大小</th>
 *   </tr>
 *   <tr>
 *     <td>PubMed</td>
 *     <td>BatchScheduleBuilder + PubmedBatchGenerationStrategy</td>
 *     <td>Offset-based</td>
 *     <td>10000（retmax 参数）</td>
 *   </tr>
 *   <tr>
 *     <td>EPMC</td>
 *     <td>BatchScheduleBuilder + EpmcBatchGenerationStrategy</td>
 *     <td>Cursor-based</td>
 *     <td>1000（pageSize 参数）</td>
 *   </tr>
 *   <tr>
 *     <td>DOAJ</td>
 *     <td>BatchScheduleBuilder + DoajBatchGenerationStrategy</td>
 *     <td>Offset-based</td>
 *     <td>100（page size 参数）</td>
 *   </tr>
 * </table>
 *
 * <h2>分页方式对比</h2>
 *
 * <h3>Offset-based（如 PubMed）</h3>
 *
 * <ul>
 *   <li><strong>优点</strong>: 简单、可预测
 *   <li><strong>缺点</strong>: 大偏移量性能差、数据变化可能导致漏数据
 *   <li><strong>示例</strong>: {@code retstart=0&retmax=10000}
 * </ul>
 *
 * <h3>Cursor-based（如 EPMC）</h3>
 *
 * <ul>
 *   <li><strong>优点</strong>: 性能稳定、不受数据变化影响
 *   <li><strong>缺点</strong>: 无法跳页、cursor 有时效性
 *   <li><strong>示例</strong>: {@code cursorMark=abc123&pageSize=1000}
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>开闭原则</strong>: 新增数据源只需实现 BatchGenerationStrategy，无需修改 BatchScheduleBuilder
 *   <li><strong>策略模式</strong>: BatchScheduleBuilder 管理策略路由，具体生成逻辑由策略实现
 *   <li><strong>自动发现</strong>: Spring 自动注入所有策略，通过 getSupportedProvenanceCode() 注册
 *   <li><strong>简化设计</strong>: 使用具体类而非接口，减少抽象层（参考 PlanExpressionBuilder）
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <h3>批次构建器（策略模式）</h3>
 *
 * <pre>{@code
 * // 1. 定义批次生成策略（domain 层）
 * @Component
 * public class PubmedBatchGenerationStrategy implements BatchGenerationStrategy {
 *
 *     @Override
 *     public ProvenanceCode getSupportedProvenanceCode() {
 *         return ProvenanceCode.PUBMED;
 *     }
 *
 *     @Override
 *     public List<Batch> generateBatches(FetchMetadata metadata, ExecutionContext ctx) {
 *         int batchSize = ctx.configSnapshot().pagination().pageSizeValue();
 *         int totalRecords = metadata.totalRecords();
 *         // 生成批次逻辑...
 *         return batches;
 *     }
 * }
 *
 * // 2. BatchScheduleBuilder 自动注入所有策略（application 层）
 * @Component
 * public class BatchScheduleBuilder {
 *
 *     private final ProvenanceDataPort provenanceDataPort;
 *     private final Map<ProvenanceCode, BatchGenerationStrategy> strategyMap;
 *
 *     public BatchScheduleBuilder(
 *             ProvenanceDataPort provenanceDataPort,
 *             List<BatchGenerationStrategy> strategies) {
 *         this.provenanceDataPort = provenanceDataPort;
 *         this.strategyMap = buildStrategyMap(strategies);  // 自动发现策略
 *     }
 *
 *     public BatchSchedule build(ExecutionContext ctx) {
 *         // 1. 准备抓取元数据
 *         FetchMetadata metadata = provenanceDataPort.prepareFetchMetadata(ctx, ctx.dataType());
 *
 *         // 2. 根据 ProvenanceCode 选择对应策略
 *         ProvenanceCode code = metadata.provenanceCode();
 *         BatchGenerationStrategy strategy = strategyMap.get(code);
 *
 *         // 3. 使用策略生成批次
 *         List<Batch> batches = strategy.generateBatches(metadata, ctx);
 *
 *         // 4. 构建并返回 BatchSchedule
 *         return new BatchSchedule(batches, ctx);
 *     }
 * }
 *
 * // 3. 调用方直接使用 BatchScheduleBuilder
 * @Service
 * public class ExecuteTaskBatchesUseCaseImpl {
 *
 *     private final BatchScheduleBuilder batchScheduleBuilder;
 *
 *     public ExecuteResult execute(ExecutionSession session, ExecutionContext ctx) {
 *         // 构建批次调度
 *         BatchSchedule schedule = batchScheduleBuilder.build(ctx);
 *
 *         // 执行批次...
 *     }
 * }
 * }</pre>
 *
 * <h2>优势</h2>
 *
 * <ul>
 *   <li><strong>简洁性</strong>: 消除不必要的接口和注册表，单一具体类完成所有工作
 *   <li><strong>可扩展性</strong>: 新增数据源零修改，只需添加 BatchGenerationStrategy 实现
 *   <li><strong>类型安全</strong>: 使用 ProvenanceCode 枚举作为路由键，编译期检查
 *   <li><strong>一致性</strong>: 命名和设计与 PlanExpressionBuilder 保持一致
 * </ul>
 *
 * @since 0.2.0
 * @author Patra Architecture Team
 */
package com.patra.ingest.app.usecase.execution.strategy.builder;

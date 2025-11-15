/**
 * 批次生成策略实现包
 *
 * <p>本包包含各数据源的批次生成策略具体实现。
 *
 * <h2>策略实现类</h2>
 *
 * <ul>
 *   <li>{@link com.patra.ingest.app.usecase.execution.strategy.batch.PubmedBatchGenerationStrategy}
 *       - PubMed 批次生成策略
 *   <li>{@link com.patra.ingest.app.usecase.execution.strategy.batch.EpmcBatchGenerationStrategy} -
 *       EPMC 批次生成策略（支持 cursorMark 游标分页）
 *   <li>{@link com.patra.ingest.app.usecase.execution.strategy.batch.DoajBatchGenerationStrategy} -
 *       DOAJ 批次生成策略（支持 Elasticsearch Scroll API）
 * </ul>
 *
 * <h2>架构演进说明（v0.3.0）</h2>
 *
 * <h3>重构前（v0.2.x）：Strategy 负责参数映射</h3>
 *
 * <pre>{@code
 * // 旧设计：Strategy 创建包含数据源特定参数的 Batch
 * public class PubmedBatchGenerationStrategy implements BatchGenerationStrategy {
 *     public List<Batch> generateBatches(...) {
 *         JsonNode params = buildPubmedParams(offset, limit);  // ❌ 包含 retstart/retmax
 *         batches.add(new Batch(batchNo, query, params, offset, limit, metadata, ctx));
 *     }
 * }
 * }</pre>
 *
 * <p><strong>问题</strong>：
 *
 * <ul>
 *   <li>❌ 违反单一职责原则：Strategy 同时负责批次计算和参数映射
 *   <li>❌ 违反分层架构：Application 层包含 Infrastructure 层的技术细节
 *   <li>❌ 代码重复：每个 Strategy 都需要实现相似的参数映射逻辑
 * </ul>
 *
 * <h3>重构后（v0.3.0）：Strategy 只负责批次生成</h3>
 *
 * <pre>{@code
 * // 新设计：Strategy 只生成纯领域 Batch（不包含数据源特定参数）
 * public class PubmedBatchGenerationStrategy implements BatchGenerationStrategy {
 *     public List<Batch> generateBatches(QuerySession session, ExecutionContext ctx) {
 *         for (int i = 0; i < pageCount; i++) {
 *             int offset = i * batchSize;
 *             batches.add(new Batch(i + 1, query, offset, batchSize));  // ✅ 纯业务概念
 *         }
 *     }
 * }
 *
 * // 参数映射由 Infrastructure 层的 ParameterMapper 处理
 * }</pre>
 *
 * <p><strong>优势</strong>：
 *
 * <ul>
 *   <li>✅ 符合单一职责原则：Strategy 只关注批次计算逻辑
 *   <li>✅ 符合分层架构：Application 层纯净，技术细节在 Infrastructure 层
 *   <li>✅ 代码复用：参数映射逻辑由统一的 ParameterMapper 处理
 *   <li>✅ 开闭原则：新增数据源参数格式无需修改 Strategy
 * </ul>
 *
 * <h2>职责分离</h2>
 *
 * <table border="1">
 *   <tr>
 *     <th>组件</th>
 *     <th>层级</th>
 *     <th>职责</th>
 *     <th>示例</th>
 *   </tr>
 *   <tr>
 *     <td><strong>BatchGenerationStrategy</strong></td>
 *     <td>Application 层</td>
 *     <td>决定如何生成批次（计算 offset/limit）</td>
 *     <td>根据 totalRecords 和 batchSize 计算批次数</td>
 *   </tr>
 *   <tr>
 *     <td><strong>ProviderParameterMapper</strong></td>
 *     <td>Infrastructure 层</td>
 *     <td>将通用参数映射为数据源特定参数</td>
 *     <td>offset/limit → retstart/retmax（PubMed）</td>
 *   </tr>
 *   <tr>
 *     <td><strong>Batch</strong></td>
 *     <td>Domain 层</td>
 *     <td>封装批次的业务概念</td>
 *     <td>batchNo、query、offset、limit</td>
 *   </tr>
 * </table>
 *
 * <h2>完整的批次处理流程</h2>
 *
 * <pre>{@code
 * // 1. Application 层：BatchScheduleBuilder 准备查询会话
 * QuerySession session = provenanceDataPort.prepareQuerySession(ctx, DataType.LITERATURE);
 * // 结果: totalRecords=10000, stateToken={webEnv: "...", queryKey: "..."}
 *
 * // 2. Application 层：BatchGenerationStrategy 生成批次列表（纯领域模型）
 * List<Batch> batches = pubmedStrategy.generateBatches(session, ctx);
 * // 结果: [Batch(1, "cancer", 0, 500), Batch(2, "cancer", 500, 500), ...]
 *
 * // 3. Infrastructure 层：ProviderParameterMapper 映射为数据源特定参数
 * for (Batch batch : batches) {
 *     JsonNode params = pubmedMapper.mapParameters(batch, baseParams, session);
 *     // 结果: {"retstart": 0, "retmax": 500, "WebEnv": "...", "query_key": "..."}
 *
 *     // 4. Infrastructure 层：ProvenanceDataAdapter 调用数据源 API
 *     DataFetchResult<Literature> result = adapter.fetchData(ctx, DataType.LITERATURE, typeRef, batch);
 * }
 * }</pre>
 *
 * <h2>扩展指南</h2>
 *
 * <p>添加新数据源的批次生成策略（以 Crossref 为例）：
 *
 * <pre>{@code
 * @Component
 * public class CrossrefBatchGenerationStrategy implements BatchGenerationStrategy {
 *
 *     @Override
 *     public ProvenanceCode getSupportedProvenanceCode() {
 *         return ProvenanceCode.CROSSREF;  // 声明支持的数据源
 *     }
 *
 *     @Override
 *     public List<Batch> generateBatches(QuerySession session, ExecutionContext ctx) {
 *         List<Batch> batches = new ArrayList<>();
 *         int batchSize = ctx.configSnapshot().pagination().pageSizeValue();
 *         int totalRecords = session.totalRecords();
 *         String query = ctx.compiledQuery();
 *
 *         // 只需计算批次数和 offset/limit（不包含数据源特定参数）
 *         int pageCount = (int) Math.ceil((double) totalRecords / batchSize);
 *         for (int i = 0; i < pageCount; i++) {
 *             int offset = i * batchSize;
 *             batches.add(new Batch(i + 1, query, offset, batchSize));
 *         }
 *
 *         return batches;
 *     }
 * }
 * }</pre>
 *
 * <p><strong>参数映射由 Infrastructure 层的 {@code CrossrefParameterMapper} 处理</strong>:
 *
 * <pre>{@code
 * @Component
 * public class CrossrefParameterMapper implements ProviderParameterMapper {
 *     @Override
 *     public ProvenanceCode getSupportedProvenance() {
 *         return ProvenanceCode.CROSSREF;
 *     }
 *
 *     @Override
 *     public JsonNode mapParameters(Batch batch, JsonNode baseParams, QuerySession session) {
 *         ObjectNode params = ...;
 *         params.put("offset", batch.offset());   // Crossref 使用 offset 参数
 *         params.put("rows", batch.limit());      // Crossref 使用 rows 参数
 *         return params;
 *     }
 * }
 * }</pre>
 *
 * <h2>设计模式</h2>
 *
 * <h3>1. 策略模式（Strategy Pattern）</h3>
 *
 * <p>{@code BatchGenerationStrategy} 接口定义了批次生成的抽象契约：
 *
 * <pre>{@code
 * public interface BatchGenerationStrategy {
 *     ProvenanceCode getSupportedProvenanceCode();
 *     List<Batch> generateBatches(QuerySession session, ExecutionContext ctx);
 * }
 * }</pre>
 *
 * <h3>2. 注册表模式（Registry Pattern）</h3>
 *
 * <p>{@code BatchScheduleBuilder} 自动发现所有 Strategy 实现并注册：
 *
 * <pre>{@code
 * @Component
 * public class BatchScheduleBuilder {
 *     private final Map<ProvenanceCode, BatchGenerationStrategy> strategyMap;
 *
 *     public BatchScheduleBuilder(List<BatchGenerationStrategy> strategies) {
 *         this.strategyMap = buildStrategyMap(strategies);  // 自动注册
 *     }
 *
 *     public BatchSchedule build(ExecutionContext ctx) {
 *         ProvenanceCode code = session.provenanceCode();
 *         BatchGenerationStrategy strategy = strategyMap.get(code);  // 路由
 *         List<Batch> batches = strategy.generateBatches(session, ctx);
 *         return new BatchSchedule(batches, ctx, session);
 *     }
 * }
 * }</pre>
 *
 * <h2>设计原则</h2>
 *
 * <h3>1. 单一职责原则（SRP）</h3>
 *
 * <ul>
 *   <li><strong>BatchGenerationStrategy</strong>：只关注批次计算逻辑（何时创建批次、如何计算 offset/limit）
 *   <li><strong>ProviderParameterMapper</strong>：只关注参数映射（如何将通用参数转换为数据源特定参数）
 *   <li><strong>Batch</strong>：只关注业务概念（批次编号、查询、分页范围）
 * </ul>
 *
 * <h3>2. 开闭原则（OCP）</h3>
 *
 * <ul>
 *   <li>新增数据源：只需添加 Strategy 和 Mapper 实现，无需修改现有代码
 *   <li>Spring 自动扫描和构造器注入实现零配置扩展
 * </ul>
 *
 * <h3>3. 依赖倒置原则（DIP）</h3>
 *
 * <ul>
 *   <li>Application 层依赖 Domain 层的 Batch 抽象
 *   <li>Infrastructure 层依赖 Domain 层的 Batch 抽象
 *   <li>通过接口解耦，符合六边形架构的端口-适配器模式
 * </ul>
 *
 * <h2>批次生成逻辑示例</h2>
 *
 * <h3>PubMed</h3>
 *
 * <p>PubMed 使用基于 offset 的分页机制：
 *
 * <pre>{@code
 * // totalRecords=10000, batchSize=500
 * // 生成 20 个批次
 * Batch(1, "cancer", 0, 500)     // retstart=0, retmax=500
 * Batch(2, "cancer", 500, 500)   // retstart=500, retmax=500
 * ...
 * Batch(20, "cancer", 9500, 500) // retstart=9500, retmax=500
 * }</pre>
 *
 * <h3>EPMC</h3>
 *
 * <p>EPMC 使用基于 page 的分页机制 + cursorMark 游标：
 *
 * <pre>{@code
 * // totalRecords=10000, batchSize=500
 * // 生成 20 个批次
 * Batch(1, "cancer", 0, 500)     // page=1, pageSize=500, cursorMark="*"
 * Batch(2, "cancer", 500, 500)   // page=2, pageSize=500, cursorMark="nextCursor1"
 * ...
 * Batch(20, "cancer", 9500, 500) // page=20, pageSize=500, cursorMark="nextCursor19"
 * }</pre>
 *
 * <h3>DOAJ</h3>
 *
 * <p>DOAJ 使用 Elasticsearch Scroll API：
 *
 * <pre>{@code
 * // totalRecords=10000, batchSize=500
 * // 生成 20 个批次
 * Batch(1, "cancer", 0, 500)     // page=1, pageSize=500, scroll="5m"
 * Batch(2, "cancer", 500, 500)   // page=2, pageSize=500, scroll_id="scrollId1"
 * ...
 * Batch(20, "cancer", 9500, 500) // page=20, pageSize=500, scroll_id="scrollId19"
 * }</pre>
 *
 * <h2>相关文档</h2>
 *
 * <ul>
 *   <li>{@link com.patra.ingest.domain.model.vo.batch} - Domain 层批次值对象
 *   <li>{@link com.patra.ingest.infra.mapper} - Infrastructure 层参数映射器
 *   <li>{@link com.patra.ingest.app.usecase.execution.strategy.builder.BatchScheduleBuilder} -
 *       批次调度构建器
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.1.0
 */
package com.patra.ingest.app.usecase.execution.strategy.batch;

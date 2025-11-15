/**
 * 批次执行值对象。
 *
 * <p>包含与批次处理和执行相关的值对象:
 *
 * <ul>
 *   <li>{@link com.patra.ingest.domain.model.vo.batch.Batch} - 纯领域批次模型（只包含业务概念）
 *   <li>{@link com.patra.ingest.domain.model.vo.batch.BatchSchedule} - 批次执行调度表
 *   <li>{@link com.patra.ingest.domain.model.vo.batch.BatchResult} - 批次执行结果
 *   <li>{@link com.patra.ingest.domain.model.vo.batch.BatchStats} - 批次统计信息
 * </ul>
 *
 * <h2>架构演进说明（v0.3.0）</h2>
 *
 * <h3>重构前（v0.2.x）：Batch 包含数据源特定参数</h3>
 *
 * <pre>{@code
 * // 旧设计：Batch 包含 7 个字段（混合业务和技术细节）
 * public record Batch(
 *     int batchNo,
 *     String query,
 *     JsonNode params,        // ❌ 数据源特定参数（如 retstart/retmax）
 *     int offset,
 *     int limit,
 *     QuerySession metadata,  // ❌ 技术元数据
 *     ExecutionContext context // ❌ 执行上下文
 * ) { }
 * }</pre>
 *
 * <p><strong>问题</strong>：
 *
 * <ul>
 *   <li>❌ 违反单一职责原则：Batch 同时负责业务概念和技术细节
 *   <li>❌ 违反分层架构：Domain 层的值对象包含 Infrastructure 层的技术细节
 *   <li>❌ 测试复杂：创建 Batch 需要准备完整的技术参数
 * </ul>
 *
 * <h3>重构后（v0.3.0）：Batch 纯领域模型 + Infrastructure 层参数映射</h3>
 *
 * <pre>{@code
 * // 新设计：Batch 只包含 4 个纯业务字段
 * public record Batch(
 *     int batchNo,    // 批次编号
 *     String query,   // 查询字符串
 *     int offset,     // 起始偏移量
 *     int limit       // 获取数量
 * ) { }
 * }</pre>
 *
 * <p><strong>优势</strong>：
 *
 * <ul>
 *   <li>✅ 符合单一职责原则：Batch 只关注业务概念
 *   <li>✅ 符合分层架构：Domain 层纯净，技术细节在 Infrastructure 层
 *   <li>✅ 测试简单：创建 Batch 只需 4 个简单参数
 *   <li>✅ 开闭原则：新增数据源无需修改 Batch 定义
 * </ul>
 *
 * <h2>职责分层</h2>
 *
 * <table border="1">
 *   <tr>
 *     <th>层级</th>
 *     <th>组件</th>
 *     <th>职责</th>
 *     <th>示例</th>
 *   </tr>
 *   <tr>
 *     <td><strong>Domain 层</strong></td>
 *     <td>{@link com.patra.ingest.domain.model.vo.batch.Batch}</td>
 *     <td>定义批次的业务概念（编号、查询、分页范围）</td>
 *     <td><code>new Batch(1, "cancer", 0, 500)</code></td>
 *   </tr>
 *   <tr>
 *     <td><strong>Application 层</strong></td>
 *     <td>{@link com.patra.ingest.app.usecase.execution.strategy.batch.PubmedBatchGenerationStrategy}</td>
 *     <td>决定如何生成批次（计算 offset/limit）</td>
 *     <td>根据 totalRecords 计算需要多少批次</td>
 *   </tr>
 *   <tr>
 *     <td><strong>Infrastructure 层</strong></td>
 *     <td>{@link com.patra.ingest.infra.mapper.PubmedParameterMapper}</td>
 *     <td>将通用参数映射为数据源特定参数</td>
 *     <td>offset/limit → retstart/retmax</td>
 *   </tr>
 * </table>
 *
 * <h2>完整的批次处理流程</h2>
 *
 * <pre>{@code
 * // 1. Application 层：BatchGenerationStrategy 生成纯领域 Batch
 * List<Batch> batches = new ArrayList<>();
 * for (int i = 0; i < pageCount; i++) {
 *     batches.add(new Batch(i + 1, "cancer", i * 500, 500));
 * }
 *
 * // 2. Infrastructure 层：ProviderParameterMapper 映射为数据源特定参数
 * JsonNode pubmedParams = pubmedMapper.mapParameters(
 *     batch,              // 纯领域模型
 *     baseParams,         // 基础参数
 *     querySession        // 会话令牌
 * );
 * // 结果: {"retstart": 0, "retmax": 500, "WebEnv": "...", "query_key": "..."}
 *
 * // 3. Infrastructure 层：ProvenanceDataAdapter 调用数据源 API
 * ProviderRequest request = ProviderRequest.builder()
 *     .query(context.compiledQuery())
 *     .params(pubmedParams)  // 使用映射后的参数
 *     .build();
 *
 * DataFetchResult<Literature> result = provider.fetchData(request, typeRef);
 * }</pre>
 *
 * <h2>设计原则</h2>
 *
 * <h3>1. 单一职责原则（SRP）</h3>
 *
 * <ul>
 *   <li><strong>Batch</strong>：只关注批次的业务概念
 *   <li><strong>BatchGenerationStrategy</strong>：只关注批次生成逻辑
 *   <li><strong>ProviderParameterMapper</strong>：只关注参数映射
 * </ul>
 *
 * <h3>2. 依赖倒置原则（DIP）</h3>
 *
 * <ul>
 *   <li>Domain 层定义抽象（Batch），不依赖具体实现
 *   <li>Infrastructure 层依赖 Domain 层的抽象
 *   <li>Application 层通过接口协调两者
 * </ul>
 *
 * <h3>3. 开闭原则（OCP）</h3>
 *
 * <ul>
 *   <li>新增数据源：只需添加 ParameterMapper 实现，无需修改 Batch
 *   <li>新增批次策略：只需添加 Strategy 实现，无需修改核心逻辑
 * </ul>
 *
 * <h2>相关文档</h2>
 *
 * <ul>
 *   <li>{@link com.patra.ingest.app.usecase.execution.strategy.batch} - Application 层批次生成策略
 *   <li>{@link com.patra.ingest.infra.mapper} - Infrastructure 层参数映射器
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.ingest.domain.model.vo.batch;

/**
 * 批次协调器包。
 *
 * <p>本包提供批次执行的协调逻辑，调用 Provider API 并发布文献数据。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>调用 Provider API（如 PubMed ESearch + EFetch）
 *   <li>解析 API 响应数据
 *   <li>将文献数据发布到下游（通过 Outbox）
 *   <li>处理 API 调用失败和重试
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@code GenericBatchExecutor} - 通用批次执行器
 *       <ul>
 *         <li>调用 ProviderPort 执行批次
 *         <li>返回批次执行结果
 *       </ul>
 *   <li>{@code LiteraturePublisherOrchestrator} - 文献发布编排器
 *       <ul>
 *         <li>将采集的文献数据发布到 Outbox
 *         <li>发布到 {@code INGEST_LITERATURE_READY} 通道
 *       </ul>
 * </ul>
 *
 * <h2>批次执行流程</h2>
 * <pre>
 * 1. 调用 Provider API
 *    ├─ 构建 API 请求（使用编译后的查询和参数）
 *    ├─ 发送 HTTP 请求
 *    └─ 解析响应数据
 *
 * 2. 提取文献数据
 *    └─ 解析 JSON/XML 响应为文献实体
 *
 * 3. 发布文献数据
 *    └─ 发布到 Outbox（LiteratureEventPublisher）
 *
 * 4. 返回批次结果
 *    ├─ recordCount: 采集数量
 *    ├─ cursorPosition: 新的游标位置
 *    └─ literatures: 文献数据列表
 * </pre>
 *
 * <h2>Provider API 调用示例</h2>
 * <h3>PubMed ESearch + EFetch</h3>
 * <pre>
 * Step 1: ESearch（搜索 PMID 列表）
 *   Request: https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi
 *            ?db=pubmed
 *            &term=entrez_date:[2025-01-01 TO 2025-01-02]
 *            &retstart=0
 *            &retmax=10000
 *   Response: { "esearchresult": { "idlist": ["12345", "67890", ...], "count": "25000" } }
 *
 * Step 2: EFetch（批量获取元数据）
 *   Request: https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi
 *            ?db=pubmed
 *            &id=12345,67890,...
 *            &retmode=xml
 *   Response: <PubmedArticleSet>...</PubmedArticleSet>
 * </pre>
 *
 * <h2>使用示例</h2>
 * <h3>通用批次执行器</h3>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class GenericBatchExecutor {
 *     private final ProviderPort providerPort;
 *
 *     public BatchExecutionResult execute(Batch batch, ExecutionContext context) {
 *         // 1. 构建 API 请求
 *         var request = ProviderApiRequest.builder()
 *             .provenanceCode(context.getProvenanceCode())
 *             .operationCode(context.getOperationCode())
 *             .query(context.getCompiledQuery())
 *             .params(mergeBatchParams(context.getCompiledParams(), batch.getParams()))
 *             .build();
 *
 *         // 2. 调用 Provider API
 *         var response = providerPort.execute(request);
 *
 *         // 3. 解析文献数据
 *         var literatures = response.getLiteratures();
 *
 *         // 4. 返回结果
 *         return BatchExecutionResult.builder()
 *             .batchSeq(batch.getSeq())
 *             .recordCount(literatures.size())
 *             .cursorPosition(CursorPosition.builder()
 *                 .highWatermark(response.getMaxPublishDate())
 *                 .batchSeq(batch.getSeq())
 *                 .offset(batch.getOffset() + literatures.size())
 *                 .build())
 *             .literatures(literatures)
 *             .build();
 *     }
 * }
 * }</pre>
 *
 * <h3>文献发布编排器</h3>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class LiteraturePublisherOrchestrator {
 *     private final LiteratureEventPublisher eventPublisher;
 *
 *     public void publish(List<Literature> literatures, ExecutionContext context) {
 *         // 1. 构建事件
 *         var events = literatures.stream()
 *             .map(lit -> new LiteratureReadyEvent(
 *                 lit.getExternalId(),
 *                 context.getProvenanceCode(),
 *                 context.getTaskId(),
 *                 lit
 *             ))
 *             .toList();
 *
 *         // 2. 批量发布到 Outbox
 *         var publishResult = eventPublisher.publish(events);
 *
 *         log.info("Literature published: taskId={}, count={}, batchSeq={}",
 *             context.getTaskId(), events.size(), context.getBatchSeq());
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.execution.coordination;

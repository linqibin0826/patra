/// 批次协调器包。
///
/// 本包提供批次执行的协调逻辑，调用 Provider API 并发布出版物数据。
///
/// ## 职责
///
/// - 调用 Provider API（如 PubMed ESearch + EFetch）
///   - 解析 API 响应数据
///   - 将出版物数据发布到下游（通过 Outbox）
///   - 处理 API 调用失败和重试
///
/// ## 核心组件
///
/// - `GenericBatchExecutor` - 通用批次执行器
///
/// - 调用 ProviderPort 执行批次
///         - 返回批次执行结果
///
///   - `PublicationPublisherOrchestrator` - 出版物发布编排器
///
/// - 将采集的出版物数据发布到 Outbox
///         - 发布到 `INGEST_PUBLICATION_READY` 通道
///
/// ## 批次执行流程
///
/// ```
///
/// 1. 调用 Provider API
///    ├─ 构建 API 请求（使用编译后的查询和参数）
///    ├─ 发送 HTTP 请求
///    └─ 解析响应数据
///
/// 2. 提取出版物数据
///    └─ 解析 JSON/XML 响应为出版物实体
///
/// 3. 发布出版物数据
///    └─ 发布到 Outbox（PublicationEventPublisher）
///
/// 4. 返回批次结果
///    ├─ recordCount: 采集数量
///    ├─ cursorPosition: 新的游标位置
///    └─ publications: 出版物数据列表
///
/// ```
///
/// ## Provider API 调用示例
///
/// ### PubMed ESearch + EFetch
///
/// ```
///
/// Step 1: ESearch（搜索 PMID 列表）
///   Request: https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi
///            ?db=pubmed
///            &term=entrez_date:[2025-01-01 TO 2025-01-02]
///            &retstart=0
///            &retmax=10000
///   Response: { "esearchresult": { "idlist": ["12345", "67890", ...], "count": "25000" } }
///
/// Step 2: EFetch（批量获取元数据）
///   Request: https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi
///            ?db=pubmed
///            &id=12345,67890,...
///            &retmode=xml
///   Response: <PubmedArticleSet>...</PubmedArticleSet>
///
/// ```
///
/// ## 使用示例
///
/// ### 通用批次执行器
///
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class GenericBatchExecutor {
///     private final ProviderPort providerPort;
///
///     public BatchExecutionResult execute(Batch batch, ExecutionContext context) {
///         // 1. 构建 API 请求
///         var request = ProviderApiRequest.builder()
///             .provenanceCode(context.getProvenanceCode())
///             .operationCode(context.getOperationCode())
///             .query(context.getCompiledQuery())
///             .params(mergeBatchParams(context.getCompiledParams(), batch.getParams()))
///             .build();
///
///         // 2. 调用 Provider API
///         var response = providerPort.execute(request);
///
///         // 3. 解析出版物数据
///         var publications = response.getPublications();
///
///         // 4. 返回结果
///         return BatchExecutionResult.builder()
///             .batchSeq(batch.getSeq())
///             .recordCount(publications.size())
///             .cursorPosition(CursorPosition.builder()
///                 .highWatermark(response.getMaxPublishDate())
///                 .batchSeq(batch.getSeq())
///                 .offset(batch.getOffset() + publications.size())
///                 .build())
///             .publications(publications)
///             .build();
/// ```
///
/// ### 出版物发布编排器
///
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class PublicationPublisherOrchestrator {
///     private final PublicationEventPublisher eventPublisher;
///
///     public void publish(List<Publication> publications, ExecutionContext context) {
///         // 1. 构建事件
///         var events = publications.stream()
///             .map(lit -> new PublicationReadyEvent(
///                 lit.getExternalId(),
///                 context.getProvenanceCode(),
///                 context.getTaskId(),
///                 lit
///             ))
///             .toList();
///
///         // 2. 批量发布到 Outbox
///         var publishResult = eventPublisher.publish(events);
///
///         log.info("Publication published: taskId={, count={, batchSeq={",
///             context.getTaskId(), events.size(), context.getBatchSeq());
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.execution.coordination;

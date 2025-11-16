/**
 * PubMed 数据处理器包。
 *
 * <p>提供 PubMed 特定的数据处理器实现，负责从 PubMed E-utilities API 获取、转换和验证出版物数据。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>实现 {@link com.patra.starter.provenance.common.processor.DataProcessor} 接口
 *   <li>处理 PubMed ESearch、EPost、EFetch 的完整流程
 *   <li>将 PubMed XML 响应转换为 {@link com.patra.common.model.CanonicalPublication}
 *   <li>验证出版物数据完整性（PMID、标题等必填字段）
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link PubmedPublicationProcessor} - PubMed 出版物数据处理器
 * </ul>
 *
 * <h2>架构位置</h2>
 *
 * <pre>
 * PubmedDataProvider (数据源提供者)
 *     ↓ 委托
 * PubmedPublicationProcessor (数据处理器) ← [本类]
 *     ↓ 调用
 * PubMedClient (HTTP 客户端) → NCBI E-utilities API
 *     ↓ 使用
 * PubmedPublicationConverter (转换器) → CanonicalPublication
 * </pre>
 *
 * <h2>核心流程</h2>
 *
 * <p><strong>1. ESearch 阶段（搜索文献 ID）</strong>:
 *
 * <pre>{@code
 * ProviderRequest request = ...; // 包含查询和分页参数
 * ProviderContext context = ProviderContext.builder()
 *     .config(config)
 *     .client(pubMedClient)
 *     .build();
 *
 * // 执行搜索
 * ESearchRequest searchRequest = ASSEMBLER.buildList(request.executionParams().params());
 * ESearchResponse searchResponse = pubMedClient.esearch(searchRequest, config);
 *
 * // 提取 PMID 列表
 * List<String> pmids = searchResponse.result().idList();
 * }</pre>
 *
 * <p><strong>2. EPost 阶段（可选，大批量 ID）</strong>:
 *
 * <pre>{@code
 * // 当 PMID 数量 > epostThreshold（默认 200）时
 * if (pmids.size() > 200) {
 *     EPostRequest postRequest = new EPostRequest("pubmed", String.join(",", pmids), ...);
 *     EPostResponse postResponse = pubMedClient.epost(postRequest, config);
 *
 *     // 获取 WebEnv 和 QueryKey
 *     String webEnv = postResponse.webEnv();
 *     String queryKey = postResponse.queryKey();
 * }
 * }</pre>
 *
 * <p><strong>3. EFetch 阶段（获取文献详情）</strong>:
 *
 * <pre>{@code
 * // 使用 PMID 列表或 WebEnv 获取文献
 * EFetchRequest fetchRequest = new EFetchRequest("pubmed", String.join(",", pmids), ...);
 * EFetchResponse fetchResponse = pubMedClient.efetch(fetchRequest, config);
 *
 * // 获取文章列表
 * List<PubmedPublication> articles = fetchResponse.articles();
 * }</pre>
 *
 * <p><strong>4. 转换与验证</strong>:
 *
 * <pre>{@code
 * List<CanonicalPublication> literatures = articles.stream()
 *     .map(converter::toCanonicalPublication)
 *     .filter(lit -> {
 *         ValidationResult validation = processor.validate(lit);
 *         if (!validation.isValid()) {
 *             log.warn("文献验证失败: {}", validation.errors());
 *             return false;
 *         }
 *         return true;
 *     })
 *     .collect(Collectors.toList());
 * }</pre>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class PubmedDataProvider implements ProvenanceDataProvider {
 *
 *     private final PubmedPublicationProcessor publicationProcessor;
 *
 *     @Override
 *     public <T> ProviderResult<T> fetchData(
 *             ProviderRequest request,
 *             DataType dataType,
 *             Class<T> targetClass) {
 *
 *         if (dataType == DataType.PUBLICATION) {
 *             // 准备上下文
 *             ProviderContext context = ProviderContext.builder()
 *                 .config(request.config())
 *                 .build();
 *
 *             // 委托给处理器
 *             ProcessResult<CanonicalPublication> processResult =
 *                 publicationProcessor.process(request, context);
 *
 *             // 转换为 ProviderResult
 *             return convertToProviderResult(processResult, dataType);
 *         }
 *
 *         return ProviderResult.nonRetriableFailure(dataType, "不支持的数据类型");
 *     }
 * }
 * }</pre>
 *
 * <h2>PubMed API 特性</h2>
 *
 * <ul>
 *   <li><b>History Server</b> - 使用 WebEnv 和 QueryKey 管理会话状态
 *   <li><b>EPost 优化</b> - 超过 200 个 PMID 时自动使用 EPost 策略
 *   <li><b>批量限制</b> - EFetch 单次最多 10,000 个 PMID
 *   <li><b>限流保护</b> - 无 API Key: 3 req/s，有 API Key: 10 req/s
 *   <li><b>延迟建议</b> - EPost 后建议延迟 600ms（符合 NCBI 使用规范）
 * </ul>
 *
 * <h2>错误处理</h2>
 *
 * <ul>
 *   <li><b>客户端异常</b> - 捕获 {@link
 *       com.patra.starter.provenance.common.exception.ProvenanceClientException}，返回
 *       ProcessResult.failure()
 *   <li><b>转换失败</b> - 记录失败的 PMID，返回 ProcessResult.partialSuccess()
 *   <li><b>验证失败</b> - 过滤无效数据，记录警告日志
 *   <li><b>中断异常</b> - 处理 InterruptedException，恢复线程中断状态
 * </ul>
 *
 * <h2>性能优化</h2>
 *
 * <ul>
 *   <li><b>批量处理</b> - 使用 EPost 减少 HTTP 请求次数
 *   <li><b>并发控制</b> - 通过 RateLimitConfig 配置最大并发数
 *   <li><b>超时控制</b> - 通过 HttpConfig 配置连接和读取超时
 *   <li><b>指标记录</b> - 可选集成 Micrometer 记录性能指标
 * </ul>
 *
 * <h2>配置参考</h2>
 *
 * <pre>{@code
 * patra:
 *   provenance:
 *     sources:
 *       pubmed:
 *         batching:
 *           epost-threshold: 200        # EPost 阈值（默认 200）
 *         http:
 *           timeout-read-millis: 60000  # 读取超时（默认 30s）
 *         rate-limit:
 *           per-credential-qps-limit: 5 # QPS 限制（默认 5）
 * }</pre>
 *
 * @since 0.1.0
 * @author Patra Architecture Team
 */
package com.patra.starter.provenance.pubmed.processor;

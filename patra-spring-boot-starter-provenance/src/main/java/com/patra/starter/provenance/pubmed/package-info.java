/// PubMed 数据源集成包。
/// 
/// 提供 PubMed E-utilities API 客户端和数据源提供者实现，支持文献搜索、详情获取和批量检索。
/// 使用 Spring RestClient 进行 HTTP 调用。
/// 
/// ## 职责
/// 
/// - 调用 PubMed E-utilities API（ESearch、EFetch、EPost）
///   - 解析 PubMed XML/JSON 响应
///   - 转换为 CanonicalPublication 标准模型
///   - 提供 {@link com.patra.starter.provenance.common.provider.ProvenanceDataProvider} 接口实现
/// 
/// ## 核心组件
/// 
/// - {@link PubMedClient} - PubMed 客户端接口
///   - {@link PubMedClientImpl} - PubMed 客户端实现（基于 RestClient）
///   - {@link PubmedDataProvider} - PubMed 数据源提供者实现
///   - {@link com.patra.starter.provenance.pubmed.converter.PubmedPublicationConverter} - PubMed 出版物转换器
/// 
/// ## 支持的 E-utilities
/// 
/// - **ESearch** - 搜索 PubMed 数据库，返回 PMID 列表
///   - **EFetch** - 根据 PMID 获取出版物详细信息
///   - **EPost** - 上传大量 ID 到 History Server，获取 WebEnv 令牌
/// 
/// ## 使用示例
/// 
/// ```java
/// @Service
/// @RequiredArgsConstructor
/// public class PubMedService {
///     private final PubMedClient client;
/// 
///     public List<String> searchPublication(String query) {
///         // 1. 搜索获取 PMID 列表
///         ESearchRequest searchRequest = new ESearchRequest();
///         searchRequest.setTerm(query);
///         searchRequest.setRetMax(100);
///         ESearchResponse searchResponse = client.esearch(searchRequest);
/// 
///         // 2. 获取出版物详情
///         EFetchRequest fetchRequest = new EFetchRequest();
///         fetchRequest.setId(searchResponse.getIdList());
///         EFetchResponse fetchResponse = client.efetch(fetchRequest);
/// 
///         return fetchResponse.getArticles();
/// ```
/// 
/// ## HTTP 客户端实现
/// 
/// 本包使用 Spring RestClient 进行 HTTP 调用：
/// 
/// - RestClient 在 {@link com.patra.starter.provenance.boot.ProvenanceAutoConfiguration} 中自动配置
///   - baseUrl、超时、默认 Headers 从配置中提取
///   - 底层使用 JDK 21 HttpClient
/// 
/// ## PubMed API 最佳实践
/// 
/// - 使用 API Key 提高限流配额（10 req/s）
///   - 超过 200 个 UID 时使用 EPost + WebEnv
///   - 避免频繁调用，遵守 NCBI 使用政策
///   - 处理分页和批量检索
/// 
/// ## 参考资源
/// 
/// - <a href="https://www.ncbi.nlm.nih.gov/books/NBK25501/">E-utilities 快速入门</a>
///   - <a href="https://www.ncbi.nlm.nih.gov/books/NBK25499/">E-utilities 完整文档</a>
///   - <a href="https://support.nlm.nih.gov/knowledgebase/article/KA-05317/en-us">API Key 申请</a>
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.provenance.pubmed;

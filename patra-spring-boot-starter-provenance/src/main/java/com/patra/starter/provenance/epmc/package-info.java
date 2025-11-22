/// Europe PMC 数据源集成包。
///
/// 提供 Europe PMC RESTful API 客户端和数据源提供者实现，支持文献搜索和元数据检索。
/// 使用 Spring RestClient 进行 HTTP 调用。
///
/// ## 职责
///
/// - 调用 Europe PMC RESTful API
///   - 解析 Europe PMC JSON 响应
///   - 转换为 CanonicalPublication 标准模型
///   - 提供 {@link com.patra.starter.provenance.common.provider.ProvenanceDataProvider} 接口实现
///
/// ## 核心组件
///
/// - {@link EPMCClient} - EPMC 客户端接口
///   - {@link EPMCClientImpl} - EPMC 客户端实现（基于 RestClient）
///
/// ## 支持的 API 端点
///
/// - **Search** - 搜索文献，返回结果列表
///   - **Article Details** - 根据 ID 获取出版物详情（计划中）
///
/// ## 使用示例
///
/// ```java
/// @Service
/// @RequiredArgsConstructor
/// public class EPMCService {
///     private final EPMCClient client;
///
///     public SearchResponse search(String query) {
///         SearchRequest request = new SearchRequest();
///         request.setQuery(query);
///         request.setPageSize(100);
///         return client.search(request);
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
/// ## EPMC API 特点
///
/// - 支持更灵活的查询语法
///   - 提供开放获取出版物的全文访问
///   - 无需 API Key（基础使用）
///   - 限流策略相对宽松
///
/// ## 参考资源
///
/// - <a href="https://europepmc.org/RestfulWebService">Europe PMC RESTful API 文档</a>
///   - <a href="https://europepmc.org/AdvancedSearch">高级搜索语法</a>
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.provenance.epmc;

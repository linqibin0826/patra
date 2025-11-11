/**
 * Europe PMC 数据源集成包。
 *
 * <p>提供 Europe PMC RESTful API 客户端和数据源适配器实现，支持文献搜索和元数据检索。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>调用 Europe PMC RESTful API
 *   <li>解析 Europe PMC JSON 响应
 *   <li>转换为 CanonicalLiterature 标准模型
 *   <li>提供 {@link com.patra.starter.provenance.common.adapter.DataSourceAdapter} 端口实现
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link EPMCClient} - EPMC 客户端接口
 *   <li>{@link EPMCClientImpl} - EPMC 客户端实现
 * </ul>
 *
 * <h2>支持的 API 端点</h2>
 *
 * <ul>
 *   <li><b>Search</b> - 搜索文献，返回结果列表
 *   <li><b>Article Details</b> - 根据 ID 获取文献详情（计划中）
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class EPMCService {
 *     private final EPMCClient client;
 *
 *     public SearchResponse search(String query) {
 *         SearchRequest request = new SearchRequest();
 *         request.setQuery(query);
 *         request.setPageSize(100);
 *         return client.search(request);
 *     }
 * }
 * }</pre>
 *
 * <h2>EPMC API 特点</h2>
 *
 * <ul>
 *   <li>支持更灵活的查询语法
 *   <li>提供开放获取文献的全文访问
 *   <li>无需 API Key（基础使用）
 *   <li>限流策略相对宽松
 * </ul>
 *
 * <h2>参考资源</h2>
 *
 * <ul>
 *   <li><a href="https://europepmc.org/RestfulWebService">Europe PMC RESTful API 文档</a>
 *   <li><a href="https://europepmc.org/AdvancedSearch">高级搜索语法</a>
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.epmc;

/**
 * Europe PMC 请求装配包。
 *
 * <p>提供 Europe PMC API 请求参数的构建和装配工具。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>将请求对象转换为 URL 查询参数
 *   <li>应用 EPMC API 参数约定
 *   <li>处理参数编码和格式化
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link EpmcParamKeys} - EPMC 参数键常量
 *   <li>{@link EpmcSearchRequestAssembler} - 搜索请求装配器
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * SearchRequest request = new SearchRequest();
 * request.setQuery("cancer");
 * request.setPageSize(100);
 *
 * Map<String, String> params = EpmcSearchRequestAssembler.assemble(request);
 * // params: {query=cancer, pageSize=100, format=json}
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.epmc.request;

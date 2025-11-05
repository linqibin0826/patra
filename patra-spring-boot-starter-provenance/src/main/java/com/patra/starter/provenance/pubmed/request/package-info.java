/**
 * PubMed 请求装配包。
 *
 * <p>提供 PubMed API 请求参数的构建和装配工具。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>将请求对象转换为 URL 查询参数
 *   <li>应用 PubMed API 参数约定
 *   <li>处理参数编码和格式化
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link PubMedParamKeys} - PubMed 参数键常量
 *   <li>{@link PubMedESearchRequestAssembler} - ESearch 请求装配器
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * ESearchRequest request = new ESearchRequest();
 * request.setTerm("cancer");
 * request.setRetMax(100);
 *
 * Map<String, String> params = PubMedESearchRequestAssembler.assemble(request);
 * // params: {term=cancer, retmax=100, db=pubmed, retmode=json}
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.pubmed.request;

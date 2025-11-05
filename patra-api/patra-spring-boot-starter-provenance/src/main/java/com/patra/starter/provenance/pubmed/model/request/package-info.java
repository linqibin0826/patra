/**
 * PubMed 请求模型包。
 *
 * <p>定义 PubMed E-utilities API 的请求对象。
 *
 * <h2>请求类型</h2>
 *
 * <ul>
 *   <li>{@link ESearchRequest} - ESearch 搜索请求
 *   <li>{@link EFetchRequest} - EFetch 获取详情请求
 *   <li>{@link EPostRequest} - EPost 上传 ID 列表请求
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // ESearch 请求
 * ESearchRequest searchRequest = new ESearchRequest();
 * searchRequest.setDb("pubmed");
 * searchRequest.setTerm("cancer[Title] AND 2023[PDAT]");
 * searchRequest.setRetMax(100);
 * searchRequest.setUseHistory(true);
 *
 * // EFetch 请求
 * EFetchRequest fetchRequest = new EFetchRequest();
 * fetchRequest.setDb("pubmed");
 * fetchRequest.setId(List.of("12345678", "87654321"));
 * fetchRequest.setRetMode("xml");
 *
 * // EPost 请求
 * EPostRequest postRequest = new EPostRequest();
 * postRequest.setDb("pubmed");
 * postRequest.setId(pmidList); // 超过 200 个 ID
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.pubmed.model.request;

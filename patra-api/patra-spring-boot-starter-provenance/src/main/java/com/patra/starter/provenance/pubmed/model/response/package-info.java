/**
 * PubMed 响应模型包。
 *
 * <p>定义 PubMed E-utilities API 的响应对象，映射 XML/JSON 响应结构。
 *
 * <h2>响应类型</h2>
 *
 * <ul>
 *   <li>{@link ESearchResponse} - ESearch 搜索响应
 *   <li>{@link EFetchResponse} - EFetch 获取详情响应
 *   <li>{@link EPostResponse} - EPost 上传响应
 * </ul>
 *
 * <h2>核心模型</h2>
 *
 * <ul>
 *   <li>{@link PubmedArticle} - 文章完整模型
 *   <li>{@link Article} - 文章基本信息
 *   <li>{@link Journal} - 期刊信息
 *   <li>{@link Author} - 作者信息
 *   <li>{@link MedlineJournalInfo} - Medline 期刊信息
 *   <li>{@link PubmedData} - PubMed 元数据
 * </ul>
 *
 * <h2>XML 映射示例</h2>
 *
 * <pre>{@code
 * <PubmedArticle>
 *   <MedlineCitation>
 *     <PMID>12345678</PMID>
 *     <Article>
 *       <ArticleTitle>Example Title</ArticleTitle>
 *       <Abstract>Example Abstract</Abstract>
 *       <AuthorList>
 *         <Author>
 *           <LastName>Smith</LastName>
 *           <ForeName>John</ForeName>
 *         </Author>
 *       </AuthorList>
 *     </Article>
 *   </MedlineCitation>
 *   <PubmedData>
 *     <ArticleIdList>
 *       <ArticleId IdType="doi">10.1234/example</ArticleId>
 *     </ArticleIdList>
 *   </PubmedData>
 * </PubmedArticle>
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.pubmed.model.response;

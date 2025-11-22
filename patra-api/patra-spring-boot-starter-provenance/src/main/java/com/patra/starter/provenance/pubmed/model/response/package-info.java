/// PubMed 响应模型包。
/// 
/// 定义 PubMed E-utilities API 的响应对象，映射 XML/JSON 响应结构。
/// 
/// ## 响应类型
/// 
/// - {@link ESearchResponse} - ESearch 搜索响应
///   - {@link EFetchResponse} - EFetch 获取详情响应
///   - {@link EPostResponse} - EPost 上传响应
/// 
/// ## 核心模型
/// 
/// - {@link PubmedPublication} - 文章完整模型（聚合根）
///       
/// - {@link PubmedPublication.Article} - 文章基本信息
///         - {@link PubmedPublication.Journal} - 期刊信息
///         - {@link PubmedPublication.Author} - 作者信息
///         - {@link PubmedPublication.MedlineJournalInfo} - Medline 期刊信息
///         - {@link PubmedPublication.PubmedData} - PubMed 元数据
/// 
/// 注意：Article、Journal、Author、MedlineJournalInfo、PubmedData 现在都是 PubmedPublication
/// 的内部类，作为聚合根的值对象存在。
/// 
/// ## XML 映射示例
/// 
/// ```java
/// <PubmedPublication>
///   <MedlineCitation>
///     <PMID>12345678</PMID>
///     <Article>
///       <ArticleTitle>Example Title</ArticleTitle>
///       <Abstract>Example Abstract</Abstract>
///       <AuthorList>
///         <Author>
///           <LastName>Smith</LastName>
///           <ForeName>John</ForeName>
///         </Author>
///       </AuthorList>
///     </Article>
///   </MedlineCitation>
///   <PubmedData>
///     <ArticleIdList>
///       <ArticleId IdType="doi">10.1234/example</ArticleId>
///     </ArticleIdList>
///   </PubmedData>
/// </PubmedPublication>
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.provenance.pubmed.model.response;

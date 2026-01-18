package com.patra.catalog.infra.adapter.parser;

/// PubMed XML 元素名称常量。
///
/// 定义 PubMed Baseline XML 中使用的元素和属性名称。
/// 基于 PubMed 2025 DTD (pubmed_250101.dtd)。
///
/// **XML 结构概览**：
///
/// ```xml
/// <PubmedArticleSet>
///   <PubmedArticle>
///     <MedlineCitation Status="MEDLINE">
///       <PMID Version="1">12345678</PMID>
///       <Article PubModel="Print">
///         <Journal>
///           <ISSN IssnType="Print">1234-5678</ISSN>
///           <JournalIssue CitedMedium="Print">
///             <Volume>10</Volume>
///             <Issue>2</Issue>
///             <PubDate>
///               <Year>2024</Year>
///               <Month>Jun</Month>
///               <Day>15</Day>
///             </PubDate>
///           </JournalIssue>
///           <Title>Journal of Examples</Title>
///         </Journal>
///         <ArticleTitle>Sample Article Title</ArticleTitle>
///         <VernacularTitle>样本文章标题</VernacularTitle>
///         <Language>eng</Language>
///         <AuthorList CompleteYN="Y">...</AuthorList>
///       </Article>
///       <MedlineJournalInfo>
///         <NlmUniqueID>101234567</NlmUniqueID>
///         <ISSNLinking>1111-2222</ISSNLinking>
///       </MedlineJournalInfo>
///     </MedlineCitation>
///     <PubmedData>
///       <PublicationStatus>epublish</PublicationStatus>
///       <ArticleIdList>
///         <ArticleId IdType="doi">10.1000/example</ArticleId>
///         <ArticleId IdType="pmc">PMC1234567</ArticleId>
///       </ArticleIdList>
///     </PubmedData>
///   </PubmedArticle>
/// </PubmedArticleSet>
/// ```
///
/// @author linqibin
/// @since 0.1.0
public final class PubmedXmlElements {

  private PubmedXmlElements() {
    throw new UnsupportedOperationException("常量类禁止实例化");
  }

  /// 根元素和容器元素名称。
  public static final class Container {
    public static final String ARTICLE_SET = "PubmedArticleSet";
    public static final String PUBMED_ARTICLE = "PubmedArticle";
    public static final String MEDLINE_CITATION = "MedlineCitation";
    public static final String ARTICLE = "Article";
    public static final String PUBMED_DATA = "PubmedData";

    private Container() {}
  }

  /// 标识符相关元素名称。
  public static final class Identifier {
    public static final String PMID = "PMID";
    public static final String ARTICLE_ID = "ArticleId";
    public static final String ARTICLE_ID_LIST = "ArticleIdList";
    public static final String NLM_UNIQUE_ID = "NlmUniqueID";

    private Identifier() {}
  }

  /// 期刊相关元素名称。
  public static final class Journal {
    public static final String JOURNAL = "Journal";
    public static final String ISSN = "ISSN";
    public static final String JOURNAL_ISSUE = "JournalIssue";
    public static final String VOLUME = "Volume";
    public static final String ISSUE = "Issue";
    public static final String TITLE = "Title";
    public static final String MEDLINE_JOURNAL_INFO = "MedlineJournalInfo";
    public static final String ISSN_LINKING = "ISSNLinking";

    private Journal() {}
  }

  /// 文章相关元素名称。
  public static final class Article {
    public static final String ARTICLE_TITLE = "ArticleTitle";
    public static final String VERNACULAR_TITLE = "VernacularTitle";
    public static final String LANGUAGE = "Language";
    public static final String AUTHOR_LIST = "AuthorList";
    public static final String PUBLICATION_STATUS = "PublicationStatus";

    private Article() {}
  }

  /// 日期相关元素名称。
  public static final class Date {
    public static final String PUB_DATE = "PubDate";
    public static final String YEAR = "Year";
    public static final String MONTH = "Month";
    public static final String DAY = "Day";
    public static final String MEDLINE_DATE = "MedlineDate";

    private Date() {}
  }

  /// 属性名称。
  public static final class Attribute {
    public static final String ISSN_TYPE = "IssnType";
    public static final String ID_TYPE = "IdType";
    public static final String COMPLETE_YN = "CompleteYN";

    // ISSN Type 属性值
    public static final String ISSN_TYPE_PRINT = "Print";
    public static final String ISSN_TYPE_ELECTRONIC = "Electronic";

    // ArticleId IdType 属性值
    public static final String ID_TYPE_DOI = "doi";
    public static final String ID_TYPE_PMC = "pmc";
    public static final String ID_TYPE_PUBMED = "pubmed";

    private Attribute() {}
  }
}

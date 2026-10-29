package dev.patra.catalog.infra.publication.parser;

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
    public static final String CITATION_SUBSET = "CitationSubset";
    public static final String ARTICLE = "Article";
    public static final String PUBMED_DATA = "PubmedData";
    public static final String DELETE_CITATION = "DeleteCitation";

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
    public static final String ISO_ABBREVIATION = "ISOAbbreviation";
    public static final String MEDLINE_TA = "MedlineTA";

    private Journal() {}
  }

  /// 文章相关元素名称。
  public static final class Article {
    public static final String ARTICLE_TITLE = "ArticleTitle";
    public static final String VERNACULAR_TITLE = "VernacularTitle";
    public static final String LANGUAGE = "Language";
    public static final String AUTHOR_LIST = "AuthorList";
    public static final String PUBLICATION_STATUS = "PublicationStatus";
    public static final String ABSTRACT = "Abstract";
    public static final String ABSTRACT_TEXT = "AbstractText";
    public static final String COPYRIGHT_INFORMATION = "CopyrightInformation";
    public static final String PUBLICATION_TYPE_LIST = "PublicationTypeList";
    public static final String PUBLICATION_TYPE = "PublicationType";
    public static final String PAGINATION = "Pagination";
    public static final String MEDLINE_PGN = "MedlinePgn";
    public static final String START_PAGE = "StartPage";
    public static final String END_PAGE = "EndPage";
    public static final String E_LOCATION_ID = "ELocationID";

    private Article() {}
  }

  /// 作者相关元素名称。
  public static final class Author {
    public static final String AUTHOR = "Author";
    public static final String LAST_NAME = "LastName";
    public static final String FORE_NAME = "ForeName";
    public static final String INITIALS = "Initials";
    public static final String SUFFIX = "Suffix";
    public static final String COLLECTIVE_NAME = "CollectiveName";
    public static final String AFFILIATION_INFO = "AffiliationInfo";
    public static final String AFFILIATION = "Affiliation";
    public static final String IDENTIFIER = "Identifier";

    private Author() {}
  }

  /// MeSH 相关元素名称。
  public static final class MeSH {
    public static final String MESH_HEADING_LIST = "MeshHeadingList";
    public static final String MESH_HEADING = "MeshHeading";
    public static final String DESCRIPTOR_NAME = "DescriptorName";
    public static final String QUALIFIER_NAME = "QualifierName";

    private MeSH() {}
  }

  /// 补充 MeSH 概念相关元素名称。
  public static final class SupplMesh {
    public static final String SUPPL_MESH_LIST = "SupplMeshList";
    public static final String SUPPL_MESH_NAME = "SupplMeshName";

    private SupplMesh() {}
  }

  /// 关键词相关元素名称。
  public static final class Keyword {
    public static final String KEYWORD_LIST = "KeywordList";
    public static final String KEYWORD = "Keyword";

    private Keyword() {}
  }

  /// 其他语言摘要相关元素名称。
  public static final class OtherAbstract {
    public static final String OTHER_ABSTRACT = "OtherAbstract";
    public static final String COPYRIGHT_INFORMATION = "CopyrightInformation";

    private OtherAbstract() {}
  }

  /// 日期相关元素名称。
  public static final class Date {
    public static final String PUB_DATE = "PubDate";
    public static final String YEAR = "Year";
    public static final String MONTH = "Month";
    public static final String DAY = "Day";
    public static final String MEDLINE_DATE = "MedlineDate";
    public static final String SEASON = "Season";
    public static final String HISTORY = "History";
    public static final String PUBMED_PUB_DATE = "PubMedPubDate";

    private Date() {}
  }

  /// 属性名称。
  public static final class Attribute {
    public static final String ISSN_TYPE = "IssnType";
    public static final String ID_TYPE = "IdType";
    public static final String COMPLETE_YN = "CompleteYN";
    public static final String VALID_YN = "ValidYN";
    public static final String E_ID_TYPE = "EIdType";
    public static final String EQUAL_CONTRIB = "EqualContrib";
    public static final String MAJOR_TOPIC_YN = "MajorTopicYN";
    public static final String UI = "UI";
    public static final String TYPE = "Type";
    public static final String SOURCE = "Source";
    public static final String OWNER = "Owner";
    public static final String LABEL = "Label";
    public static final String LANGUAGE = "Language";
    public static final String STATUS = "Status";
    public static final String INDEXING_METHOD = "IndexingMethod";
    public static final String PUB_STATUS = "PubStatus";

    // PubMedPubDate PubStatus 属性值
    public static final String PUB_STATUS_PMC_RELEASE = "pmc-release";

    // ISSN Type 属性值
    public static final String ISSN_TYPE_PRINT = "Print";
    public static final String ISSN_TYPE_ELECTRONIC = "Electronic";

    // ArticleId IdType 属性值
    public static final String ID_TYPE_DOI = "doi";
    public static final String ID_TYPE_PMC = "pmc";
    public static final String ID_TYPE_PUBMED = "pubmed";

    // Author Identifier Source 属性值
    public static final String SOURCE_ORCID = "ORCID";

    private Attribute() {}
  }
}

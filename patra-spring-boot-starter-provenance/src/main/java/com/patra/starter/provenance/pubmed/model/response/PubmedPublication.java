package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlText;

/// 从XML直接解析的PubMed文章简化视图。
///
/// 该类封装了PubMed文章的核心信息,包括PMID、文章元数据、期刊信息和补充数据。 提供便捷的访问器方法用于获取关键字、文章标识符等常用信息。
///
/// @author linqibin
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PubmedPublication {

  private static final PubmedData EMPTY_PUBMED_DATA = new PubmedData();

  /// Medline引用信息,包含PMID和文章核心元数据
  @JacksonXmlProperty(localName = "MedlineCitation")
  private MedlineCitation medlineCitation;

  /// PubMed补充数据,包含历史事件和文章标识符
  @JacksonXmlProperty(localName = "PubmedData")
  private PubmedData pubmedData;

  public PubmedPublication() {}

  /// 返回PubMed标识符(PMID)
  public String pmid() {
    return medlineCitation != null ? medlineCitation.pmid() : null;
  }

  /// 返回核心文章元数据
  public Article article() {
    return medlineCitation != null ? medlineCitation.article() : null;
  }

  /// 返回Medline报告的期刊信息
  public MedlineJournalInfo journalInfo() {
    return medlineCitation != null ? medlineCitation.journalInfo() : null;
  }

  /// 返回PubMed补充数据,如历史事件和文章标识符
  public PubmedData pubmedData() {
    return pubmedData != null ? pubmedData : EMPTY_PUBMED_DATA;
  }

  /// 返回从所有关键字块合并的关键字列表
  public List<String> keywords() {
    return medlineCitation != null ? medlineCitation.keywords() : List.of();
  }

  /// 返回关键字列表集合（包含来源和主题标志）。
  ///
  /// @return 关键字列表集合，如果没有则返回空列表
  public List<KeywordList> keywordLists() {
    return medlineCitation != null ? medlineCitation.keywordLists() : List.of();
  }

  /// 返回文章标识符列表的便捷访问器(例如DOI、PMC)
  public List<PubmedData.ArticleId> articleIds() {
    return pubmedData != null ? pubmedData.articleIds() : List.of();
  }

  /// 返回文献创建日期
  public DateInfo dateCreated() {
    return medlineCitation != null ? medlineCitation.dateCreated() : null;
  }

  /// 返回文献完成日期
  public DateInfo dateCompleted() {
    return medlineCitation != null ? medlineCitation.dateCompleted() : null;
  }

  /// 返回文献修订日期
  public DateInfo dateRevised() {
    return medlineCitation != null ? medlineCitation.dateRevised() : null;
  }

  /// 返回数据记录的所有者或来源
  public String owner() {
    return medlineCitation != null ? medlineCitation.owner() : null;
  }

  /// 返回文献记录的处理状态
  public String status() {
    return medlineCitation != null ? medlineCitation.status() : null;
  }

  /// 返回文献记录的标引方式
  public String indexingMethod() {
    return medlineCitation != null ? medlineCitation.indexingMethod() : null;
  }

  /// 返回化学物质列表
  public List<Chemical> chemicals() {
    return medlineCitation != null ? medlineCitation.chemicals() : List.of();
  }

  /// 返回引文子集标识
  public String citationSubset() {
    return medlineCitation != null ? medlineCitation.citationSubset() : null;
  }

  /// 返回参考文献数量
  public Integer numberOfReferences() {
    return medlineCitation != null ? medlineCitation.numberOfReferences() : null;
  }

  /// 返回利益冲突声明
  public String coiStatement() {
    return medlineCitation != null ? medlineCitation.coiStatement() : null;
  }

  /// 返回 MeSH 标引列表。
  ///
  /// MeSH (Medical Subject Headings) 是美国国家医学图书馆创建的受控词表, 用于标引医学出版物的主题和内容。每个 MeSH 标引项包含一个主题词和可选的限定词。
  ///
  /// @return MeSH 标引列表,如果没有则返回空列表
  public List<MeshHeading> meshHeadings() {
    return medlineCitation != null ? medlineCitation.meshHeadings() : List.of();
  }

  /// 返回补充 MeSH 概念列表。
  ///
  /// 补充 MeSH 概念用于描述疾病、药物试验等特定主题。
  ///
  /// @return 补充 MeSH 概念列表,如果没有则返回空列表
  public List<SupplMeshName> supplMeshNames() {
    return medlineCitation != null ? medlineCitation.supplMeshNames() : List.of();
  }

  /// 返回评论、更正、撤稿信息列表。
  ///
  /// 包含与本文献相关的评论、更正、撤稿、勘误等信息。
  ///
  /// @return 评论更正信息列表,如果没有则返回空列表
  public List<CommentsCorrections> commentsCorrections() {
    return medlineCitation != null ? medlineCitation.commentsCorrections() : List.of();
  }

  /// 返回基因符号列表。
  ///
  /// 与本文献相关的基因符号标识。
  ///
  /// @return 基因符号列表,如果没有则返回空列表
  public List<String> geneSymbols() {
    return medlineCitation != null ? medlineCitation.geneSymbols() : List.of();
  }

  /// 返回其他 ID 列表（如 PMC ID）。
  ///
  /// 除 PMID 外的其他标识符,如 PMC ID、NLM ID 等。
  ///
  /// @return 其他 ID 列表,如果没有则返回空列表
  public List<OtherId> otherIds() {
    return medlineCitation != null ? medlineCitation.otherIds() : List.of();
  }

  /// 返回其他语言的摘要列表。
  ///
  /// 除主摘要外的其他语言版本摘要。
  ///
  /// @return 其他语言摘要列表,如果没有则返回空列表
  public List<OtherAbstract> otherAbstracts() {
    return medlineCitation != null ? medlineCitation.otherAbstracts() : List.of();
  }

  /// 返回作为主题的人物列表。
  ///
  /// 以人物为主题的文献中涉及的人物信息。
  ///
  /// @return 人物主题列表,如果没有则返回空列表
  public List<PersonalNameSubject> personalNameSubjects() {
    return medlineCitation != null ? medlineCitation.personalNameSubjects() : List.of();
  }

  /// 返回研究者列表。
  ///
  /// 参与研究但非文章作者的研究者信息。
  ///
  /// @return 研究者列表,如果没有则返回空列表
  public List<Investigator> investigators() {
    return medlineCitation != null ? medlineCitation.investigators() : List.of();
  }

  /// 返回 NLM 内部注释列表。
  ///
  /// NLM 对文献的内部注释和备注信息。
  ///
  /// @return NLM 注释列表,如果没有则返回空列表
  public List<GeneralNote> generalNotes() {
    return medlineCitation != null ? medlineCitation.generalNotes() : List.of();
  }

  /// 返回航天任务列表。
  ///
  /// 与本文献相关的航天任务名称列表。
  ///
  /// @return 航天任务列表,如果没有则返回空列表
  public List<String> spaceFlightMissions() {
    return medlineCitation != null ? medlineCitation.spaceFlightMissions() : List.of();
  }

  /// Medline引用信息的内部表示
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class MedlineCitation {

    /// PubMed标识符对象
    @JacksonXmlProperty(localName = "PMID")
    private Pmid pmid;

    /// 文章核心元数据
    @JacksonXmlProperty(localName = "Article")
    private Article article;

    /// Medline期刊信息
    @JacksonXmlProperty(localName = "MedlineJournalInfo")
    private MedlineJournalInfo journalInfo;

    /// 关键字列表集合
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "KeywordList")
    private List<KeywordList> keywordLists;

    /// 文献创建日期
    @JacksonXmlProperty(localName = "DateCreated")
    private DateInfo dateCreated;

    /// 文献完成日期
    @JacksonXmlProperty(localName = "DateCompleted")
    private DateInfo dateCompleted;

    /// 文献修订日期
    @JacksonXmlProperty(localName = "DateRevised")
    private DateInfo dateRevised;

    /// 数据记录的所有者或来源
    @JacksonXmlProperty(isAttribute = true, localName = "Owner")
    private String owner;

    /// 文献记录的处理状态
    @JacksonXmlProperty(isAttribute = true, localName = "Status")
    private String status;

    /// 文献记录的标引方式
    @JacksonXmlProperty(isAttribute = true, localName = "IndexingMethod")
    private String indexingMethod;

    /// 引文子集标识
    @JacksonXmlProperty(localName = "CitationSubset")
    private String citationSubset;

    /// 参考文献数量
    @JacksonXmlProperty(localName = "NumberOfReferences")
    private Integer numberOfReferences;

    /// 利益冲突声明
    @JacksonXmlProperty(localName = "CoiStatement")
    private String coiStatement;

    /// 化学物质列表
    @JacksonXmlProperty(localName = "ChemicalList")
    private ChemicalList chemicalList;

    /// MeSH 标引列表
    @JacksonXmlProperty(localName = "MeshHeadingList")
    private MeshHeadingList meshHeadingList;

    /// 补充 MeSH 概念列表
    @JacksonXmlProperty(localName = "SupplMeshList")
    private SupplMeshList supplMeshList;

    /// 评论、更正、撤稿信息列表
    @JacksonXmlProperty(localName = "CommentsCorrectionsList")
    private CommentsCorrectionsList commentsCorrectionsList;

    /// 基因符号列表
    @JacksonXmlProperty(localName = "GeneSymbolList")
    private GeneSymbolList geneSymbolList;

    /// 其他 ID 列表（如 PMC ID）
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "OtherID")
    private List<OtherId> otherIds;

    /// 其他语言的摘要列表
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "OtherAbstract")
    private List<OtherAbstract> otherAbstracts;

    /// 作为主题的人物列表
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "PersonalNameSubject")
    private List<PersonalNameSubject> personalNameSubjects;

    /// 研究者列表
    @JacksonXmlProperty(localName = "InvestigatorList")
    private InvestigatorList investigatorList;

    /// NLM 内部注释列表
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "GeneralNote")
    private List<GeneralNote> generalNotes;

    /// 航天任务列表
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "SpaceFlightMission")
    private List<String> spaceFlightMissions;

    private MedlineCitation() {}

    private String pmid() {
      return pmid != null ? pmid.value : null;
    }

    private Article article() {
      return article;
    }

    private MedlineJournalInfo journalInfo() {
      return journalInfo;
    }

    private List<String> keywords() {
      if (keywordLists == null || keywordLists.isEmpty()) {
        return List.of();
      }
      List<String> values = new ArrayList<>();
      for (KeywordList list : keywordLists) {
        values.addAll(list.values());
      }
      return List.copyOf(values);
    }

    private List<KeywordList> keywordLists() {
      return keywordLists != null ? List.copyOf(keywordLists) : List.of();
    }

    private DateInfo dateCreated() {
      return dateCreated;
    }

    private DateInfo dateCompleted() {
      return dateCompleted;
    }

    private DateInfo dateRevised() {
      return dateRevised;
    }

    private String owner() {
      return owner;
    }

    private String status() {
      return status;
    }

    private String indexingMethod() {
      return indexingMethod;
    }

    private String citationSubset() {
      return citationSubset;
    }

    private Integer numberOfReferences() {
      return numberOfReferences;
    }

    private String coiStatement() {
      return coiStatement;
    }

    private List<Chemical> chemicals() {
      return chemicalList != null ? chemicalList.chemicals() : List.of();
    }

    private List<MeshHeading> meshHeadings() {
      return meshHeadingList != null ? meshHeadingList.meshHeadings() : List.of();
    }

    private List<SupplMeshName> supplMeshNames() {
      return supplMeshList != null ? supplMeshList.supplMeshNames() : List.of();
    }

    private List<CommentsCorrections> commentsCorrections() {
      return commentsCorrectionsList != null
          ? commentsCorrectionsList.commentsCorrections()
          : List.of();
    }

    private List<String> geneSymbols() {
      return geneSymbolList != null ? geneSymbolList.geneSymbols() : List.of();
    }

    private List<OtherId> otherIds() {
      return otherIds != null ? List.copyOf(otherIds) : List.of();
    }

    private List<OtherAbstract> otherAbstracts() {
      return otherAbstracts != null ? List.copyOf(otherAbstracts) : List.of();
    }

    private List<PersonalNameSubject> personalNameSubjects() {
      return personalNameSubjects != null ? List.copyOf(personalNameSubjects) : List.of();
    }

    private List<Investigator> investigators() {
      return investigatorList != null ? investigatorList.investigators() : List.of();
    }

    private List<GeneralNote> generalNotes() {
      return generalNotes != null ? List.copyOf(generalNotes) : List.of();
    }

    private List<String> spaceFlightMissions() {
      return spaceFlightMissions != null ? List.copyOf(spaceFlightMissions) : List.of();
    }
  }

  /// 关键字列表。
  ///
  /// 包含关键字来源和关键字集合。
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class KeywordList {

    /// 关键字来源（如 NOTNLM, NLM）。
    @JacksonXmlProperty(isAttribute = true, localName = "Owner")
    private String owner;

    /// 关键字集合
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Keyword")
    private List<Keyword> keywords;

    private KeywordList() {}

    /// 返回关键字来源。
    public String owner() {
      return owner;
    }

    /// 返回关键字列表。
    public List<Keyword> keywords() {
      return keywords != null ? List.copyOf(keywords) : List.of();
    }

    private List<String> values() {
      if (keywords == null || keywords.isEmpty()) {
        return List.of();
      }
      List<String> values = new ArrayList<>(keywords.size());
      for (Keyword keyword : keywords) {
        if (keyword.value != null && !keyword.value.isBlank()) {
          values.add(keyword.value);
        }
      }
      return values;
    }
  }

  /// 单个关键字。
  ///
  /// 包含关键字文本和主要主题标志。
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Keyword {

    /// 关键字文本内容
    @JacksonXmlText private String value;

    /// 是否为主要主题 (Y/N)
    @JacksonXmlProperty(isAttribute = true, localName = "MajorTopicYN")
    private String majorTopicYN;

    private Keyword() {}

    /// 返回关键字文本。
    public String value() {
      return value;
    }

    /// 返回是否为主要主题 (Y/N)。
    public String majorTopicYN() {
      return majorTopicYN;
    }
  }

  /// PMID标识符的内部表示
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class Pmid {

    /// PMID数值
    @JacksonXmlText private String value;

    /// PMID版本号
    @JacksonXmlProperty(isAttribute = true, localName = "Version")
    private String version;

    private Pmid() {}

    /// 返回PMID值
    String value() {
      return value;
    }

    /// 返回PMID版本
    String version() {
      return version;
    }
  }

  /// 日期信息的内部表示
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class DateInfo {

    @JacksonXmlProperty(localName = "Year")
    private String year;

    @JacksonXmlProperty(localName = "Month")
    private String month;

    @JacksonXmlProperty(localName = "Day")
    private String day;

    private DateInfo() {}

    /// 返回年份
    public String year() {
      return year;
    }

    /// 返回月份
    public String month() {
      return month;
    }

    /// 返回日期
    public String day() {
      return day;
    }
  }

  /// 化学物质列表的内部表示
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class ChemicalList {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Chemical")
    private List<Chemical> chemicals;

    private ChemicalList() {}

    private List<Chemical> chemicals() {
      return chemicals != null ? List.copyOf(chemicals) : List.of();
    }
  }

  /// 化学物质的内部表示
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Chemical {

    @JacksonXmlProperty(localName = "RegistryNumber")
    private String registryNumber;

    @JacksonXmlProperty(localName = "NameOfSubstance")
    private NameOfSubstance nameOfSubstance;

    private Chemical() {}

    /// 返回CAS注册号
    public String registryNumber() {
      return registryNumber;
    }

    /// 返回物质名称信息
    public NameOfSubstance nameOfSubstance() {
      return nameOfSubstance;
    }
  }

  /// 物质名称的内部表示
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class NameOfSubstance {

    @JacksonXmlText private String value;

    @JacksonXmlProperty(isAttribute = true, localName = "UI")
    private String ui;

    private NameOfSubstance() {}

    /// 返回物质名称
    public String value() {
      return value;
    }

    /// 返回MeSH UI标识
    public String ui() {
      return ui;
    }
  }

  /// MeSH 标引列表容器。
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class MeshHeadingList {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "MeshHeading")
    private List<MeshHeading> meshHeadings;

    private MeshHeadingList() {}

    List<MeshHeading> meshHeadings() {
      return meshHeadings != null ? List.copyOf(meshHeadings) : List.of();
    }
  }

  /// MeSH 标引项。
  ///
  /// 包含主题词(DescriptorName)和可选的限定词列表(QualifierName)。
  ///
  /// 例如: {@code "Humans" [DescriptorName]} + {@code "genetics" [QualifierName]} 表示"人类遗传学"主题。
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class MeshHeading {

    @JacksonXmlProperty(localName = "DescriptorName")
    private DescriptorName descriptorName;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "QualifierName")
    private List<QualifierName> qualifierNames;

    private MeshHeading() {}

    /// 返回 MeSH 主题词。
    public DescriptorName descriptorName() {
      return descriptorName;
    }

    /// 返回 MeSH 限定词列表。
    public List<QualifierName> qualifierNames() {
      return qualifierNames != null ? List.copyOf(qualifierNames) : List.of();
    }
  }

  /// MeSH 主题词。
  ///
  /// 主题词是 MeSH 术语的主要部分,描述文章的核心主题。
  ///
  /// 包含:
  ///
  /// - UI: MeSH 主题词的唯一标识符
  ///   - MajorTopicYN: 是否为文章的主要主题(Y=是, N=否)
  ///   - Type: 主题词的类别类型(如 Geographic 表示地理名称)
  ///   - value: 主题词的文本值(如 "Humans", "Antibodies")
  ///
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class DescriptorName {

    @JacksonXmlText private String value;

    @JacksonXmlProperty(isAttribute = true, localName = "UI")
    private String ui;

    @JacksonXmlProperty(isAttribute = true, localName = "MajorTopicYN")
    private String majorTopicYN;

    @JacksonXmlProperty(isAttribute = true, localName = "Type")
    private String type;

    private DescriptorName() {}

    /// 返回主题词的文本值。
    public String value() {
      return value;
    }

    /// 返回主题词的唯一标识符。
    public String ui() {
      return ui;
    }

    /// 返回是否为文章的主要主题。
    ///
    /// @return "Y" 表示是,"N" 表示否
    public String majorTopicYN() {
      return majorTopicYN;
    }

    /// 返回主题词的类别类型。
    ///
    /// @return 如 "Geographic" 表示地理名称
    public String type() {
      return type;
    }
  }

  /// MeSH 副主题词（限定词）。
  ///
  /// 限定词用于进一步细化主题词的含义,例如:
  ///
  /// - {@code "Humans" [主题词]} + {@code "genetics" [限定词]} = "人类遗传学"
  ///   - {@code "Diabetes Mellitus" [主题词]} + {@code "drug therapy" [限定词]} = "糖尿病药物治疗"
  ///
  /// 包含:
  ///
  /// - UI: MeSH 限定词的唯一标识符
  ///   - MajorTopicYN: 是否为文章的主要主题(Y=是, N=否)
  ///   - value: 限定词的文本值(如 "genetics", "drug therapy")
  ///
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class QualifierName {

    @JacksonXmlText private String value;

    @JacksonXmlProperty(isAttribute = true, localName = "UI")
    private String ui;

    @JacksonXmlProperty(isAttribute = true, localName = "MajorTopicYN")
    private String majorTopicYN;

    private QualifierName() {}

    /// 返回限定词的文本值。
    public String value() {
      return value;
    }

    /// 返回限定词的唯一标识符。
    public String ui() {
      return ui;
    }

    /// 返回是否为文章的主要主题。
    ///
    /// @return "Y" 表示是,"N" 表示否
    public String majorTopicYN() {
      return majorTopicYN;
    }
  }

  /// 补充 MeSH 概念列表容器
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class SupplMeshList {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "SupplMeshName")
    private List<SupplMeshName> supplMeshNames;

    private SupplMeshList() {}

    List<SupplMeshName> supplMeshNames() {
      return supplMeshNames != null ? List.copyOf(supplMeshNames) : List.of();
    }
  }

  /// 补充 MeSH 概念。
  ///
  /// 补充 MeSH 概念用于描述疾病、药物试验等特定主题。
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class SupplMeshName {

    @JacksonXmlText private String value;

    @JacksonXmlProperty(isAttribute = true, localName = "Type")
    private String type;

    @JacksonXmlProperty(isAttribute = true, localName = "UI")
    private String ui;

    private SupplMeshName() {}

    /// 返回补充 MeSH 名称
    public String value() {
      return value;
    }

    /// 返回补充 MeSH 类型（如 Protocol, Disease）
    public String type() {
      return type;
    }

    /// 返回唯一标识符
    public String ui() {
      return ui;
    }
  }

  /// 评论更正列表容器
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class CommentsCorrectionsList {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "CommentsCorrections")
    private List<CommentsCorrections> commentsCorrections;

    private CommentsCorrectionsList() {}

    List<CommentsCorrections> commentsCorrections() {
      return commentsCorrections != null ? List.copyOf(commentsCorrections) : List.of();
    }
  }

  /// 评论、更正、撤稿信息。
  ///
  /// 包含与本文献相关的评论、更正、撤稿、勘误等信息。
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class CommentsCorrections {

    @JacksonXmlProperty(isAttribute = true, localName = "RefType")
    private String refType;

    @JacksonXmlProperty(localName = "RefSource")
    private String refSource;

    @JacksonXmlProperty(localName = "PMID")
    private PmidRef pmid;

    @JacksonXmlProperty(localName = "Note")
    private String note;

    private CommentsCorrections() {}

    /// 返回关系类型（如 RetractionOf, ErratumIn, CommentOn）
    public String refType() {
      return refType;
    }

    /// 返回原始来源
    public String refSource() {
      return refSource;
    }

    /// 返回关联的 PMID
    public String pmid() {
      return pmid != null ? pmid.value() : null;
    }

    /// 返回备注
    public String note() {
      return note;
    }

    /// PMID 引用的内部表示
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class PmidRef {

      @JacksonXmlText private String value;

      @JacksonXmlProperty(isAttribute = true, localName = "Version")
      private String version;

      private PmidRef() {}

      String value() {
        return value;
      }

      String version() {
        return version;
      }
    }
  }

  /// 基因符号列表容器
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class GeneSymbolList {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "GeneSymbol")
    private List<String> geneSymbols;

    private GeneSymbolList() {}

    List<String> geneSymbols() {
      return geneSymbols != null ? List.copyOf(geneSymbols) : List.of();
    }
  }

  /// 其他 ID（如 PMC ID）。
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class OtherId {

    @JacksonXmlText private String value;

    @JacksonXmlProperty(isAttribute = true, localName = "Source")
    private String source;

    private OtherId() {}

    /// 返回 ID 值
    public String value() {
      return value;
    }

    /// 返回 ID 来源（如 PMC, NLM）
    public String source() {
      return source;
    }
  }

  /// 其他语言的摘要。
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class OtherAbstract {

    @JacksonXmlProperty(isAttribute = true, localName = "Type")
    private String type;

    @JacksonXmlProperty(isAttribute = true, localName = "Language")
    private String language;

    @JacksonXmlProperty(localName = "AbstractText")
    private String abstractText;

    @JacksonXmlProperty(localName = "CopyrightInformation")
    private String copyrightInformation;

    private OtherAbstract() {}

    /// 返回摘要类型
    public String type() {
      return type;
    }

    /// 返回摘要语言
    public String language() {
      return language;
    }

    /// 返回摘要文本
    public String abstractText() {
      return abstractText;
    }

    /// 返回版权信息
    public String copyrightInformation() {
      return copyrightInformation;
    }
  }

  /// 作为主题的人物。
  ///
  /// 以人物为主题的文献中涉及的人物信息。
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class PersonalNameSubject {

    @JacksonXmlProperty(localName = "LastName")
    private String lastName;

    @JacksonXmlProperty(localName = "ForeName")
    private String foreName;

    @JacksonXmlProperty(localName = "Initials")
    private String initials;

    @JacksonXmlProperty(localName = "Suffix")
    private String suffix;

    private PersonalNameSubject() {}

    /// 返回姓
    public String lastName() {
      return lastName;
    }

    /// 返回名
    public String foreName() {
      return foreName;
    }

    /// 返回姓名缩写
    public String initials() {
      return initials;
    }

    /// 返回后缀
    public String suffix() {
      return suffix;
    }
  }

  /// 研究者列表容器
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class InvestigatorList {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Investigator")
    private List<Investigator> investigators;

    private InvestigatorList() {}

    List<Investigator> investigators() {
      return investigators != null ? List.copyOf(investigators) : List.of();
    }
  }

  /// 研究者（非作者）。
  ///
  /// 参与研究但非文章作者的研究者信息。
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Investigator {

    @JacksonXmlProperty(isAttribute = true, localName = "ValidYN")
    private String validYN;

    @JacksonXmlProperty(localName = "LastName")
    private String lastName;

    @JacksonXmlProperty(localName = "ForeName")
    private String foreName;

    @JacksonXmlProperty(localName = "Initials")
    private String initials;

    @JacksonXmlProperty(localName = "Suffix")
    private String suffix;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Identifier")
    private List<Author.Identifier> identifiers;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "AffiliationInfo")
    private List<Author.AffiliationInfo> affiliationInfo;

    private Investigator() {}

    /// 返回是否有效（Y/N）
    public String validYN() {
      return validYN;
    }

    /// 返回姓
    public String lastName() {
      return lastName;
    }

    /// 返回名
    public String foreName() {
      return foreName;
    }

    /// 返回姓名缩写
    public String initials() {
      return initials;
    }

    /// 返回后缀
    public String suffix() {
      return suffix;
    }

    /// 返回标识符列表（如 ORCID）
    public List<Author.Identifier> identifiers() {
      return identifiers != null ? List.copyOf(identifiers) : List.of();
    }

    /// 返回单位信息列表
    public List<Author.AffiliationInfo> affiliationInfo() {
      return affiliationInfo != null ? List.copyOf(affiliationInfo) : List.of();
    }
  }

  /// NLM 内部注释。
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class GeneralNote {

    @JacksonXmlText private String value;

    @JacksonXmlProperty(isAttribute = true, localName = "Owner")
    private String owner;

    @JacksonXmlProperty(isAttribute = true, localName = "NoteType")
    private String noteType;

    private GeneralNote() {}

    /// 返回注释内容
    public String value() {
      return value;
    }

    /// 返回注释所有者
    public String owner() {
      return owner;
    }

    /// 返回注释类型
    public String noteType() {
      return noteType;
    }
  }

  /// 从 Medline 引文中提取的简化 PubMed 文章元数据。
  ///
  /// 包含文章的核心信息,包括标题、摘要、作者列表、期刊信息、发表类型等。 这是 PubMed 文章数据的主要数据传输对象。
  ///
  /// **主要字段:**
  ///
  /// - **pubModel**: 文章出版形式 (Print, Electronic 等)
  ///   - **journal**: 期刊元数据 (标题、ISSN、发表日期等)
  ///   - **title**: 文章标题
  ///   - **pagination**: 页码信息
  ///   - **eLocationIds**: 电子定位标识列表 (如 DOI)
  ///   - **abstractContent**: 摘要内容 (可能包含多个分段)
  ///   - **authors**: 作者列表
  ///   - **languages**: 语言列表
  ///   - **dataBankList**: 关联的数据库列表 (如 GENBANK)
  ///   - **grantList**: 资助信息列表
  ///   - **publicationTypes**: 发表类型标识符
  ///   - **vernacularTitle**: 非英语文章的原文标题
  ///   - **articleDates**: 文章日期列表 (如电子版发布日期)
  ///
  /// @author linqibin
  /// @since 0.1.0
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Article {

    @JacksonXmlProperty(isAttribute = true, localName = "PubModel")
    private String pubModel;

    @JacksonXmlProperty(localName = "Journal")
    private Journal journal;

    @JacksonXmlProperty(localName = "ArticleTitle")
    private String title;

    @JacksonXmlProperty(localName = "Pagination")
    private Pagination pagination;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "ELocationID")
    private List<ELocationId> eLocationIds;

    @JacksonXmlProperty(localName = "Abstract")
    private AbstractContent abstractContent;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Language")
    private List<String> languages;

    @JacksonXmlProperty(localName = "AuthorList")
    private AuthorListWrapper authorList;

    @JacksonXmlProperty(localName = "DataBankList")
    private DataBankList dataBankList;

    @JacksonXmlProperty(localName = "GrantList")
    private GrantList grantList;

    @JacksonXmlElementWrapper(localName = "PublicationTypeList")
    @JacksonXmlProperty(localName = "PublicationType")
    private List<PublicationType> publicationTypes;

    @JacksonXmlProperty(localName = "VernacularTitle")
    private String vernacularTitle;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "ArticleDate")
    private List<ArticleDate> articleDates;

    private Article() {}

    /// 文章的出版形式 (Print, Electronic, Print-Electronic 等)。
    public String pubModel() {
      return pubModel;
    }

    /// 与文章关联的期刊元数据。
    public Journal journal() {
      return journal;
    }

    /// 文章标题。
    public String title() {
      return title;
    }

    /// 页码信息。
    public Pagination pagination() {
      return pagination;
    }

    /// 电子定位标识列表 (如 DOI)。
    public List<ELocationId> eLocationIds() {
      return eLocationIds != null ? List.copyOf(eLocationIds) : List.of();
    }

    /// PubMed 报告的主要语言。
    public String language() {
      if (languages == null || languages.isEmpty()) {
        return null;
      }
      return languages.get(0);
    }

    /// 摘要分段 (标签 + 文本)。
    public List<AbstractSection> abstractSections() {
      return abstractContent != null ? abstractContent.sections() : List.of();
    }

    /// 摘要的版权信息。
    public String copyrightInformation() {
      return abstractContent != null ? abstractContent.copyrightInformation() : null;
    }

    /// 已解析的作者列表。
    public List<Author> authors() {
      return authorList != null ? authorList.authors() : List.of();
    }

    /// 作者列表是否完整 (Y = 完整, N = 不完整)。
    public String authorsCompleteYN() {
      return authorList != null ? authorList.completeYN() : null;
    }

    /// 关联的数据库列表 (如 GENBANK)。
    public List<DataBank> dataBanks() {
      return dataBankList != null ? dataBankList.dataBanks() : List.of();
    }

    /// 数据库列表是否完整 (Y = 完整, N = 不完整)。
    public String dataBanksCompleteYN() {
      return dataBankList != null ? dataBankList.completeYN() : null;
    }

    /// 资助信息列表。
    public List<Grant> grants() {
      return grantList != null ? grantList.grants() : List.of();
    }

    /// PubMed 分配的发表类型标识符。
    public List<PublicationType> publicationTypes() {
      if (publicationTypes == null || publicationTypes.isEmpty()) {
        return List.of();
      }
      return List.copyOf(publicationTypes);
    }

    /// 非英语文章的原文标题。
    public String vernacularTitle() {
      return vernacularTitle;
    }

    /// 文章日期列表 (如电子版发布日期)。
    public List<ArticleDate> articleDates() {
      return articleDates != null ? List.copyOf(articleDates) : List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class AbstractContent {

      @JacksonXmlElementWrapper(useWrapping = false)
      @JacksonXmlProperty(localName = "AbstractText")
      private List<AbstractText> sections;

      @JacksonXmlProperty(localName = "CopyrightInformation")
      private String copyrightInformation;

      private AbstractContent() {}

      private List<AbstractSection> sections() {
        if (sections == null || sections.isEmpty()) {
          return List.of();
        }
        List<AbstractSection> result = new ArrayList<>(sections.size());
        for (AbstractText section : sections) {
          String text = section.value;
          if (text != null && !text.isBlank()) {
            result.add(new AbstractSection(section.label, section.nlmCategory, text));
          }
        }
        return List.copyOf(result);
      }

      private String copyrightInformation() {
        return copyrightInformation;
      }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class AbstractText {

      @JacksonXmlText private String value;

      @JacksonXmlProperty(isAttribute = true, localName = "Label")
      private String label;

      @JacksonXmlProperty(isAttribute = true, localName = "NlmCategory")
      private String nlmCategory;

      private AbstractText() {}

      String value() {
        return value;
      }

      String label() {
        return label;
      }

      String nlmCategory() {
        return nlmCategory;
      }
    }

    /// 从引文中提取的摘要分段,带可选标签和类别。
    ///
    /// @param label 段落标签（如 BACKGROUND, METHODS, RESULTS）
    /// @param nlmCategory NLM 类别（如 BACKGROUND, METHODS, RESULTS, CONCLUSIONS）
    /// @param text 段落文本
    public record AbstractSection(String label, String nlmCategory, String text) {}

    /// 页码信息。
    ///
    /// 包含文章的起始页码、结束页码和 MEDLINE 格式页码。
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Pagination {

      @JacksonXmlProperty(localName = "StartPage")
      private String startPage;

      @JacksonXmlProperty(localName = "EndPage")
      private String endPage;

      @JacksonXmlProperty(localName = "MedlinePgn")
      private String medlinePgn;

      private Pagination() {}

      /// 起始页码。
      public String startPage() {
        return startPage;
      }

      /// 结束页码。
      public String endPage() {
        return endPage;
      }

      /// MEDLINE 格式页码。
      public String medlinePgn() {
        return medlinePgn;
      }
    }

    /// 电子定位标识 (如 DOI)。
    ///
    /// 包含电子出版物的唯一标识符及其类型。
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ELocationId {

      @JacksonXmlText private String value;

      @JacksonXmlProperty(isAttribute = true, localName = "EIdType")
      private String eidType;

      @JacksonXmlProperty(isAttribute = true, localName = "ValidYN")
      private String validYN;

      private ELocationId() {}

      /// 电子定位标识的实际值 (如 DOI 字符串)。
      public String value() {
        return value;
      }

      /// 标识类型 (如 doi, pii)。
      public String eidType() {
        return eidType;
      }

      /// 标识是否有效 (Y = 有效, N = 无效)。
      public String validYN() {
        return validYN;
      }
    }

    /// 数据库列表容器。
    ///
    /// 包含文章关联的数据库信息,如 GENBANK。
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class DataBankList {

      @JacksonXmlProperty(isAttribute = true, localName = "CompleteYN")
      private String completeYN;

      @JacksonXmlElementWrapper(useWrapping = false)
      @JacksonXmlProperty(localName = "DataBank")
      private List<DataBank> dataBanks;

      private DataBankList() {}

      String completeYN() {
        return completeYN;
      }

      List<DataBank> dataBanks() {
        return dataBanks != null ? List.copyOf(dataBanks) : List.of();
      }
    }

    /// 数据库信息 (如 GENBANK)。
    ///
    /// 包含数据库名称和登记号列表。
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class DataBank {

      @JacksonXmlProperty(localName = "DataBankName")
      private String dataBankName;

      @JacksonXmlElementWrapper(localName = "AccessionNumberList")
      @JacksonXmlProperty(localName = "AccessionNumber")
      private List<String> accessionNumbers;

      private DataBank() {}

      /// 数据库名称 (如 GENBANK)。
      public String dataBankName() {
        return dataBankName;
      }

      /// 登记号列表。
      public List<String> accessionNumbers() {
        return accessionNumbers != null ? List.copyOf(accessionNumbers) : List.of();
      }
    }

    /// 资助信息列表容器。
    ///
    /// 包含文章的研究资助信息。
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class GrantList {

      @JacksonXmlProperty(isAttribute = true, localName = "CompleteYN")
      private String completeYN;

      @JacksonXmlElementWrapper(useWrapping = false)
      @JacksonXmlProperty(localName = "Grant")
      private List<Grant> grants;

      private GrantList() {}

      String completeYN() {
        return completeYN;
      }

      List<Grant> grants() {
        return grants != null ? List.copyOf(grants) : List.of();
      }
    }

    /// 作者列表容器。
    ///
    /// 包含作者列表及其完整性标志。
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class AuthorListWrapper {

      @JacksonXmlProperty(isAttribute = true, localName = "CompleteYN")
      private String completeYN;

      @JacksonXmlElementWrapper(useWrapping = false)
      @JacksonXmlProperty(localName = "Author")
      private List<Author> authors;

      private AuthorListWrapper() {}

      String completeYN() {
        return completeYN;
      }

      List<Author> authors() {
        return authors != null ? List.copyOf(authors) : List.of();
      }
    }

    /// 资助信息。
    ///
    /// 包含研究项目的资助编号、机构和国家等信息。
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Grant {

      @JacksonXmlProperty(localName = "GrantID")
      private String grantId;

      @JacksonXmlProperty(localName = "Acronym")
      private String acronym;

      @JacksonXmlProperty(localName = "Agency")
      private String agency;

      @JacksonXmlProperty(localName = "Country")
      private String country;

      private Grant() {}

      /// 资助编号。
      public String grantId() {
        return grantId;
      }

      /// 机构缩写。
      public String acronym() {
        return acronym;
      }

      /// 资助机构。
      public String agency() {
        return agency;
      }

      /// 国家。
      public String country() {
        return country;
      }
    }

    /// 文章日期信息。
    ///
    /// 包含文章的电子发布日期等信息。
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ArticleDate {

      @JacksonXmlProperty(isAttribute = true, localName = "DateType")
      private String dateType;

      @JacksonXmlProperty(localName = "Year")
      private String year;

      @JacksonXmlProperty(localName = "Month")
      private String month;

      @JacksonXmlProperty(localName = "Day")
      private String day;

      private ArticleDate() {}

      /// 日期类型 (如 Electronic, Collection)。
      public String dateType() {
        return dateType;
      }

      /// 年。
      public String year() {
        return year;
      }

      /// 月。
      public String month() {
        return month;
      }

      /// 日。
      public String day() {
        return day;
      }
    }

    /// 发表类型。
    ///
    /// 包含发表类型的文本值和 MeSH UI 标识符。
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class PublicationType {

      /// 发表类型文本
      @JacksonXmlText private String value;

      /// MeSH UI 标识符
      @JacksonXmlProperty(isAttribute = true, localName = "UI")
      private String ui;

      private PublicationType() {}

      /// 返回发表类型文本
      public String value() {
        return value;
      }

      /// 返回 MeSH UI 标识符
      public String ui() {
        return ui;
      }
    }
  }

  /// 从 PubMed 引文中提取的作者信息。
  ///
  /// 包含作者的姓名、缩写、机构隶属关系和可选的标识符 (如 ORCID)。 支持个人作者和团体作者两种类型。
  ///
  /// **主要字段:**
  ///
  /// - **lastName**: 姓氏(个人作者)
  ///   - **foreName**: 名字(个人作者)
  ///   - **initials**: 姓名缩写(个人作者)
  ///   - **suffix**: 后缀(如 Jr, III)
  ///   - **collectiveName**: 团体作者名称
  ///   - **affiliationInfo**: 机构隶属关系列表
  ///   - **identifiers**: 作者标识符列表(如 ORCID)
  ///   - **validYN**: 作者信息是否有效(Y/N)
  ///   - **equalContrib**: 是否为同等贡献作者(Y/N)
  ///
  /// @author linqibin
  /// @since 0.1.0
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Author {

    @JacksonXmlProperty(localName = "LastName")
    private String lastName;

    @JacksonXmlProperty(localName = "ForeName")
    private String foreName;

    @JacksonXmlProperty(localName = "Initials")
    private String initials;

    @JacksonXmlProperty(localName = "Suffix")
    private String suffix;

    @JacksonXmlProperty(localName = "CollectiveName")
    private String collectiveName;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "AffiliationInfo")
    private List<AffiliationInfo> affiliationInfo;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Identifier")
    private List<Identifier> identifiers;

    @JacksonXmlProperty(isAttribute = true, localName = "ValidYN")
    private String validYN;

    @JacksonXmlProperty(isAttribute = true, localName = "EqualContrib")
    private String equalContrib;

    private Author() {}

    public String lastName() {
      return lastName;
    }

    public String foreName() {
      return foreName;
    }

    public String initials() {
      return initials;
    }

    public String suffix() {
      return suffix;
    }

    public String collectiveName() {
      return collectiveName;
    }

    public String validYN() {
      return validYN;
    }

    public String equalContrib() {
      return equalContrib;
    }

    public List<String> affiliations() {
      if (affiliationInfo == null || affiliationInfo.isEmpty()) {
        return List.of();
      }
      List<String> affiliations = new ArrayList<>(affiliationInfo.size());
      for (AffiliationInfo info : affiliationInfo) {
        String value = info.value();
        if (value != null && !value.isBlank()) {
          affiliations.add(value);
        }
      }
      return List.copyOf(affiliations);
    }

    public List<Identifier> identifiers() {
      return identifiers != null ? List.copyOf(identifiers) : List.of();
    }

    /// 返回完整的机构信息列表（包含标识符）。
    ///
    /// 相比 `affiliations()` 方法（只返回文本），此方法保留 ROR/Ringgold 等标识符信息。
    ///
    /// @return 机构信息列表
    public List<AffiliationInfo> affiliationInfo() {
      return affiliationInfo != null ? List.copyOf(affiliationInfo) : List.of();
    }

    /// 作者的机构隶属关系信息。
    ///
    /// 包含机构文本和可选的机构标识符(如 ROR)。
    ///
    /// @author linqibin
    /// @since 0.1.0
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class AffiliationInfo {

      @JacksonXmlProperty(localName = "Affiliation")
      private String affiliation;

      @JacksonXmlText private String value;

      @JacksonXmlElementWrapper(useWrapping = false)
      @JacksonXmlProperty(localName = "Identifier")
      private List<Identifier> identifiers;

      private AffiliationInfo() {}

      public String value() {
        if (affiliation != null && !affiliation.isBlank()) {
          return affiliation;
        }
        return value;
      }

      public List<Identifier> identifiers() {
        return identifiers != null ? List.copyOf(identifiers) : List.of();
      }
    }

    /// 作者或机构的唯一标识符。
    ///
    /// 常见来源包括 ORCID、ResearcherID、ROR 等。
    ///
    /// @author linqibin
    /// @since 0.1.0
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Identifier {

      @JacksonXmlText private String value;

      @JacksonXmlProperty(isAttribute = true, localName = "Source")
      private String source;

      private Identifier() {}

      public String value() {
        return value;
      }

      public String source() {
        return source;
      }
    }
  }

  /// 从 PubMed 引文中衍生的期刊元数据。
  ///
  /// 包含期刊的标识信息 (ISSN)、标题、ISO 缩写以及期刊期号和发表日期信息。
  ///
  /// **主要字段:**
  ///
  /// - **issn**: 期刊 ISSN (国际标准连续出版物编号)
  ///   - **title**: 完整期刊标题
  ///   - **isoAbbreviation**: ISO 标准期刊缩写
  ///   - **journalIssue**: 期号信息,包含发表日期
  ///
  /// @author linqibin
  /// @since 0.1.0
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Journal {

    @JacksonXmlProperty(localName = "ISSN")
    private Issn issn;

    @JacksonXmlProperty(localName = "Title")
    private String title;

    @JacksonXmlProperty(localName = "ISOAbbreviation")
    private String isoAbbreviation;

    @JacksonXmlProperty(localName = "JournalIssue")
    private JournalIssue journalIssue;

    public Journal() {}

    /// Journal ISSN value.
    public String issn() {
      return issn != null ? issn.value : null;
    }

    /// Journal ISSN type attribute.
    public String issnType() {
      return issn != null ? issn.type : null;
    }

    /// Full journal title.
    public String title() {
      return title;
    }

    /// ISO abbreviation for the journal.
    public String isoAbbreviation() {
      return isoAbbreviation;
    }

    /// Issue information containing publication date.
    public JournalIssue journalIssue() {
      return journalIssue;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class JournalIssue {

      @JacksonXmlProperty(isAttribute = true, localName = "CitedMedium")
      private String citedMedium;

      @JacksonXmlProperty(localName = "Volume")
      private String volume;

      @JacksonXmlProperty(localName = "Issue")
      private String issue;

      @JacksonXmlProperty(localName = "PubDate")
      private PubDate pubDate;

      private JournalIssue() {}

      /// 引用媒介类型(Print 或 Internet).
      public String citedMedium() {
        return citedMedium;
      }

      /// 卷号.
      public String volume() {
        return volume;
      }

      /// 期号.
      public String issue() {
        return issue;
      }

      /// 出版日期元数据.
      public PubDate pubDate() {
        return pubDate;
      }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class PubDate {

      @JacksonXmlProperty(localName = "Year")
      private String year;

      @JacksonXmlProperty(localName = "Month")
      private String month;

      @JacksonXmlProperty(localName = "Day")
      private String day;

      @JacksonXmlProperty(localName = "Season")
      private String season;

      @JacksonXmlProperty(localName = "MedlineDate")
      private String medlineDate;

      @JacksonXmlProperty(localName = "Hour")
      private String hour;

      @JacksonXmlProperty(localName = "Minute")
      private String minute;

      @JacksonXmlProperty(localName = "Second")
      private String second;

      private PubDate() {}

      public String year() {
        return year;
      }

      public String month() {
        return month;
      }

      public String day() {
        return day;
      }

      public String season() {
        return season;
      }

      public String medlineDate() {
        return medlineDate;
      }

      public String hour() {
        return hour;
      }

      public String minute() {
        return minute;
      }

      public String second() {
        return second;
      }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class Issn {

      @JacksonXmlText private String value;

      @JacksonXmlProperty(isAttribute = true, localName = "IssnType")
      private String type;

      private Issn() {}
    }
  }

  /// Medline 提供的额外期刊元数据。
  ///
  /// 包含期刊的标准化缩写、出版国家、NLM唯一标识符和链接ISSN等信息。
  ///
  /// @author linqibin
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class MedlineJournalInfo {

    /// Medline标准化期刊缩写名称
    @JacksonXmlProperty(localName = "MedlineTA")
    private String medlineTa;

    /// 期刊出版国家/地区
    @JacksonXmlProperty(localName = "Country")
    private String country;

    /// NLM(美国国家医学图书馆)唯一标识符
    @JacksonXmlProperty(localName = "NlmUniqueID")
    private String nlmUniqueId;

    /// 链接ISSN(国际标准期刊号)
    @JacksonXmlProperty(localName = "ISSNLinking")
    private String issnLinking;

    public MedlineJournalInfo() {}

    /// 返回Medline标准化期刊缩写名称
    public String medlineTa() {
      return medlineTa;
    }

    /// 返回期刊出版国家/地区
    public String country() {
      return country;
    }

    /// 返回NLM唯一标识符
    public String nlmUniqueId() {
      return nlmUniqueId;
    }

    /// 返回链接ISSN值
    public String issnLinking() {
      return issnLinking;
    }
  }

  /// 从XML直接解析的PubMed补充数据块。
  ///
  /// 包含文章的发布状态、历史事件时间线和各类文章标识符(DOI、PMC等)。 提供便捷的方法用于访问和检查这些补充信息。
  ///
  /// @author linqibin
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class PubmedData {

    /// 文章发布状态(如epublish、ppublish等)
    @JacksonXmlProperty(localName = "PublicationStatus")
    private String publicationStatus;

    /// 文章发布历史时间线
    @JacksonXmlProperty(localName = "History")
    private History history;

    /// 文章标识符列表
    @JacksonXmlProperty(localName = "ArticleIdList")
    private ArticleIdList articleIdList;

    /// 参考文献列表
    @JacksonXmlProperty(localName = "ReferenceList")
    private ReferenceList referenceList;

    /// 补充对象列表
    @JacksonXmlProperty(localName = "ObjectList")
    private ObjectList objectList;

    /// PMC 引用计数
    @JacksonXmlProperty(localName = "PmcRefCount")
    private Integer pmcRefCount;

    public PubmedData() {}

    /// 返回PubMed报告的发布状态
    public String publicationStatus() {
      return publicationStatus;
    }

    /// 返回描述发布时间线的不可变历史事件列表
    public List<HistoryEvent> history() {
      return history != null ? history.events() : List.of();
    }

    /// 返回文章标识符的不可变列表(例如DOI、PMC)
    public List<ArticleId> articleIds() {
      return articleIdList != null ? articleIdList.articleIds() : List.of();
    }

    /// 返回参考文献的不可变列表
    public List<Reference> references() {
      return referenceList != null ? referenceList.references() : List.of();
    }

    /// 返回补充对象的不可变列表
    public List<ObjectInfo> objects() {
      return objectList != null ? objectList.objects() : List.of();
    }

    /// 返回在 PMC 中被引用的次数
    public Integer pmcRefCount() {
      return pmcRefCount;
    }

    /// 检查是否包含文章标识符
    @JsonIgnore
    public boolean hasArticleIds() {
      return !articleIds().isEmpty();
    }

    /// 历史事件集合的内部表示
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class History {

      /// 发布日期事件列表
      @JacksonXmlElementWrapper(useWrapping = false)
      @JacksonXmlProperty(localName = "PubMedPubDate")
      private List<PubDate> events;

      private History() {}

      private List<HistoryEvent> events() {
        if (events == null || events.isEmpty()) {
          return List.of();
        }
        List<HistoryEvent> mapped = new ArrayList<>(events.size());
        for (PubDate event : events) {
          mapped.add(event.toHistoryEvent());
        }
        return List.copyOf(mapped);
      }
    }

    /// 发布日期的内部表示
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class PubDate {

      /// 发布状态(如received、accepted、epublish等)
      @JacksonXmlProperty(isAttribute = true, localName = "PubStatus")
      private String status;

      /// 年份
      @JacksonXmlProperty(localName = "Year")
      private String year;

      /// 月份
      @JacksonXmlProperty(localName = "Month")
      private String month;

      /// 日期
      @JacksonXmlProperty(localName = "Day")
      private String day;

      /// 小时
      @JacksonXmlProperty(localName = "Hour")
      private String hour;

      /// 分钟
      @JacksonXmlProperty(localName = "Minute")
      private String minute;

      private PubDate() {}

      private HistoryEvent toHistoryEvent() {
        return new HistoryEvent(status, year, month, day, hour, minute);
      }
    }

    /// 文章标识符列表的内部表示
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ArticleIdList {

      /// 文章标识符集合
      @JacksonXmlElementWrapper(useWrapping = false)
      @JacksonXmlProperty(localName = "ArticleId")
      private List<ArticleId> articleIds;

      private ArticleIdList() {}

      private List<ArticleId> articleIds() {
        if (articleIds == null || articleIds.isEmpty()) {
          return List.of();
        }
        return List.copyOf(articleIds);
      }
    }

    /// 文章标识符,表示各类唯一标识(DOI、PMC、PubMed等)。
    ///
    /// 每个标识符包含类型和对应的值。
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ArticleId {

      /// 标识符值
      @JacksonXmlText private String value;

      /// 标识符类型
      @JacksonXmlProperty(isAttribute = true, localName = "IdType")
      private String type;

      public ArticleId() {}

      /// 返回标识符值(例如DOI、PMC值)
      public String value() {
        return value;
      }

      /// 返回PubMed报告的标识符类型(例如doi、pmc、pubmed)
      public String type() {
        return type;
      }
    }

    /// 参考文献列表的内部表示
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ReferenceList {

      @JacksonXmlElementWrapper(useWrapping = false)
      @JacksonXmlProperty(localName = "Reference")
      private List<Reference> references;

      private ReferenceList() {}

      List<Reference> references() {
        return references != null ? List.copyOf(references) : List.of();
      }
    }

    /// 单条参考文献。
    ///
    /// 包含引文文本和可选的文章标识符列表。
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Reference {

      @JacksonXmlProperty(localName = "Citation")
      private String citation;

      @JacksonXmlProperty(localName = "ArticleIdList")
      private ArticleIdList articleIdList;

      private Reference() {}

      public String citation() {
        return citation;
      }

      public List<ArticleId> articleIds() {
        return articleIdList != null ? articleIdList.articleIds() : List.of();
      }
    }

    /// 补充对象列表的内部表示
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ObjectList {

      @JacksonXmlElementWrapper(useWrapping = false)
      @JacksonXmlProperty(localName = "Object")
      private List<ObjectInfo> objects;

      private ObjectList() {}

      List<ObjectInfo> objects() {
        return objects != null ? List.copyOf(objects) : List.of();
      }
    }

    /// 补充对象信息(如图表、关键词等)。
    ///
    /// 每个对象包含类型和参数列表。
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ObjectInfo {

      @JacksonXmlProperty(isAttribute = true, localName = "Type")
      private String type;

      @JacksonXmlElementWrapper(useWrapping = false)
      @JacksonXmlProperty(localName = "Param")
      private List<Param> params;

      private ObjectInfo() {}

      public String type() {
        return type;
      }

      public List<Param> params() {
        return params != null ? List.copyOf(params) : List.of();
      }
    }

    /// 对象参数。
    ///
    /// 表示补充对象的键值对属性。
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Param {

      @JacksonXmlProperty(isAttribute = true, localName = "Name")
      private String name;

      @JacksonXmlText private String value;

      private Param() {}

      public String name() {
        return name;
      }

      public String value() {
        return value;
      }
    }

    /// 历史事件记录,描述关键的发布里程碑。
    ///
    /// @param status 发布状态(如received、accepted、epublish等)
    /// @param year 年份
    /// @param month 月份
    /// @param day 日期
    /// @param hour 小时
    /// @param minute 分钟
    public record HistoryEvent(
        String status, String year, String month, String day, String hour, String minute) {}
  }
}

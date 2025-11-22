package com.patra.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/// Patra 平台规范化医学出版物模型（基于 PubMed/MEDLINE 标准设计）。
///
/// 此不可变值对象作为采集、目录和溯源微服务间的共享内核模型，封装了医学出版物的核心元数据。
///
/// 设计基于以下国际标准：
///
/// - **PubMed/MEDLINE** - 医学出版物元数据标准（主要数据源）
///   - **MeSH** - 美国国家医学图书馆医学主题词表
///   - **Dublin Core** - 核心元数据标准（title, creator, identifier等）
///   - **Schema.org** - ScholarlyArticle 规范（author, abstract, keywords等）
///
/// 设计原则：
///
/// - 使用医学领域标准术语（MeSH, Investigator, Substance 等）
///   - 不包含业务行为，保持共享模块无框架依赖
///   - 主要支持医学数据源（PubMed, EMBASE, MEDLINE）
///   - 保留医学领域特有字段（MeSH 标引、研究者、物质等）
///   - 优化医学出版物处理性能和语义清晰度
///
/// @since 0.1.0
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CanonicalPublication {

  // ==================== 标识符组 ====================

  /// 出版物的多种标识符（PMID, DOI, PMC, PII, arXiv 等）。
  List<Identifier> identifiers;

  // ==================== 基础元数据组 ====================

  /// 出版物标题（主标题）。
  String title;

  /// 原始语言标题（针对翻译作品或多语言出版物）。
  String originalTitle;

  /// 出版物语言（ISO 639-1 代码，如 "en", "zh", "ja"）。
  String language;

  /// 发表类型列表（如 "Journal Article", "Review", "Conference Paper"）。
  List<PublicationType> publicationTypes;

  /// 出版状态。
  ///
  /// 通用值：published（已出版）, preprint（预印本）, in-press（印刷中）, retracted（已撤稿）。
  String publicationStatus;

  /// 媒介类型（出版形式）。
  ///
  /// 可能的值：print（纸质）, electronic（电子）, print-electronic（纸质+电子）。
  String mediaType;

  // ==================== 作者组 ====================

  /// 作者列表（按展示顺序）。
  List<Author> authors;

  /// 作者列表是否完整（true=完整, false=部分作者）。
  Boolean authorsComplete;

  /// 研究者列表（参与研究但非文章作者，常见于临床试验等）。
  List<Investigator> investigators;

  /// 作为主题的人物列表（用于传记、医学史等出版物）。
  List<PersonalNameSubject> personalNameSubjects;

  // ==================== 出版载体组 ====================

  /// 期刊/会议/图书信息（出版载体）。
  Journal journal;

  // ==================== 内容组 ====================

  /// 摘要信息（支持结构化和非结构化摘要）。
  Abstract abstractContent;

  /// 其他语言或版本的摘要列表。
  List<AlternativeAbstract> alternativeAbstracts;

  // ==================== 索引与分类组 ====================

  /// MeSH 主题标引列表（医学主题词表）。
  List<MeshHeading> meshHeadings;

  /// 补充 MeSH 概念列表（疾病、药物试验等特定主题）。
  List<SupplMeshName> supplMeshNames;

  /// 关键词集合列表（支持多个来源的关键词）。
  List<KeywordSet> keywords;

  /// 物质列表（化学物质、生物制品、药物等，医学/化学领域特有）。
  List<Substance> substances;

  /// 基因列表（生物医学领域特有）。
  List<String> genes;

  // ==================== 出版信息组 ====================

  /// 页码信息（包含起始页、结束页和 MEDLINE 格式页码）。
  Pagination pagination;

  /// 多种日期信息（出版、修订、接收、接受等）。
  PublicationDates dates;

  /// 发布历史时间线（详细的发布事件记录）。
  List<PublicationHistoryEvent> publicationHistory;

  // ==================== 关联数据组 ====================

  /// 资助信息列表。
  List<FundingInfo> funding;

  /// 外部关联数据列表（如基因库、临床试验、软件仓库等）。
  List<ExternalReference> externalReferences;

  /// 补充对象列表（图表、数据集、关键词等附加材料）。
  List<SupplementalObject> supplementalObjects;

  /// 被引次数。
  Integer citationCount;

  /// 参考出版物数量。
  Integer numberOfReferences;

  /// 参考出版物列表。
  List<Reference> references;

  // ==================== 质量与合规组 ====================

  /// 利益冲突声明。
  String conflictOfInterestStatement;

  /// 相关项目列表（更正、撤稿、评论、转载等）。
  List<RelatedItem> relatedItems;

  /// 出版物元数据（索引方法、所有者、状态等）。
  PublicationMetadata metadata;

  // ==================== 内嵌值对象 ====================

  /// 标识符（支持多种类型）。
  @Value
  @Builder
  @Jacksonized
  public static class Identifier {

    /// 标识符类型。
    ///
    /// 常见值：doi, pmid, pmc, pii, arxiv, isbn, issn, orcid, ror。
    String type;

    /// 标识符的实际值。
    String value;
  }

  /// 发表类型。
  @Value
  @Builder
  @Jacksonized
  public static class PublicationType {

    /// 发表类型的唯一标识符（来自受控词表）。
    String id;

    /// 发表类型的文本描述（如 "Journal Article", "Conference Paper"）。
    String value;

    /// 词表来源（如 "MeSH", "Crossref"，可选）。
    String vocabularySource;
  }

  /// 作者信息。
  @Value
  @Builder
  @Jacksonized
  public static class Author {

    /// 姓氏。
    String lastName;

    /// 名字。
    String foreName;

    /// 姓名缩写（如 "JD"）。
    String initials;

    /// 后缀（如 "Jr", "III"）。
    String suffix;

    /// 组织作者名称（当作者为组织/团体时使用）。
    String organizationName;

    /// 是否为同等贡献作者。
    Boolean equalContribution;

    /// 作者信息是否有效（用于标识经过验证的作者信息）。
    Boolean valid;

    /// 作者机构列表（一个作者可能隶属多个机构）。
    List<Affiliation> affiliations;

    /// 作者标识符列表（如 ORCID, ResearcherID）。
    List<Identifier> identifiers;
  }

  /// 机构信息。
  @Value
  @Builder
  @Jacksonized
  public static class Affiliation {

    /// 机构名称（自由文本，包含部门、学校等信息）。
    String name;

    /// 机构标识符列表（如 ROR, GRID）。
    List<Identifier> identifiers;
  }

  /// 期刊/会议/图书信息（出版载体）。
  @Value
  @Builder
  @Jacksonized
  public static class Journal {

    /// 期刊全名。
    String title;

    /// ISO 标准缩写。
    String isoAbbreviation;

    /// Medline 标准缩写（来自 MedlineTA）。
    String medlineAbbreviation;

    /// ISSN 号（国际标准期刊号）。
    String issn;

    /// ISSN 类型（print=纸质, electronic=电子）。
    String issnType;

    /// ISSN-L（Linking ISSN，用于关联同一期刊的不同版本）。
    String issnLinking;

    /// NLM 唯一标识符。
    String nlmUniqueId;

    /// 引用媒介（Print, Internet）。
    String citedMedium;

    /// 卷号。
    String volume;

    /// 期号。
    String issue;

    /// 出版国家/地区。
    String country;

    /// 出版商名称。
    String publisher;
  }

  /// 页码信息。
  ///
  /// 支持传统的起止页码以及 MEDLINE 标准格式页码（可能包含电子文章编号）。
  @Value
  @Builder
  @Jacksonized
  public static class Pagination {

    /// 起始页码。
    String startPage;

    /// 结束页码。
    String endPage;

    /// MEDLINE 标准格式页码（如 "123-145" 或 "e12345"）。
    String medlinePgn;
  }

  /// 摘要信息（支持结构化和非结构化摘要）。
  @Value
  @Builder
  @Jacksonized
  public static class Abstract {

    /// 纯文本摘要（非结构化摘要直接使用此字段）。
    String text;

    /// 结构化摘要段落列表（如 BACKGROUND, METHODS, RESULTS, CONCLUSIONS）。
    List<AbstractSection> sections;

    /// 版权信息。
    String copyright;
  }

  /// 摘要段落（结构化摘要的组成部分）。
  @Value
  @Builder
  @Jacksonized
  public static class AbstractSection {

    /// 段落标签（如 BACKGROUND, METHODS, RESULTS, CONCLUSIONS, OBJECTIVE）。
    String label;

    /// 段落类别（用于进一步分类，可选）。
    String category;

    /// 段落内容。
    String content;
  }

  /// 其他语言或版本的摘要。
  @Value
  @Builder
  @Jacksonized
  public static class AlternativeAbstract {

    /// 摘要类型（如 "author", "publisher", "plain-language"）。
    String type;

    /// 语言代码（ISO 639-1，如 "zh", "ja"）。
    String language;

    /// 摘要文本。
    String text;

    /// 版权信息。
    String copyright;
  }

  /// 关键词集合（支持多个来源）。
  @Value
  @Builder
  @Jacksonized
  public static class KeywordSet {

    /// 关键词来源。
    ///
    /// 常见值：author（作者提供）, publisher（出版商）, editor（编辑）, indexer（索引机构）。
    String source;

    /// 关键词列表。
    List<Keyword> keywords;
  }

  /// 单个关键词。
  @Value
  @Builder
  @Jacksonized
  public static class Keyword {

    /// 是否为主要主题。
    Boolean majorTopic;

    /// 关键词文本。
    String term;
  }

  /// 物质信息（化学物质、生物制品、药物等）。
  ///
  /// 主要用于医学、化学、生物学领域。
  @Value
  @Builder
  @Jacksonized
  public static class Substance {

    /// 注册号（如 CAS 号，"0" 表示非特指物质）。
    String registryNumber;

    /// 物质名称。
    String name;

    /// 受控词表中的标识符。
    String vocabularyId;

    /// 受控词表来源（如 "MeSH", "ChEBI", "DrugBank"）。
    String vocabularySource;
  }

  /// 多种日期信息。
  @Value
  @Builder
  @Jacksonized
  public static class PublicationDates {

    /// 主要出版日期（期刊出版日期）。
    LocalDate published;

    /// 电子版发布日期。
    LocalDate electronic;

    /// 记录创建日期。
    LocalDate created;

    /// 出版物完成日期（PubMed 索引流程完成）。
    LocalDate completed;

    /// 最后修订日期。
    LocalDate revised;

    /// 索引完成日期（如 MEDLINE 索引完成）。
    LocalDate indexed;

    /// 收稿日期（投稿日期）。
    LocalDate received;

    /// 接受日期（录用日期）。
    LocalDate accepted;
  }

  /// 资助信息。
  @Value
  @Builder
  @Jacksonized
  public static class FundingInfo {

    /// 资助项目编号/授权号。
    String grantId;

    /// 资助机构名称。
    String funderName;

    /// 资助机构缩写（如 NIH, NSF, NSFC）。
    String funderAcronym;

    /// 资助机构标识符（如 Crossref Funder ID, ROR）。
    String funderIdentifier;

    /// 资助机构所在国家/地区。
    String country;
  }

  /// 外部关联数据（如基因库、临床试验、软件仓库等）。
  @Value
  @Builder
  @Jacksonized
  public static class ExternalReference {

    /// 引用类型。
    ///
    /// 常见值：database（数据库）, clinical-trial（临床试验）, software（软件）, dataset（数据集）。
    String type;

    /// 数据库或资源名称（如 "GenBank", "ClinicalTrials.gov"）。
    String name;

    /// 登记号/标识符列表（如基因序列号、临床试验号）。
    List<String> identifiers;
  }

  /// 相关项目（更正、撤稿、评论、转载等）。
  @Value
  @Builder
  @Jacksonized
  public static class RelatedItem {

    /// 关系类型。
    ///
    /// 常见值：
    ///
    /// - retraction-of（撤稿）
    ///   - erratum-in（勘误）
    ///   - comment-on（评论）
    ///   - republished-from（转载）
    ///   - correction-to（更正）
    ///
    String relationType;

    /// 引用信息（如原出版物的引用格式）。
    String citation;

    /// 关联出版物的标识符。
    String identifier;

    /// 标识符类型（如 "pmid", "doi"）。
    String identifierType;

    /// 说明信息。
    String description;
  }

  /// 发布历史事件。
  ///
  /// 记录出版物在发布过程中的关键时间节点，如收稿、接受、电子发布、PubMed 收录等。
  @Value
  @Builder
  @Jacksonized
  public static class PublicationHistoryEvent {

    /// 发布状态。
    ///
    /// 常见值：
    ///
    /// - received - 收稿日期
    ///   - accepted - 接受日期
    ///   - revised - 修订日期
    ///   - epublish - 电子发布
    ///   - ppublish - 纸质发布
    ///   - pubmed - PubMed 收录
    ///   - medline - MEDLINE 索引
    ///   - entrez - Entrez 数据库收录
    ///
    String status;

    /// 事件发生的年份。
    String year;

    /// 事件发生的月份。
    String month;

    /// 事件发生的日期。
    String day;

    /// 事件发生的小时。
    String hour;

    /// 事件发生的分钟。
    String minute;
  }

  /// 出版物元数据。
  ///
  /// 包含出版物记录的处理信息和质量标注。
  @Value
  @Builder
  @Jacksonized
  public static class PublicationMetadata {

    /// 索引方法（如 "Automated", "Manual"）。
    String indexingMethod;

    /// 数据记录的所有者或来源（如 "NLM", "NASA"）。
    String owner;

    /// 出版物记录的处理状态（如 "MEDLINE", "PubMed", "In-Process"）。
    String status;

    /// 引文子集标识（如 "IM" 表示 Index Medicus）。
    String citationSubset;
  }

  /// 参考出版物。
  ///
  /// 表示文章引用的其他出版物。
  @Value
  @Builder
  @Jacksonized
  public static class Reference {

    /// 引文文本（格式化的引用字符串）。
    String citation;

    /// 参考出版物的标识符列表（如 PMID, DOI）。
    List<Identifier> identifiers;
  }

  /// 研究者（非作者）。
  ///
  /// 参与研究但未列为文章作者的研究人员，常见于大型临床试验、多中心研究等。
  @Value
  @Builder
  @Jacksonized
  public static class Investigator {

    /// 姓氏。
    String lastName;

    /// 名字。
    String foreName;

    /// 姓名缩写（如 "JD"）。
    String initials;

    /// 后缀（如 "Jr", "III"）。
    String suffix;

    /// 研究者信息是否有效。
    Boolean valid;

    /// 机构列表。
    List<Affiliation> affiliations;

    /// 标识符列表（如 ORCID）。
    List<Identifier> identifiers;
  }

  /// 作为主题的人物。
  ///
  /// 用于传记、医学史、案例报告等以特定人物为主题的出版物。
  @Value
  @Builder
  @Jacksonized
  public static class PersonalNameSubject {

    /// 姓氏。
    String lastName;

    /// 名字。
    String foreName;

    /// 姓名缩写。
    String initials;

    /// 后缀。
    String suffix;
  }

  /// 补充对象。
  ///
  /// 表示出版物的附加材料，如图表、数据集、关键词列表、多媒体文件等。
  @Value
  @Builder
  @Jacksonized
  public static class SupplementalObject {

    /// 对象类型（如 "keyword", "figure", "dataset", "video"）。
    String type;

    /// 对象参数列表（键值对形式的属性）。
    List<ObjectParam> params;
  }

  /// 对象参数。
  ///
  /// 表示补充对象的属性键值对。
  @Value
  @Builder
  @Jacksonized
  public static class ObjectParam {

    /// 参数名称。
    String name;

    /// 参数值。
    String value;
  }

  /// MeSH 主题标引。
  ///
  /// Medical Subject Headings (MeSH) 是美国国家医学图书馆（NLM）创建的受控词表，
  /// 用于标引医学出版物的主题和内容。每个 MeSH 标引项包含一个主题词（Descriptor）
  /// 和可选的限定词（Qualifiers）列表。
  ///
  /// 例如："Humans" [主题词] + "genetics" [限定词] 表示"人类遗传学"主题。
  @Value
  @Builder
  @Jacksonized
  public static class MeshHeading {

    /// MeSH 主题词（Descriptor）。
    DescriptorName descriptorName;

    /// MeSH 限定词列表（Qualifiers），用于进一步细化主题范围。
    List<QualifierName> qualifierNames;
  }

  /// MeSH 主题词（Descriptor）。
  ///
  /// 主题词是 MeSH 术语的主要部分，描述文章的核心主题。
  @Value
  @Builder
  @Jacksonized
  public static class DescriptorName {

    /// MeSH 唯一标识符（UI）。
    String ui;

    /// 主题词文本（如 "Humans", "Antibodies", "COVID-19"）。
    String term;

    /// 是否为文章的主要主题。
    Boolean majorTopic;

    /// 主题词类型（可选）。
    ///
    /// 如 "Geographic" 表示地理名称。
    String type;
  }

  /// MeSH 限定词（Qualifier）。
  ///
  /// 限定词用于进一步细化主题词的含义。
  ///
  /// 例如：
  ///
  /// - "Humans" [主题词] + "genetics" [限定词] = "人类遗传学"
  ///   - "Diabetes Mellitus" [主题词] + "drug therapy" [限定词] = "糖尿病药物治疗"
  ///
  @Value
  @Builder
  @Jacksonized
  public static class QualifierName {

    /// MeSH 唯一标识符（UI）。
    String ui;

    /// 限定词文本（如 "genetics", "drug therapy", "diagnosis"）。
    String term;

    /// 是否为文章的主要主题。
    Boolean majorTopic;
  }

  /// 补充 MeSH 概念。
  ///
  /// 补充 MeSH 概念用于描述疾病、药物试验、化学物质等特定主题，
  /// 是对标准 MeSH 主题词的补充。
  @Value
  @Builder
  @Jacksonized
  public static class SupplMeshName {

    /// MeSH 唯一标识符（UI）。
    String ui;

    /// 补充概念名称。
    String name;

    /// 补充概念类型。
    ///
    /// 常见值：Protocol（研究方案）, Disease（疾病）。
    String type;
  }
}

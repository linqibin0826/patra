package com.patra.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Patra 平台规范化文献模型（基于学术元数据标准设计）。
 *
 * <p>此不可变值对象作为采集、目录和溯源微服务间的共享内核模型，封装了学术文献的核心元数据。
 *
 * <p>设计基于以下国际标准：
 *
 * <ul>
 *   <li><b>Dublin Core</b> - 核心元数据标准（title, creator, subject, identifier等）
 *   <li><b>Schema.org</b> - ScholarlyArticle 规范（author, abstract, keywords等）
 *   <li><b>Crossref</b> - 学术文献元数据标准（funder, relation等）
 * </ul>
 *
 * <p>设计原则：
 *
 * <ul>
 *   <li>使用通用术语而非数据源特定术语
 *   <li>不包含业务行为，保持共享模块无框架依赖
 *   <li>支持多种数据源（PubMed, EPMC, Crossref, Scopus等）
 *   <li>医学特有字段通用化但保留支持（通过 vocabulary/source 标注来源）
 *   <li>可扩展至其他学科领域
 * </ul>
 *
 * @since 0.1.0
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CanonicalLiterature {

  // ==================== 标识符组 ====================

  /** 文献的多种标识符（PMID, DOI, PMC, PII, arXiv 等）。 */
  List<Identifier> identifiers;

  // ==================== 基础元数据组 ====================

  /** 文献标题（主标题）。 */
  String title;

  /** 原始语言标题（针对翻译作品或多语言出版物）。 */
  String originalTitle;

  /** 文献语言（ISO 639-1 代码，如 "en", "zh", "ja"）。 */
  String language;

  /** 发表类型列表（如 "Journal Article", "Review", "Conference Paper"）。 */
  List<PublicationType> publicationTypes;

  /**
   * 出版状态。
   *
   * <p>通用值：published（已出版）, preprint（预印本）, in-press（印刷中）, retracted（已撤稿）。
   */
  String publicationStatus;

  /**
   * 媒介类型（出版形式）。
   *
   * <p>可能的值：print（纸质）, electronic（电子）, print-electronic（纸质+电子）。
   */
  String mediaType;

  // ==================== 作者组 ====================

  /** 作者列表（按展示顺序）。 */
  List<Author> authors;

  /** 作者列表是否完整（true=完整, false=部分作者）。 */
  Boolean authorsComplete;

  // ==================== 出版载体组 ====================

  /** 期刊/会议/图书信息（出版载体）。 */
  Journal journal;

  // ==================== 内容组 ====================

  /** 摘要信息（支持结构化和非结构化摘要）。 */
  Abstract abstractContent;

  /** 其他语言或版本的摘要列表。 */
  List<AlternativeAbstract> alternativeAbstracts;

  // ==================== 索引与分类组 ====================

  /** 主题标引列表（支持多种受控词表，如 MeSH, LCSH, 自定义分类等）。 */
  List<Subject> subjects;

  /** 关键词集合列表（支持多个来源的关键词）。 */
  List<KeywordSet> keywords;

  /** 物质列表（化学物质、生物制品、药物等，医学/化学领域特有）。 */
  List<Substance> substances;

  /** 基因列表（生物医学领域特有）。 */
  List<String> genes;

  // ==================== 出版信息组 ====================

  /** 页码信息（如 "123-145" 或 "e12345"）。 */
  String pagination;

  /** 多种日期信息（出版、修订、接收、接受等）。 */
  PublicationDates dates;

  // ==================== 关联数据组 ====================

  /** 资助信息列表。 */
  List<FundingInfo> funding;

  /** 外部关联数据列表（如基因库、临床试验、软件仓库等）。 */
  List<ExternalReference> externalReferences;

  /** 被引次数。 */
  Integer citationCount;

  // ==================== 质量与合规组 ====================

  /** 利益冲突声明。 */
  String conflictOfInterestStatement;

  /** 相关项目列表（更正、撤稿、评论、转载等）。 */
  List<RelatedItem> relatedItems;

  // ==================== 内嵌值对象 ====================

  /** 标识符（支持多种类型）。 */
  @Value
  @Builder
  @Jacksonized
  public static class Identifier {

    /**
     * 标识符类型。
     *
     * <p>常见值：doi, pmid, pmc, pii, arxiv, isbn, issn, orcid, ror。
     */
    String type;

    /** 标识符的实际值。 */
    String value;
  }

  /** 发表类型。 */
  @Value
  @Builder
  @Jacksonized
  public static class PublicationType {

    /** 发表类型的唯一标识符（来自受控词表）。 */
    String id;

    /** 发表类型的文本描述（如 "Journal Article", "Conference Paper"）。 */
    String value;

    /** 词表来源（如 "MeSH", "Crossref"，可选）。 */
    String vocabularySource;
  }

  /** 作者信息。 */
  @Value
  @Builder
  @Jacksonized
  public static class Author {

    /** 姓氏。 */
    String lastName;

    /** 名字。 */
    String foreName;

    /** 姓名缩写（如 "JD"）。 */
    String initials;

    /** 后缀（如 "Jr", "III"）。 */
    String suffix;

    /** 组织作者名称（当作者为组织/团体时使用）。 */
    String organizationName;

    /** 是否为同等贡献作者。 */
    Boolean equalContribution;

    /** 作者机构列表（一个作者可能隶属多个机构）。 */
    List<Affiliation> affiliations;

    /** 作者标识符列表（如 ORCID, ResearcherID）。 */
    List<Identifier> identifiers;
  }

  /** 机构信息。 */
  @Value
  @Builder
  @Jacksonized
  public static class Affiliation {

    /** 机构名称（自由文本，包含部门、学校等信息）。 */
    String name;

    /** 机构标识符列表（如 ROR, GRID）。 */
    List<Identifier> identifiers;
  }

  /** 期刊/会议/图书信息（出版载体）。 */
  @Value
  @Builder
  @Jacksonized
  public static class Journal {

    /** 期刊全名。 */
    String title;

    /** ISO 标准缩写。 */
    String isoAbbreviation;

    /** ISSN 号（国际标准期刊号）。 */
    String issn;

    /** ISSN 类型（print=纸质, electronic=电子）。 */
    String issnType;

    /** 卷号。 */
    String volume;

    /** 期号。 */
    String issue;

    /** 出版国家/地区。 */
    String country;

    /** 出版商名称。 */
    String publisher;
  }

  /** 摘要信息（支持结构化和非结构化摘要）。 */
  @Value
  @Builder
  @Jacksonized
  public static class Abstract {

    /** 纯文本摘要（非结构化摘要直接使用此字段）。 */
    String text;

    /** 结构化摘要段落列表（如 BACKGROUND, METHODS, RESULTS, CONCLUSIONS）。 */
    List<AbstractSection> sections;

    /** 版权信息。 */
    String copyright;
  }

  /** 摘要段落（结构化摘要的组成部分）。 */
  @Value
  @Builder
  @Jacksonized
  public static class AbstractSection {

    /** 段落标签（如 BACKGROUND, METHODS, RESULTS, CONCLUSIONS, OBJECTIVE）。 */
    String label;

    /** 段落类别（用于进一步分类，可选）。 */
    String category;

    /** 段落内容。 */
    String content;
  }

  /** 其他语言或版本的摘要。 */
  @Value
  @Builder
  @Jacksonized
  public static class AlternativeAbstract {

    /** 摘要类型（如 "author", "publisher", "plain-language"）。 */
    String type;

    /** 语言代码（ISO 639-1，如 "zh", "ja"）。 */
    String language;

    /** 摘要文本。 */
    String text;

    /** 版权信息。 */
    String copyright;
  }

  /**
   * 主题标引（支持多种受控词表）。
   *
   * <p>可表示 MeSH 主题词、LCSH、自定义分类等。
   */
  @Value
  @Builder
  @Jacksonized
  public static class Subject {

    /** 主题词唯一标识符。 */
    String id;

    /** 主题词名称。 */
    String term;

    /** 是否为文章的主要主题。 */
    Boolean majorTopic;

    /** 主题词类型（如 "geographic" 表示地理名称，可选）。 */
    String type;

    /**
     * 受控词表来源。
     *
     * <p>常见值：MeSH（医学主题词表）, LCSH（国会图书馆主题词表）, custom（自定义）。
     */
    String vocabulary;

    /** 限定词列表（用于进一步限定主题词的范围，可选）。 */
    List<SubjectQualifier> qualifiers;
  }

  /** 主题词限定词（用于细化主题范围）。 */
  @Value
  @Builder
  @Jacksonized
  public static class SubjectQualifier {

    /** 限定词唯一标识符。 */
    String id;

    /** 限定词名称。 */
    String term;

    /** 是否为文章的主要主题。 */
    Boolean majorTopic;
  }

  /** 关键词集合（支持多个来源）。 */
  @Value
  @Builder
  @Jacksonized
  public static class KeywordSet {

    /**
     * 关键词来源。
     *
     * <p>常见值：author（作者提供）, publisher（出版商）, editor（编辑）, indexer（索引机构）。
     */
    String source;

    /** 关键词列表。 */
    List<Keyword> keywords;
  }

  /** 单个关键词。 */
  @Value
  @Builder
  @Jacksonized
  public static class Keyword {

    /** 是否为主要主题。 */
    Boolean majorTopic;

    /** 关键词文本。 */
    String term;
  }

  /**
   * 物质信息（化学物质、生物制品、药物等）。
   *
   * <p>主要用于医学、化学、生物学领域。
   */
  @Value
  @Builder
  @Jacksonized
  public static class Substance {

    /** 注册号（如 CAS 号，"0" 表示非特指物质）。 */
    String registryNumber;

    /** 物质名称。 */
    String name;

    /** 受控词表中的标识符。 */
    String vocabularyId;

    /** 受控词表来源（如 "MeSH", "ChEBI", "DrugBank"）。 */
    String vocabularySource;
  }

  /** 多种日期信息。 */
  @Value
  @Builder
  @Jacksonized
  public static class PublicationDates {

    /** 主要出版日期（期刊出版日期）。 */
    LocalDate published;

    /** 电子版发布日期。 */
    LocalDate electronic;

    /** 记录创建日期。 */
    LocalDate created;

    /** 最后修订日期。 */
    LocalDate revised;

    /** 索引完成日期（如 MEDLINE 索引完成）。 */
    LocalDate indexed;

    /** 收稿日期（投稿日期）。 */
    LocalDate received;

    /** 接受日期（录用日期）。 */
    LocalDate accepted;
  }

  /** 资助信息。 */
  @Value
  @Builder
  @Jacksonized
  public static class FundingInfo {

    /** 资助项目编号/授权号。 */
    String grantId;

    /** 资助机构名称。 */
    String funderName;

    /** 资助机构标识符（如 Crossref Funder ID, ROR）。 */
    String funderIdentifier;

    /** 资助机构所在国家/地区。 */
    String country;
  }

  /** 外部关联数据（如基因库、临床试验、软件仓库等）。 */
  @Value
  @Builder
  @Jacksonized
  public static class ExternalReference {

    /**
     * 引用类型。
     *
     * <p>常见值：database（数据库）, clinical-trial（临床试验）, software（软件）, dataset（数据集）。
     */
    String type;

    /** 数据库或资源名称（如 "GenBank", "ClinicalTrials.gov"）。 */
    String name;

    /** 登记号/标识符列表（如基因序列号、临床试验号）。 */
    List<String> identifiers;
  }

  /** 相关项目（更正、撤稿、评论、转载等）。 */
  @Value
  @Builder
  @Jacksonized
  public static class RelatedItem {

    /**
     * 关系类型。
     *
     * <p>常见值：
     *
     * <ul>
     *   <li>retraction-of（撤稿）
     *   <li>erratum-in（勘误）
     *   <li>comment-on（评论）
     *   <li>republished-from（转载）
     *   <li>correction-to（更正）
     * </ul>
     */
    String relationType;

    /** 引用信息（如原文献的引用格式）。 */
    String citation;

    /** 关联文献的标识符。 */
    String identifier;

    /** 标识符类型（如 "pmid", "doi"）。 */
    String identifierType;

    /** 说明信息。 */
    String description;
  }
}

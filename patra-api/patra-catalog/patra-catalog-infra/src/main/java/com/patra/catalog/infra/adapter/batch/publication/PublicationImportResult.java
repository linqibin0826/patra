package com.patra.catalog.infra.adapter.batch.publication;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.vo.publication.PublicationMetadata;
import java.util.List;
import lombok.Builder;

/// 文献导入结果包装类。
///
/// **设计说明**：
///
/// 封装 ItemProcessor 处理后的所有数据，包括：
/// - 主数据：PublicationAggregate（文献聚合根）
/// - 关联数据：MeSH 标引、关键词、资助信息、出版类型、补充 MeSH 概念、翻译摘要、日期等
///
/// **使用场景**：
///
/// ItemProcessor 返回此对象，ItemWriter 分别处理主数据和关联数据：
/// 1. 先写入 PublicationAggregate，获取生成的 ID
/// 2. 使用 Publication ID 写入关联数据
///
/// @param publication 文献聚合根（主数据）
/// @param metadata 文献元数据（索引状态、数据溯源等）
/// @param meshHeadings MeSH 标引数据
/// @param keywords 关键词数据
/// @param funding 资助信息数据
/// @param publicationTypes 出版类型数据
/// @param supplMeshNames 补充 MeSH 概念数据
/// @param alternativeAbstracts 翻译摘要数据
/// @param dates 日期数据
/// @author linqibin
/// @since 0.1.0
@Builder
public record PublicationImportResult(
    PublicationAggregate publication,
    PublicationMetadata metadata,
    List<MeshHeadingData> meshHeadings,
    List<KeywordData> keywords,
    List<FundingData> funding,
    List<PublicationTypeData> publicationTypes,
    List<SupplMeshData> supplMeshNames,
    List<AlternativeAbstractData> alternativeAbstracts,
    List<PublicationDateData> dates) {

  /// Compact constructor：确保所有集合字段不为 null。
  public PublicationImportResult {
    meshHeadings = meshHeadings != null ? List.copyOf(meshHeadings) : List.of();
    keywords = keywords != null ? List.copyOf(keywords) : List.of();
    funding = funding != null ? List.copyOf(funding) : List.of();
    publicationTypes = publicationTypes != null ? List.copyOf(publicationTypes) : List.of();
    supplMeshNames = supplMeshNames != null ? List.copyOf(supplMeshNames) : List.of();
    alternativeAbstracts =
        alternativeAbstracts != null ? List.copyOf(alternativeAbstracts) : List.of();
    dates = dates != null ? List.copyOf(dates) : List.of();
  }

  /// 创建仅包含主数据的结果（无关联数据）。
  ///
  /// **使用场景**：测试或仅需主数据时使用。
  ///
  /// @param publication 文献聚合根
  /// @return 结果对象
  public static PublicationImportResult ofPublication(PublicationAggregate publication) {
    return new PublicationImportResult(
        publication,
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  /// 创建包含所有关联数据的完整结果。
  ///
  /// **使用场景**：Processor 返回完整处理结果时使用。
  ///
  /// @param publication 文献聚合根
  /// @param metadata 文献元数据
  /// @param meshHeadings MeSH 标引数据
  /// @param keywords 关键词数据
  /// @param funding 资助信息数据
  /// @param publicationTypes 出版类型数据
  /// @param supplMeshNames 补充 MeSH 概念数据
  /// @param alternativeAbstracts 翻译摘要数据
  /// @param dates 日期数据
  /// @return 结果对象
  public static PublicationImportResult ofComplete(
      PublicationAggregate publication,
      PublicationMetadata metadata,
      List<MeshHeadingData> meshHeadings,
      List<KeywordData> keywords,
      List<FundingData> funding,
      List<PublicationTypeData> publicationTypes,
      List<SupplMeshData> supplMeshNames,
      List<AlternativeAbstractData> alternativeAbstracts,
      List<PublicationDateData> dates) {
    // compact constructor 会自动处理 null 值并进行防御性拷贝
    return new PublicationImportResult(
        publication,
        metadata,
        meshHeadings,
        keywords,
        funding,
        publicationTypes,
        supplMeshNames,
        alternativeAbstracts,
        dates);
  }

  /// 是否有元数据。
  public boolean hasMetadata() {
    return metadata != null;
  }

  /// 是否有 MeSH 标引数据。
  public boolean hasMeshHeadings() {
    return !meshHeadings.isEmpty();
  }

  /// 是否有关键词数据。
  public boolean hasKeywords() {
    return !keywords.isEmpty();
  }

  /// 是否有资助信息数据。
  public boolean hasFunding() {
    return !funding.isEmpty();
  }

  /// 是否有出版类型数据。
  public boolean hasPublicationTypes() {
    return !publicationTypes.isEmpty();
  }

  /// 是否有补充 MeSH 概念数据。
  public boolean hasSupplMeshNames() {
    return !supplMeshNames.isEmpty();
  }

  /// 是否有翻译摘要数据。
  public boolean hasAlternativeAbstracts() {
    return !alternativeAbstracts.isEmpty();
  }

  /// 是否有日期数据。
  public boolean hasDates() {
    return !dates.isEmpty();
  }

  // ==================== MeSH 相关数据类型 ====================

  /// MeSH 标引数据（中间结构，用于后续写入关联表）。
  ///
  /// @param descriptorUi MeSH 主题词 UI（如 "D000001"）
  /// @param majorTopic 是否为主要主题
  /// @param headingOrder 标引顺序
  /// @param qualifiers 限定词列表
  public record MeshHeadingData(
      String descriptorUi,
      boolean majorTopic,
      Integer headingOrder,
      List<QualifierData> qualifiers) {

    /// 创建标引数据。
    public static MeshHeadingData of(
        String descriptorUi,
        boolean majorTopic,
        Integer headingOrder,
        List<QualifierData> qualifiers) {
      return new MeshHeadingData(
          descriptorUi, majorTopic, headingOrder, qualifiers != null ? qualifiers : List.of());
    }

    /// 是否有限定词。
    public boolean hasQualifiers() {
      return qualifiers != null && !qualifiers.isEmpty();
    }
  }

  /// MeSH 限定词数据。
  ///
  /// @param qualifierUi MeSH 限定词 UI（如 "Q000379"）
  /// @param majorTopic 是否为主要主题
  /// @param qualifierOrder 限定词顺序（在同一标引内）
  public record QualifierData(String qualifierUi, boolean majorTopic, Integer qualifierOrder) {

    /// 创建限定词数据。
    public static QualifierData of(String qualifierUi, boolean majorTopic, Integer qualifierOrder) {
      return new QualifierData(qualifierUi, majorTopic, qualifierOrder);
    }
  }

  // ==================== 关键词数据类型 ====================

  /// 关键词数据。
  ///
  /// @param source 关键词来源（author, publisher, indexer 等）
  /// @param term 关键词文本
  /// @param majorTopic 是否为主要主题
  /// @param keywordOrder 顺序
  @Builder
  public record KeywordData(String source, String term, boolean majorTopic, Integer keywordOrder) {

    /// 创建关键词数据。
    public static KeywordData of(String source, String term, boolean majorTopic, Integer order) {
      return new KeywordData(source, term, majorTopic, order);
    }
  }

  // ==================== 资助信息数据类型 ====================

  /// 资助信息数据。
  ///
  /// **设计说明**：
  ///
  /// - `organizationId`：资助机构 ID（通过 FunderLookupPort 匹配后填充）
  /// - `funderNameRaw` 等字段：保留原始数据，用于后续机构匹配和数据质量分析
  /// - `provenanceCode`：数据来源追踪（PUBMED/OPENALEX/CROSSREF 等）
  ///
  /// @param organizationId 资助机构 ID（匹配后填充，可能为 null）
  /// @param grantId 项目编号/授权号
  /// @param funderNameRaw 资助机构原始名称
  /// @param funderAcronymRaw 资助机构缩写原始值
  /// @param funderIdentifierRaw 资助机构标识符原始值
  /// @param countryRaw 国家/地区原始值
  /// @param fundingOrder 顺序
  /// @param provenanceCode 数据来源
  @Builder
  public record FundingData(
      Long organizationId,
      String grantId,
      String funderNameRaw,
      String funderAcronymRaw,
      String funderIdentifierRaw,
      String countryRaw,
      Integer fundingOrder,
      String provenanceCode) {}

  // ==================== 出版类型数据类型 ====================

  /// 出版类型数据。
  ///
  /// @param typeId 类型标识符（来自受控词表）
  /// @param typeValue 类型文本描述
  /// @param vocabularySource 词表来源
  /// @param typeOrder 顺序
  @Builder
  public record PublicationTypeData(
      String typeId, String typeValue, String vocabularySource, Integer typeOrder) {

    /// 创建出版类型数据。
    public static PublicationTypeData of(
        String typeId, String typeValue, String vocabularySource, Integer order) {
      return new PublicationTypeData(typeId, typeValue, vocabularySource, order);
    }
  }

  // ==================== 补充 MeSH 概念数据类型 ====================

  /// 补充 MeSH 概念数据。
  ///
  /// 用于存储 PubMed 文献中的 SupplMeshNameList 数据，关联到 MeSH SCR（补充概念记录）。
  ///
  /// **业务含义**：
  ///
  /// MeSH SCR 用于标注文献中的化学物质、疾病变体、实验方案等概念，
  /// 与 MeshHeading（正式描述符）互补，提供更精细的标引。
  ///
  /// @param scrUi SCR UI（如 "C538003"）
  /// @param supplOrder 顺序（保留 XML 中的顺序）
  public record SupplMeshData(String scrUi, Integer supplOrder) {

    /// 创建补充 MeSH 概念数据。
    public static SupplMeshData of(String scrUi, Integer supplOrder) {
      return new SupplMeshData(scrUi, supplOrder);
    }
  }

  // ==================== 翻译摘要数据类型 ====================

  /// 翻译摘要数据。
  ///
  /// 用于存储 PubMed 文献中的 OtherAbstract 数据（其他语言摘要）。
  ///
  /// **业务含义**：
  ///
  /// OtherAbstract 包含文献摘要的翻译版本，来源包括：
  /// - Publisher（出版商提供）：官方翻译
  /// - AIMSHP/KIEML/NASA 等：专业机构翻译
  /// - plain-language-summary：面向患者的通俗语言摘要
  ///
  /// @param languageCode 语言代码（ISO 639-1，如 "zh"、"ja"）
  /// @param abstractType 摘要类型（如 "Publisher"、"AIMSHP"、"plain-language-summary"）
  /// @param plainText 摘要文本
  /// @param copyright 版权信息
  /// @param abstractOrder 顺序号
  @Builder
  public record AlternativeAbstractData(
      String languageCode,
      String abstractType,
      String plainText,
      String copyright,
      Integer abstractOrder) {

    /// 创建翻译摘要数据。
    public static AlternativeAbstractData of(
        String languageCode,
        String abstractType,
        String plainText,
        String copyright,
        Integer order) {
      return new AlternativeAbstractData(languageCode, abstractType, plainText, copyright, order);
    }
  }

  // ==================== 日期数据类型 ====================

  /// 文献日期数据。
  ///
  /// 用于存储文献生命周期中的各类日期（投稿、接收、发表、修订等）。
  ///
  /// **支持不完整日期**：
  ///
  /// 医学文献的日期经常不完整，本数据结构支持三种精度级别：
  /// - `day` - 精确到日（year + month + day 全部有值）
  /// - `month` - 精确到月（仅 year + month 有值）
  /// - `year` - 仅有年份（仅 year 有值）
  ///
  /// **日期类型说明**：
  ///
  /// | 类型 | 说明 |
  /// |------|------|
  /// | RECEIVED | 投稿日期 |
  /// | ACCEPTED | 接收日期 |
  /// | REVISED | 修订日期 |
  /// | PUBLISHED | 发表日期 |
  /// | EPUBLISH | 电子版发表日期 |
  /// | ENTREZ_DATE | 进入 PubMed 数据库日期 |
  /// | OTHER | 其他日期类型 |
  ///
  /// @param dateType 日期类型代码（如 "received"、"accepted"、"published"）
  /// @param year 年份（必填）
  /// @param month 月份（1-12，可为空）
  /// @param day 日期（1-31，可为空）
  /// @param datePrecision 日期精度代码（"year"、"month"、"day"）
  /// @param season 季节（如 "Spring 2024"，可为空）
  /// @param dateString 原始日期字符串（可为空）
  /// @param isPrimary 是否主要日期
  /// @param orderNum 顺序号
  @Builder
  public record PublicationDateData(
      String dateType,
      int year,
      Integer month,
      Integer day,
      String datePrecision,
      String season,
      String dateString,
      boolean isPrimary,
      Integer orderNum) {

    /// 创建完整日期数据（精确到日）。
    ///
    /// @param dateType 日期类型代码
    /// @param year 年份
    /// @param month 月份
    /// @param day 日期
    /// @param orderNum 顺序号
    /// @return 日期数据对象
    public static PublicationDateData of(
        String dateType, int year, int month, int day, Integer orderNum) {
      return new PublicationDateData(
          dateType, year, month, day, "day", null, null, false, orderNum);
    }

    /// 从 LocalDate 创建日期数据。
    ///
    /// @param dateType 日期类型代码
    /// @param localDate LocalDate 对象
    /// @param orderNum 顺序号
    /// @return 日期数据对象
    public static PublicationDateData fromLocalDate(
        String dateType, java.time.LocalDate localDate, Integer orderNum) {
      return new PublicationDateData(
          dateType,
          localDate.getYear(),
          localDate.getMonthValue(),
          localDate.getDayOfMonth(),
          "day",
          null,
          null,
          false,
          orderNum);
    }

    /// 创建主要日期数据。
    ///
    /// @param dateType 日期类型代码
    /// @param localDate LocalDate 对象
    /// @param orderNum 顺序号
    /// @return 主要日期数据对象
    public static PublicationDateData primaryFromLocalDate(
        String dateType, java.time.LocalDate localDate, Integer orderNum) {
      return new PublicationDateData(
          dateType,
          localDate.getYear(),
          localDate.getMonthValue(),
          localDate.getDayOfMonth(),
          "day",
          null,
          null,
          true,
          orderNum);
    }
  }
}

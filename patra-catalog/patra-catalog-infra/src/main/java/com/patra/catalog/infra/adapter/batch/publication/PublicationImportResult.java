package com.patra.catalog.infra.adapter.batch.publication;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import java.util.List;
import lombok.Builder;

/// 文献导入结果包装类。
///
/// **设计说明**：
///
/// 封装 ItemProcessor 处理后的所有数据，包括：
/// - 主数据：PublicationAggregate（文献聚合根）
/// - 关联数据：MeSH 标引、关键词、资助信息、出版类型等
///
/// **使用场景**：
///
/// ItemProcessor 返回此对象，ItemWriter 分别处理主数据和关联数据：
/// 1. 先写入 PublicationAggregate，获取生成的 ID
/// 2. 使用 Publication ID 写入关联数据
///
/// @param publication 文献聚合根（主数据）
/// @param meshHeadings MeSH 标引数据
/// @param keywords 关键词数据
/// @param funding 资助信息数据
/// @param publicationTypes 出版类型数据
/// @author linqibin
/// @since 0.1.0
@Builder
public record PublicationImportResult(
    PublicationAggregate publication,
    List<MeshHeadingData> meshHeadings,
    List<KeywordData> keywords,
    List<FundingData> funding,
    List<PublicationTypeData> publicationTypes) {

  /// 创建仅包含主数据的结果（无关联数据）。
  ///
  /// @param publication 文献聚合根
  /// @return 结果对象
  public static PublicationImportResult ofPublication(PublicationAggregate publication) {
    return new PublicationImportResult(publication, List.of(), List.of(), List.of(), List.of());
  }

  /// 创建包含 MeSH 数据的结果（向后兼容）。
  ///
  /// @param publication 文献聚合根
  /// @param meshHeadings MeSH 标引数据
  /// @return 结果对象
  public static PublicationImportResult of(
      PublicationAggregate publication, List<MeshHeadingData> meshHeadings) {
    return new PublicationImportResult(
        publication,
        meshHeadings != null ? meshHeadings : List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  /// 创建包含所有关联数据的结果。
  ///
  /// @param publication 文献聚合根
  /// @param meshHeadings MeSH 标引数据
  /// @param keywords 关键词数据
  /// @param funding 资助信息数据
  /// @param publicationTypes 出版类型数据
  /// @return 结果对象
  public static PublicationImportResult ofAll(
      PublicationAggregate publication,
      List<MeshHeadingData> meshHeadings,
      List<KeywordData> keywords,
      List<FundingData> funding,
      List<PublicationTypeData> publicationTypes) {
    return new PublicationImportResult(
        publication,
        meshHeadings != null ? meshHeadings : List.of(),
        keywords != null ? keywords : List.of(),
        funding != null ? funding : List.of(),
        publicationTypes != null ? publicationTypes : List.of());
  }

  /// 是否有 MeSH 标引数据。
  public boolean hasMeshHeadings() {
    return meshHeadings != null && !meshHeadings.isEmpty();
  }

  /// 是否有关键词数据。
  public boolean hasKeywords() {
    return keywords != null && !keywords.isEmpty();
  }

  /// 是否有资助信息数据。
  public boolean hasFunding() {
    return funding != null && !funding.isEmpty();
  }

  /// 是否有出版类型数据。
  public boolean hasPublicationTypes() {
    return publicationTypes != null && !publicationTypes.isEmpty();
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
  public record QualifierData(String qualifierUi, boolean majorTopic) {

    /// 创建限定词数据。
    public static QualifierData of(String qualifierUi, boolean majorTopic) {
      return new QualifierData(qualifierUi, majorTopic);
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
}

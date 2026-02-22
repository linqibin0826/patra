package com.patra.catalog.adapter.rest.publication.response;

import java.time.Instant;
import java.util.List;

/// Publication 详情响应 DTO。
///
/// 遵循六边形架构规范，Adapter 层响应 DTO 不应直接依赖 Domain 层值对象，
/// 而应定义自己的嵌套 Response Record 结构。
///
/// @param id 文献主键 ID
/// @param provenanceCode 数据来源代码
/// @param title 标题
/// @param originalTitle 原始标题（可空）
/// @param pmid PubMed ID（可空）
/// @param doi DOI（可空）
/// @param publicationYear 出版年份（可空）
/// @param languageCode 语言代码（可空）
/// @param languageRaw 原始语言文本（可空）
/// @param languageBase 基础语种代码（可空）
/// @param publicationStatus 出版状态（可空）
/// @param mediaType 媒体类型（可空）
/// @param isOa 是否有 OA 版本（可空）
/// @param oaStatus OA 状态（可空）
/// @param venueId 载体 ID（可空）
/// @param venueName 载体名称（可空）
/// @param citationCount 被引次数（可空）
/// @param numberOfReferences 参考文献数量（可空）
/// @param authorsComplete 作者列表是否完整（可空）
/// @param conflictOfInterest 利益冲突声明（可空）
/// @param lastSyncedAt 最后同步时间（可空）
/// @param createdAt 创建时间
/// @param updatedAt 更新时间
/// @param abstracts 摘要列表
/// @param identifiers 标识符列表
/// @param keywords 关键词列表
/// @param meshHeadings MeSH 标引列表
public record PublicationDetailResponse(
    Long id,
    String provenanceCode,
    String title,
    String originalTitle,
    String pmid,
    String doi,
    Integer publicationYear,
    String languageCode,
    String languageRaw,
    String languageBase,
    String publicationStatus,
    String mediaType,
    Boolean isOa,
    String oaStatus,
    Long venueId,
    String venueName,
    Integer citationCount,
    Integer numberOfReferences,
    Boolean authorsComplete,
    String conflictOfInterest,
    Instant lastSyncedAt,
    Instant createdAt,
    Instant updatedAt,
    List<AbstractDto> abstracts,
    List<IdentifierDto> identifiers,
    List<KeywordDto> keywords,
    List<MeshHeadingDto> meshHeadings) {

  /// 摘要 DTO。
  ///
  /// @param plainText 纯文本摘要
  /// @param structuredSections 结构化摘要内容（JSON 字符串）
  /// @param copyright 版权声明
  /// @param abstractType 摘要类型
  public record AbstractDto(
      String plainText, String structuredSections, String copyright, String abstractType) {}

  /// 标识符 DTO。
  ///
  /// @param type 标识符类型
  /// @param value 标识符值
  /// @param source 来源
  public record IdentifierDto(String type, String value, String source) {}

  /// 关键词 DTO。
  ///
  /// @param term 关键词文本
  /// @param major 是否主要关键词
  /// @param keywordSet 关键词集名称
  public record KeywordDto(String term, Boolean major, String keywordSet) {}

  /// MeSH 标引 DTO。
  ///
  /// @param descriptorUi MeSH 描述符 UI
  /// @param majorTopic 是否主要主题
  /// @param qualifiers 限定词列表
  public record MeshHeadingDto(
      String descriptorUi, Boolean majorTopic, List<MeshQualifierDto> qualifiers) {

    /// MeSH 限定词 DTO。
    ///
    /// @param qualifierUi 限定词 UI
    /// @param majorTopic 是否主要主题
    public record MeshQualifierDto(String qualifierUi, Boolean majorTopic) {}
  }
}

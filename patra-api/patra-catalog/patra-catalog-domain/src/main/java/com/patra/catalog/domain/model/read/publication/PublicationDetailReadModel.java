package com.patra.catalog.domain.model.read.publication;

import cn.hutool.core.lang.Assert;
import java.time.Instant;
import java.util.List;
import lombok.Builder;

/// 文献出版物详情读模型。
///
/// 用于 CQRS 读端详情查询，包含文献的完整信息：
/// 主表字段 + 摘要 + 标识符 + 关键词 + MeSH 标引。
///
/// @author linqibin
/// @since 0.1.0
@Builder
public record PublicationDetailReadModel(
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
    List<AbstractInfo> abstracts,
    List<IdentifierInfo> identifiers,
    List<KeywordInfo> keywords,
    List<MeshHeadingInfo> meshHeadings) {

  public PublicationDetailReadModel {
    Assert.notNull(id, "出版物 ID 不能为空");
    Assert.notBlank(title, "出版物标题不能为空");
    Assert.notNull(createdAt, "创建时间不能为空");
    Assert.notNull(updatedAt, "更新时间不能为空");
    abstracts = abstracts != null ? List.copyOf(abstracts) : List.of();
    identifiers = identifiers != null ? List.copyOf(identifiers) : List.of();
    keywords = keywords != null ? List.copyOf(keywords) : List.of();
    meshHeadings = meshHeadings != null ? List.copyOf(meshHeadings) : List.of();
  }

  /// 摘要信息。
  ///
  /// @param plainText 纯文本摘要
  /// @param structuredSections 结构化摘要内容（JSON 字符串）
  /// @param copyright 版权声明
  /// @param abstractType 摘要类型（MAIN、PLAIN_LANGUAGE 等）
  public record AbstractInfo(
      String plainText, String structuredSections, String copyright, String abstractType) {}

  /// 标识符信息。
  ///
  /// @param type 标识符类型（pmid、doi、pmc 等）
  /// @param value 标识符值
  /// @param source 来源（pubmed、crossref 等）
  public record IdentifierInfo(String type, String value, String source) {}

  /// 关键词信息。
  ///
  /// @param term 关键词文本
  /// @param major 是否主要关键词
  /// @param keywordSet 关键词集名称
  public record KeywordInfo(String term, Boolean major, String keywordSet) {}

  /// MeSH 标引信息。
  ///
  /// @param descriptorUi MeSH 描述符 UI
  /// @param majorTopic 是否主要主题
  /// @param qualifiers 限定词列表
  public record MeshHeadingInfo(
      String descriptorUi, Boolean majorTopic, List<MeshQualifierInfo> qualifiers) {

    public MeshHeadingInfo {
      qualifiers = qualifiers != null ? List.copyOf(qualifiers) : List.of();
    }

    /// MeSH 限定词信息。
    ///
    /// @param qualifierUi 限定词 UI
    /// @param majorTopic 是否主要主题
    public record MeshQualifierInfo(String qualifierUi, Boolean majorTopic) {}
  }
}

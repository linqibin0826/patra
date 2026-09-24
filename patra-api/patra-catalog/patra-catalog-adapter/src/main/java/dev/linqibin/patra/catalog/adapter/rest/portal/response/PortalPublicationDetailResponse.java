package dev.linqibin.patra.catalog.adapter.rest.portal.response;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/// Portal 文献详情响应 DTO。
///
/// 字段名与类型直接对齐前端 `PublicationDetail`，前端零字段映射。
///
/// @param id 文献主键（String，避免 JS 超 2^53 精度损失）
/// @param title 文献标题
/// @param originalTitle 原始语言标题（非英文文献时填充）
/// @param venueId 期刊 ID（String）
/// @param venueName 期刊名称
/// @param publicationYear 出版年份
/// @param evidenceLevel 证据等级（衍生自出版类型）
/// @param abstractType 摘要类型
/// @param abstractSections 结构化摘要段落（有序数组）
/// @param abstractPlainText 纯文本摘要
/// @param doi DOI
/// @param pmid PubMed ID
/// @param pmcid PubMed Central ID
/// @param pii Publisher Item Identifier
/// @param primaryType 主出版类型（第一条）
/// @param publicationTypes 全部出版类型列表
/// @param citationCount 被引次数
/// @param numberOfReferences 参考文献数
/// @param conflictOfInterest 利益冲突声明
/// @param isOa 是否 OA
/// @param oaStatus OA 状态
/// @param authors 作者列表
/// @param meshHeadings MeSH 标引列表
/// @param keywords 关键词列表
/// @param funding 资助信息列表
/// @param dates 出版日期列表
/// @param aiSummary AI 摘要（当前恒为 null）
/// @param source 数据来源（人类可读，如 "PubMed"；来自 provenance_code）
/// @param fullTextUrl 原文链接（cat_publication_metadata.full_text_url，可为 null）
/// @param bookmarks 收藏数（无用户系统，恒为 0）
/// @param estimatedReadMin 预估阅读分钟数（无全文/字数数据，当前恒为 null）
/// @author linqibin
/// @since 0.1.0
@Builder
public record PortalPublicationDetailResponse(
    String id,
    String title,
    String originalTitle,
    String venueId,
    String venueName,
    Integer publicationYear,
    EvidenceLevelView evidenceLevel,
    String abstractType,
    List<AbstractSection> abstractSections,
    String abstractPlainText,
    String doi,
    String pmid,
    String pmcid,
    String pii,
    String primaryType,
    List<String> publicationTypes,
    Integer citationCount,
    Integer numberOfReferences,
    String conflictOfInterest,
    Boolean isOa,
    String oaStatus,
    List<Author> authors,
    List<MeshHeading> meshHeadings,
    List<String> keywords,
    List<Funding> funding,
    List<PublicationDate> dates,
    String aiSummary,
    String source,
    String fullTextUrl,
    Integer bookmarks,
    Integer estimatedReadMin) {

  public PortalPublicationDetailResponse {
    abstractSections = abstractSections != null ? List.copyOf(abstractSections) : List.of();
    publicationTypes = publicationTypes != null ? List.copyOf(publicationTypes) : List.of();
    authors = authors != null ? List.copyOf(authors) : List.of();
    meshHeadings = meshHeadings != null ? List.copyOf(meshHeadings) : List.of();
    keywords = keywords != null ? List.copyOf(keywords) : List.of();
    funding = funding != null ? List.copyOf(funding) : List.of();
    dates = dates != null ? List.copyOf(dates) : List.of();
  }

  /// 结构化摘要段落响应。
  ///
  /// @param label 段落标签（如 BACKGROUND、METHODS、RESULTS）；混合摘要的无标签段为 null
  /// @param text 段落内容
  public record AbstractSection(String label, String text) {

    /// 创建摘要段落响应。
    ///
    /// @param label 段落标签
    /// @param text 段落内容
    /// @return 摘要段落响应
    public static AbstractSection of(String label, String text) {
      return new AbstractSection(label, text);
    }
  }

  /// 作者响应。
  ///
  /// @param order 作者顺序
  /// @param first 是否第一作者
  /// @param corresponding 是否通讯作者
  /// @param name 作者姓名
  /// @param affiliation 主机构字符串
  @Builder
  public record Author(
      int order, boolean first, boolean corresponding, String name, String affiliation) {}

  /// MeSH 标引响应。
  ///
  /// @param descriptorUi MeSH 主题词 UI
  /// @param term 主题词名称
  /// @param major 是否为主要主题
  public record MeshHeading(String descriptorUi, String term, boolean major) {

    /// 创建 MeSH 标引响应。
    ///
    /// @param descriptorUi MeSH 主题词 UI
    /// @param term 主题词名称
    /// @param major 是否为主要主题
    /// @return MeSH 标引响应
    public static MeshHeading of(String descriptorUi, String term, boolean major) {
      return new MeshHeading(descriptorUi, term, major);
    }
  }

  /// 资助信息响应。
  ///
  /// @param funder 资助方名称
  /// @param grantId 资助编号
  /// @param country 资助国家
  public record Funding(String funder, String grantId, String country) {

    /// 创建资助信息响应。
    ///
    /// @param funder 资助方名称
    /// @param grantId 资助编号
    /// @param country 资助国家
    /// @return 资助信息响应
    public static Funding of(String funder, String grantId, String country) {
      return new Funding(funder, grantId, country);
    }
  }

  /// 出版日期响应。
  ///
  /// @param type 日期类型
  /// @param date 日期值
  public record PublicationDate(String type, LocalDate date) {

    /// 创建出版日期响应。
    ///
    /// @param type 日期类型
    /// @param date 日期值
    /// @return 出版日期响应
    public static PublicationDate of(String type, LocalDate date) {
      return new PublicationDate(type, date);
    }
  }
}

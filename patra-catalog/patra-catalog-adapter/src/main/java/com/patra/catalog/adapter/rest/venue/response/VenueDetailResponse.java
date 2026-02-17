package com.patra.catalog.adapter.rest.venue.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/// Venue 详情响应 DTO。
///
/// 遵循六边形架构规范，Adapter 层响应 DTO 不应直接依赖 Domain 层值对象，
/// 而应定义自己的嵌套 Response Record 结构。
///
/// @param id 期刊主键 ID
/// @param venueType 载体类型
/// @param title 期刊标题
/// @param titleZh 中文标题（可空）
/// @param issnL ISSN-L（可空）
/// @param nlmId NLM ID（可空）
/// @param openalexId OpenAlex Source ID（可空）
/// @param abbreviatedTitle 缩写标题（可空）
/// @param primaryLanguage 主要语言代码（可空）
/// @param countryCode 国家编码（可空）
/// @param provenanceCode 数据来源编码
/// @param publicationProfile 出版概况（可空）
/// @param citationMetrics 引用指标（可空）
/// @param openAccess 开放获取信息（可空）
/// @param affiliatedSocieties 关联学会列表（可空）
/// @param lastSyncedAt 最后同步时间（可空）
/// @param createdAt 创建时间
/// @param updatedAt 更新时间
public record VenueDetailResponse(
    Long id,
    String venueType,
    String title,
    String titleZh,
    String issnL,
    String nlmId,
    String openalexId,
    String abbreviatedTitle,
    String primaryLanguage,
    String countryCode,
    String provenanceCode,
    PublicationProfileDto publicationProfile,
    CitationMetricsDto citationMetrics,
    OpenAccessDto openAccess,
    List<SocietyDto> affiliatedSocieties,
    Instant lastSyncedAt,
    Instant createdAt,
    Instant updatedAt) {

  /// 引用指标 DTO。
  ///
  /// @param worksCount 发表作品总数
  /// @param citedByCount 被引用总次数
  /// @param hIndex H 指数（可选）
  /// @param i10Index i10 指数（引用次数 ≥10 的论文数，可选）
  /// @param twoYearMeanCitedness 两年平均被引次数（可选）
  public record CitationMetricsDto(
      Integer worksCount,
      Integer citedByCount,
      Integer hIndex,
      Integer i10Index,
      BigDecimal twoYearMeanCitedness) {}

  /// 出版概况 DTO。
  ///
  /// 白名单策略：仅暴露前端需要的字段，extData 不对外暴露。
  ///
  /// @param abbreviatedTitle 缩写标题
  /// @param alternateTitles 替代名称列表
  /// @param homepageUrl 主页 URL
  /// @param frequency 出版频率
  /// @param publicationHistory 出版历史
  /// @param languages 语言信息
  /// @param hostOrganization 宿主机构信息
  /// @param countryCode 国家代码
  /// @param indexingInfo MEDLINE 索引收录信息
  public record PublicationProfileDto(
      String abbreviatedTitle,
      List<String> alternateTitles,
      String homepageUrl,
      String frequency,
      PublicationHistoryDto publicationHistory,
      VenueLanguagesDto languages,
      HostOrganizationDto hostOrganization,
      String countryCode,
      IndexingInfoDto indexingInfo) {

    /// 出版历史 DTO。
    ///
    /// @param startYear 创刊年份
    /// @param endYear 停刊年份（可选）
    /// @param ceased 是否已停刊
    public record PublicationHistoryDto(Integer startYear, Integer endYear, boolean ceased) {}

    /// 语言信息 DTO。
    ///
    /// @param primary 主语言列表
    /// @param summary 摘要语言列表
    public record VenueLanguagesDto(List<String> primary, List<String> summary) {}

    /// 宿主机构 DTO。
    ///
    /// @param id 机构 ID
    /// @param name 机构名称
    public record HostOrganizationDto(String id, String name) {}

    /// 索引信息 DTO。
    ///
    /// @param status 索引状态
    /// @param medlineTa MEDLINE Title Abbreviation
    /// @param isoAbbreviation ISO 缩写标题
    public record IndexingInfoDto(String status, String medlineTa, String isoAbbreviation) {}
  }

  /// 开放获取信息 DTO。
  ///
  /// @param isOa 是否为开放获取
  /// @param apcUsd APC 美元价格（可选）
  /// @param oaType OA 类型（gold/green/hybrid/bronze/diamond）
  public record OpenAccessDto(boolean isOa, Integer apcUsd, String oaType) {}

  /// 关联学会 DTO。
  ///
  /// @param url 学会主页 URL
  /// @param organization 学会/组织名称
  public record SocietyDto(String url, String organization) {}
}

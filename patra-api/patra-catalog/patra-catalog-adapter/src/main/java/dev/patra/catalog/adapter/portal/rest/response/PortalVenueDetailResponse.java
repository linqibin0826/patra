package dev.patra.catalog.adapter.portal.rest.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;

/// Portal 期刊详情响应 DTO。
///
/// 字段名与类型直接对齐前端 `VenueDetail`，前端零字段映射。
///
/// @param id 期刊主键（String，避免 JS 超 2^53 精度损失）
/// @param title 期刊全称
/// @param abbreviatedTitle 缩写标题
/// @param venueType 载体类型
/// @param issnL Linking ISSN
/// @param countryCode 国家编码
/// @param primaryLanguage 主要语言
/// @param foundedYear 创刊年份
/// @param coverObjectKey 封面对象键
/// @param homepageUrl 官网链接（当前恒为 null，待接入元数据后填充）
/// @param isOpenAccess 是否开放获取
/// @param impactFactor 最新年影响因子
/// @param jcrQuartile JCR JIF 分区
/// @param jcrSubject JCR 学科
/// @param casMajorCategory CAS 大类
/// @param casMajorQuartile CAS 大类分区
/// @param casIsTop 是否 Top 期刊
/// @param citeScore 最新年 CiteScore
/// @param hIndex H 指数
/// @param citedByCount 总被引次数
/// @param worksCount 总发文量
/// @param frequency 出版频次
/// @param medlineIndexed 是否 Medline 收录
/// @param oaType OA 类型
/// @param apcUsd 文章处理费（美元）
/// @param isInDoaj 是否收录于 DOAJ
/// @param jcrRatings JCR 年度评级列表
/// @param casRatings CAS 年度评级列表
/// @param scopusRatings Scopus 年度评级列表
/// @param yearlyStats 年度发文量统计列表
/// @param identifiers 期刊标识符列表
/// @author linqibin
/// @since 0.1.0
@Builder
public record PortalVenueDetailResponse(
    String id,
    String title,
    String abbreviatedTitle,
    String venueType,
    String issnL,
    String countryCode,
    String primaryLanguage,
    Integer foundedYear,
    String coverObjectKey,
    String homepageUrl,
    Boolean isOpenAccess,
    BigDecimal impactFactor,
    String jcrQuartile,
    String jcrSubject,
    String casMajorCategory,
    String casMajorQuartile,
    Boolean casIsTop,
    BigDecimal citeScore,
    Integer hIndex,
    Integer citedByCount,
    Integer worksCount,
    String frequency,
    Boolean medlineIndexed,
    String oaType,
    Integer apcUsd,
    Boolean isInDoaj,
    List<JcrRating> jcrRatings,
    List<CasRating> casRatings,
    List<ScopusRating> scopusRatings,
    List<YearlyStat> yearlyStats,
    List<Identifier> identifiers) {

  public PortalVenueDetailResponse {
    jcrRatings = jcrRatings != null ? List.copyOf(jcrRatings) : List.of();
    casRatings = casRatings != null ? List.copyOf(casRatings) : List.of();
    scopusRatings = scopusRatings != null ? List.copyOf(scopusRatings) : List.of();
    yearlyStats = yearlyStats != null ? List.copyOf(yearlyStats) : List.of();
    identifiers = identifiers != null ? List.copyOf(identifiers) : List.of();
  }

  /// JCR 年度评级响应。
  ///
  /// @param year 评级年份
  /// @param impactFactor 影响因子
  /// @param quartile JIF 分区
  /// @param subject JIF 学科分类
  /// @param jifRank JIF 排名
  /// @param jifPercentile JIF 百分位
  @Builder
  public record JcrRating(
      int year,
      BigDecimal impactFactor,
      String quartile,
      String subject,
      String jifRank,
      BigDecimal jifPercentile) {}

  /// CAS 年度评级响应。
  ///
  /// @param year 评级年份
  /// @param edition 版本
  /// @param majorCategory 大类
  /// @param majorQuartile 大类分区
  /// @param minorSubject 小类学科
  /// @param minorQuartile 小类分区
  /// @param isTop 是否 Top 期刊
  /// @param isReview 是否综述期刊
  @Builder
  public record CasRating(
      int year,
      String edition,
      String majorCategory,
      String majorQuartile,
      String minorSubject,
      String minorQuartile,
      Boolean isTop,
      Boolean isReview) {}

  /// Scopus 年度评级响应。
  ///
  /// @param year 评级年份
  /// @param citeScore CiteScore 值
  /// @param sjr SJR 值
  /// @param snip SNIP 值
  /// @param quartile 分区
  /// @param percentile 百分位
  @Builder
  public record ScopusRating(
      int year,
      BigDecimal citeScore,
      BigDecimal sjr,
      BigDecimal snip,
      String quartile,
      BigDecimal percentile) {}

  /// 年度发文量统计响应。
  ///
  /// @param year 统计年份
  /// @param worksCount 发文量
  /// @param citedByCount 被引次数
  /// @param oaWorksCount OA 发文量
  public record YearlyStat(
      int year, Integer worksCount, Integer citedByCount, Integer oaWorksCount) {

    /// 创建年度统计响应。
    ///
    /// @param year 统计年份
    /// @param worksCount 发文量
    /// @param citedByCount 被引次数
    /// @param oaWorksCount OA 发文量
    /// @return 年度统计响应
    public static YearlyStat of(
        int year, Integer worksCount, Integer citedByCount, Integer oaWorksCount) {
      return new YearlyStat(year, worksCount, citedByCount, oaWorksCount);
    }
  }

  /// 期刊标识符响应。
  ///
  /// @param type 标识符类型
  /// @param value 标识符值
  /// @param primary 是否主标识符
  public record Identifier(String type, String value, boolean primary) {

    /// 创建标识符响应。
    ///
    /// @param type 标识符类型
    /// @param value 标识符值
    /// @param primary 是否主标识符
    /// @return 标识符响应
    public static Identifier of(String type, String value, boolean primary) {
      return new Identifier(type, value, primary);
    }
  }
}

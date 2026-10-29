package dev.patra.catalog.domain.portal.model.read;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;

/// 期刊详情读模型（CQRS 读端，一次返回完整数据集，含深数据层）。
///
/// @author linqibin
/// @since 0.1.0
@Builder
public record VenueDetailReadModel(
    Long id,
    String title,
    String abbreviatedTitle,
    String venueType,
    String issnL,
    String countryCode,
    String primaryLanguage,
    Integer foundedYear,
    String coverObjectKey,
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
    List<JcrRatingView> jcrRatings,
    List<CasRatingView> casRatings,
    List<ScopusRatingView> scopusRatings,
    List<YearlyStatView> yearlyStats,
    List<IdentifierView> identifiers) {

  public VenueDetailReadModel {
    jcrRatings = jcrRatings != null ? List.copyOf(jcrRatings) : List.of();
    casRatings = casRatings != null ? List.copyOf(casRatings) : List.of();
    scopusRatings = scopusRatings != null ? List.copyOf(scopusRatings) : List.of();
    yearlyStats = yearlyStats != null ? List.copyOf(yearlyStats) : List.of();
    identifiers = identifiers != null ? List.copyOf(identifiers) : List.of();
  }

  /// JCR 年度评级视图。
  ///
  /// @param year 评级年份
  /// @param impactFactor 影响因子
  /// @param quartile JIF 分区
  /// @param subject JIF 学科分类
  /// @param jifRank JIF 排名
  /// @param jifPercentile JIF 百分位
  @Builder
  public record JcrRatingView(
      int year,
      BigDecimal impactFactor,
      String quartile,
      String subject,
      String jifRank,
      BigDecimal jifPercentile) {}

  /// CAS 年度评级视图。
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
  public record CasRatingView(
      int year,
      String edition,
      String majorCategory,
      String majorQuartile,
      String minorSubject,
      String minorQuartile,
      Boolean isTop,
      Boolean isReview) {}

  /// Scopus 年度评级视图。
  ///
  /// @param year 评级年份
  /// @param citeScore CiteScore 值
  /// @param sjr SJR 值
  /// @param snip SNIP 值
  /// @param quartile 分区
  /// @param percentile 百分位
  @Builder
  public record ScopusRatingView(
      int year,
      BigDecimal citeScore,
      BigDecimal sjr,
      BigDecimal snip,
      String quartile,
      BigDecimal percentile) {}

  /// 年度发文量统计视图。
  ///
  /// @param year 统计年份
  /// @param worksCount 发文量
  /// @param citedByCount 被引次数
  /// @param oaWorksCount OA 发文量
  public record YearlyStatView(
      int year, Integer worksCount, Integer citedByCount, Integer oaWorksCount) {

    /// 创建年度统计视图。
    ///
    /// @param year 统计年份
    /// @param worksCount 发文量
    /// @param citedByCount 被引次数
    /// @param oaWorksCount OA 发文量
    /// @return 年度统计视图
    public static YearlyStatView of(
        int year, Integer worksCount, Integer citedByCount, Integer oaWorksCount) {
      return new YearlyStatView(year, worksCount, citedByCount, oaWorksCount);
    }
  }

  /// 期刊标识符视图。
  ///
  /// @param type 标识符类型
  /// @param value 标识符值
  /// @param primary 是否主标识符
  public record IdentifierView(String type, String value, boolean primary) {

    /// 创建标识符视图。
    ///
    /// @param type 标识符类型
    /// @param value 标识符值
    /// @param primary 是否主标识符
    /// @return 标识符视图
    public static IdentifierView of(String type, String value, boolean primary) {
      return new IdentifierView(type, value, primary);
    }
  }
}

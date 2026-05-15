package dev.linqibin.patra.catalog.domain.model.vo.venue;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/// 引用指标值对象。封装期刊的引用相关统计指标快照。
///
/// **设计原则**：
///
/// - 不可变性：Record 自动提供
/// - 时点快照：表示某一时刻的累计统计数据
/// - 语义化命名：聚焦引用指标，与 PublicationProfile（出版概况）、OpenAccessInfo（OA 信息）职责分离
/// - 与年度指标区分：CitationMetrics 是当前快照，VenuePublicationStats 是年度时序数据
///
/// **包含的数据**：
///
/// | 指标 | 字段 | 说明 |
/// |------|------|------|
/// | 作品数 | worksCount | 发表作品总数 |
/// | 被引数 | citedByCount | 被引用总次数 |
/// | H 指数 | hIndex | Hirsch 指数 |
/// | i10 指数 | i10Index | 引用次数 ≥10 的论文数 |
/// | 两年平均被引 | twoYearMeanCitedness | 近两年文章的平均被引次数 |
///
/// **使用示例**：
///
/// ```java
/// // 创建完整引用指标
/// CitationMetrics metrics = CitationMetrics.of(150000, 2500000, 285, 1200, new
// BigDecimal("3.45"));
///
/// // 创建基础引用指标（无高级指标）
/// CitationMetrics basic = CitationMetrics.ofBasic(150000, 2500000);
///
/// // 计算平均被引
/// BigDecimal avgCitations = metrics.calculateAverageCitations();
/// ```
///
/// @param worksCount 发表作品总数
/// @param citedByCount 被引用总次数
/// @param hIndex H 指数（可选）
/// @param i10Index i10 指数（引用次数 ≥10 的论文数，可选）
/// @param twoYearMeanCitedness 两年平均被引次数（可选）
/// @author linqibin
/// @since 0.7.0
public record CitationMetrics(
    Integer worksCount,
    Integer citedByCount,
    Integer hIndex,
    Integer i10Index,
    BigDecimal twoYearMeanCitedness)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 创建完整引用指标。
  ///
  /// @param worksCount 发表作品总数
  /// @param citedByCount 被引用总次数
  /// @param hIndex H 指数
  /// @param i10Index i10 指数
  /// @param twoYearMeanCitedness 两年平均被引次数
  /// @return 引用指标值对象
  public static CitationMetrics of(
      Integer worksCount,
      Integer citedByCount,
      Integer hIndex,
      Integer i10Index,
      BigDecimal twoYearMeanCitedness) {
    return new CitationMetrics(worksCount, citedByCount, hIndex, i10Index, twoYearMeanCitedness);
  }

  /// 创建基础引用指标（无高级指标）。
  ///
  /// @param worksCount 发表作品总数
  /// @param citedByCount 被引用总次数
  /// @return 引用指标值对象
  public static CitationMetrics ofBasic(Integer worksCount, Integer citedByCount) {
    return new CitationMetrics(worksCount, citedByCount, null, null, null);
  }

  /// 创建空引用指标。
  ///
  /// @return 空引用指标值对象
  public static CitationMetrics empty() {
    return new CitationMetrics(0, 0, null, null, null);
  }

  /// 判断是否有 H 指数。
  ///
  /// @return true 如果有 H 指数
  public boolean hasHIndex() {
    return hIndex != null;
  }

  /// 判断是否有 i10 指数。
  ///
  /// @return true 如果有 i10 指数
  public boolean hasI10Index() {
    return i10Index != null;
  }

  /// 判断是否有两年平均被引次数。
  ///
  /// @return true 如果有两年平均被引次数
  public boolean hasTwoYearMeanCitedness() {
    return twoYearMeanCitedness != null;
  }

  /// 计算平均每篇被引次数。
  ///
  /// @return 平均被引次数，如果无作品则返回 0
  public BigDecimal calculateAverageCitations() {
    if (worksCount == null || worksCount == 0 || citedByCount == null) {
      return BigDecimal.ZERO;
    }
    return BigDecimal.valueOf(citedByCount)
        .divide(BigDecimal.valueOf(worksCount), 2, RoundingMode.HALF_UP);
  }
}

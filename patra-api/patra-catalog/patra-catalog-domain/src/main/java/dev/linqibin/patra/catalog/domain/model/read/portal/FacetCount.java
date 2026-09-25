package dev.linqibin.patra.catalog.domain.model.read.portal;

/// Portal facet 单项：维度值及其命中数量（期刊浏览与文献检索共用）。
///
/// @param value 维度值（如 "Q1"、"2026"、"Journal Article"、"en"）
/// @param count 该维度值下的命中数量
/// @author linqibin
/// @since 0.1.0
public record FacetCount(String value, long count) {

  /// 创建 [FacetCount] 实例。
  ///
  /// @param value 维度值
  /// @param count 命中数量
  /// @return 新建的 [FacetCount]
  public static FacetCount of(String value, long count) {
    return new FacetCount(value, count);
  }
}

package dev.linqibin.patra.catalog.adapter.rest.portal.response;

/// facet 单项响应 DTO，期刊与文献 facets 共用。
///
/// @param value 维度值
/// @param count 该维度值下的命中数量
/// @author linqibin
/// @since 0.1.0
public record FacetCountResponse(String value, long count) {

  /// 创建 [FacetCountResponse] 实例。
  ///
  /// @param value 维度值
  /// @param count 命中数量
  /// @return 新建的 [FacetCountResponse]
  public static FacetCountResponse of(String value, long count) {
    return new FacetCountResponse(value, count);
  }
}

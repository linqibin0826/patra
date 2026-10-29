package dev.patra.catalog.app.portal.usecase.query;

/// Portal 文献流查询参数（未归一化的外部输入）。
///
/// @param tab 排序维度字符串（recent/cited，可空 → 默认 recent）
/// @param page 页码（可空，由服务归一化）
/// @param pageSize 每页大小（可空，由服务归一化）
/// @author linqibin
/// @since 0.1.0
public record PortalFeedQuery(String tab, Integer page, Integer pageSize) {

  /// 创建查询参数。
  ///
  /// @param tab 排序维度字符串
  /// @param page 页码
  /// @param pageSize 每页大小
  /// @return 查询参数
  public static PortalFeedQuery of(String tab, Integer page, Integer pageSize) {
    return new PortalFeedQuery(tab, page, pageSize);
  }
}

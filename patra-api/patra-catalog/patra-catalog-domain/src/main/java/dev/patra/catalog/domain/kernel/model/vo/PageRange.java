package dev.patra.catalog.domain.kernel.model.vo;

/// 一段页码：首页与尾页（尾页可空）。尾页已按首页补齐高位（`1451-61` → `1461`）。
///
/// @param first 首页，如 `1451`、`S1`、`e0123456`
/// @param last 尾页，可空
/// @author linqibin
/// @since 0.1.0
public record PageRange(String first, String last) {

  /// 创建页码段。
  ///
  /// @param first 首页
  /// @param last 尾页，可空
  /// @return 页码段
  public static PageRange of(String first, String last) {
    return new PageRange(first, last);
  }
}

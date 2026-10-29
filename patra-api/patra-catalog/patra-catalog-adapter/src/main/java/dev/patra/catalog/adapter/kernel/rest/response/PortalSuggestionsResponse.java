package dev.patra.catalog.adapter.kernel.rest.response;

import java.util.List;

/// 建议接口的响应信封：`{ "items": [...] }`，没有命中时 `items` 为空数组。
///
/// @param items 候选列表
/// @param <T> 候选类型
/// @author linqibin
/// @since 0.1.0
public record PortalSuggestionsResponse<T>(List<T> items) {

  /// 紧凑构造器：防御性拷贝，`null` 视为空列表。
  public PortalSuggestionsResponse {
    items = items != null ? List.copyOf(items) : List.of();
  }

  /// 创建响应信封。
  ///
  /// @param items 候选列表
  /// @param <T> 候选类型
  /// @return 响应信封
  public static <T> PortalSuggestionsResponse<T> of(List<T> items) {
    return new PortalSuggestionsResponse<>(items);
  }
}

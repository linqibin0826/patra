package dev.patra.interaction.adapter.collection.rest.response;

import java.util.List;

/// 批量「我的状态」：按去重后的请求顺序，每个 key 一行。
///
/// @param items 状态
public record StatusResponse(List<ItemStatusResponse> items) {

  /// 防御性拷贝。
  public StatusResponse {
    items = List.copyOf(items);
  }

  /// 创建。
  ///
  /// @param items 状态
  /// @return 响应
  public static StatusResponse of(List<ItemStatusResponse> items) {
    return new StatusResponse(items);
  }
}

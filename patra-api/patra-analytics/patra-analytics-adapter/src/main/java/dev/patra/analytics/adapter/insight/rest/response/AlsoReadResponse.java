package dev.patra.analytics.adapter.insight.rest.response;

import java.util.List;

/// 看过这篇的人还看了：最多 6 篇，顺序就是展示顺序。
///
/// @param items 文献
public record AlsoReadResponse(List<AlsoReadItemResponse> items) {

  /// 防御性拷贝。
  public AlsoReadResponse {
    items = List.copyOf(items);
  }

  /// 构造。
  ///
  /// @param items 文献
  /// @return 响应
  public static AlsoReadResponse of(List<AlsoReadItemResponse> items) {
    return new AlsoReadResponse(items);
  }
}

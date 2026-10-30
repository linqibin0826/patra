package dev.patra.interaction.adapter.subject.rest.response;

import java.util.List;

/// 批量对象统计：按去重后的请求顺序，每个 key 一行。
///
/// @param items 计数
public record SubjectStatsResponse(List<SubjectStatsItemResponse> items) {

  /// 防御性拷贝。
  public SubjectStatsResponse {
    items = List.copyOf(items);
  }

  /// 创建。
  ///
  /// @param items 计数
  /// @return 响应
  public static SubjectStatsResponse of(List<SubjectStatsItemResponse> items) {
    return new SubjectStatsResponse(items);
  }
}

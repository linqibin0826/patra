package dev.patra.interaction.adapter.subject.rest.response;

import dev.patra.interaction.app.subject.usecase.query.SubjectStatsView;

/// 批量对象统计里的一个 key。
///
/// @param key 请求里的 key
/// @param collectorCount 收藏人数
/// @param discussionCount 讨论数
public record SubjectStatsItemResponse(String key, int collectorCount, int discussionCount) {

  /// 从查询结果转换。
  ///
  /// @param view 查询结果
  /// @return 响应
  public static SubjectStatsItemResponse from(SubjectStatsView view) {
    return new SubjectStatsItemResponse(view.key(), view.collectorCount(), view.discussionCount());
  }
}

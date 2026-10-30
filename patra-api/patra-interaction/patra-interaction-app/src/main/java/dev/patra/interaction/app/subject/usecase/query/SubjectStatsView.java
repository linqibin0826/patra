package dev.patra.interaction.app.subject.usecase.query;

/// 批量对象统计里的一个 key。
///
/// @param key 请求里的 key
/// @param collectorCount 收藏人数
/// @param discussionCount 讨论数
public record SubjectStatsView(String key, int collectorCount, int discussionCount) {

  /// 创建。
  ///
  /// @param key 请求里的 key
  /// @param collectorCount 收藏人数
  /// @param discussionCount 讨论数
  /// @return 视图
  public static SubjectStatsView of(String key, int collectorCount, int discussionCount) {
    return new SubjectStatsView(key, collectorCount, discussionCount);
  }
}

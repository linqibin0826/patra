package dev.patra.interaction.domain.subject.model.read;

/// 一个对象的公开计数（文献卡片用）：收藏人数来自收藏子域，讨论数来自讨论子域。
///
/// @param collectorCount 收藏人数（文献库条目数）
/// @param discussionCount 讨论数（未删除的评论总数，含楼内回复）
public record SubjectStats(int collectorCount, int discussionCount) {

  /// 两项都是 0：没有统计行的对象。
  public static final SubjectStats ZERO = new SubjectStats(0, 0);

  /// 创建。
  ///
  /// @param collectorCount 收藏人数
  /// @param discussionCount 讨论数
  /// @return 计数
  public static SubjectStats of(int collectorCount, int discussionCount) {
    return new SubjectStats(collectorCount, discussionCount);
  }
}

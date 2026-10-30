package dev.patra.interaction.domain.post.model.read;

/// 一条经验的公开计数与访客状态。
///
/// @param helpfulCount 有帮助数
/// @param replyCount 经验下未删除的评论总数
/// @param helpful 访客点过有帮助没有；没有访客时为 `null`
public record PostStats(int helpfulCount, int replyCount, Boolean helpful) {

  /// 创建。
  ///
  /// @param helpfulCount 有帮助数
  /// @param replyCount 回复数
  /// @param helpful 我点过没有，可空
  /// @return 统计
  public static PostStats of(int helpfulCount, int replyCount, Boolean helpful) {
    return new PostStats(helpfulCount, replyCount, helpful);
  }
}

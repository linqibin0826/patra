package dev.patra.interaction.domain.discussion.model.read;

import java.time.Instant;

/// 一楼的统计（`discussion.thread_stats`）：未删除的回复数与其中最新一条的发表时刻。
///
/// @param replyCount 未删除的回复数（任意深度）
/// @param lastReplyAt 最新一条未删除回复的发表时刻；没有时为 `null`
public record ThreadStats(int replyCount, Instant lastReplyAt) {

  /// 创建。
  ///
  /// @param replyCount 回复数
  /// @param lastReplyAt 最后回复时刻，可空
  /// @return 统计
  public static ThreadStats of(int replyCount, Instant lastReplyAt) {
    return new ThreadStats(replyCount, lastReplyAt);
  }
}

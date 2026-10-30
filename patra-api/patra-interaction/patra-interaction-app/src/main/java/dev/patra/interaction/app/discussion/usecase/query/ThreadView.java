package dev.patra.interaction.app.discussion.usecase.query;

import java.time.Instant;
import java.util.List;
import lombok.Builder;

/// 一楼：根评论加楼内回复（平铺、正序），带这一楼的统计（根评论是占位时照样带）。
///
/// @param root 根评论（可能是占位）
/// @param replies 列表里是前 3 条回复，单楼视图里是全部
/// @param hasMoreReplies 还有没给出的回复（不给条数）
/// @param repliesCursor 取剩下回复的游标；没有更多时为 `null`
/// @param replyCount 这一楼未删除的回复数
/// @param lastReplyAt 最新一条未删除回复的发表时刻；没有回复时为 `null`
@Builder
public record ThreadView(
    CommentView root,
    List<CommentView> replies,
    boolean hasMoreReplies,
    String repliesCursor,
    int replyCount,
    Instant lastReplyAt) {

  /// 防御性拷贝。
  public ThreadView {
    replies = List.copyOf(replies);
  }
}

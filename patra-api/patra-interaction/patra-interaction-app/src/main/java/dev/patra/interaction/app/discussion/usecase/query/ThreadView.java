package dev.patra.interaction.app.discussion.usecase.query;

import java.util.List;

/// 一楼：根评论加楼内回复（平铺、正序）。
///
/// @param root 根评论（可能是占位）
/// @param replies 列表里是前 3 条回复，单楼视图里是全部
/// @param hasMoreReplies 还有没给出的回复（不给条数）
/// @param repliesCursor 取剩下回复的游标；没有更多时为 `null`
public record ThreadView(
    CommentView root, List<CommentView> replies, boolean hasMoreReplies, String repliesCursor) {

  /// 防御性拷贝。
  public ThreadView {
    replies = List.copyOf(replies);
  }

  /// 创建。
  ///
  /// @param root 根评论
  /// @param replies 回复
  /// @param hasMoreReplies 是否还有
  /// @param repliesCursor 游标，可空
  /// @return 一楼
  public static ThreadView of(
      CommentView root, List<CommentView> replies, boolean hasMoreReplies, String repliesCursor) {
    return new ThreadView(root, replies, hasMoreReplies, repliesCursor);
  }
}

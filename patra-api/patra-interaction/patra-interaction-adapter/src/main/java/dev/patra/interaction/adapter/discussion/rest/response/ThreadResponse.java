package dev.patra.interaction.adapter.discussion.rest.response;

import dev.patra.interaction.app.discussion.usecase.query.ThreadView;
import java.util.List;

/// 一楼：根评论加楼内回复。
///
/// @param root 根评论
/// @param replies 列表里是前 3 条回复，单楼视图里是全部
/// @param hasMoreReplies 还有没给出的回复（不给条数）
/// @param repliesCursor 交给 `/comments/{rootId}/replies` 取剩下回复的游标；没有更多时为 `null`
public record ThreadResponse(
    CommentResponse root,
    List<CommentResponse> replies,
    boolean hasMoreReplies,
    String repliesCursor) {

  /// 防御性拷贝。
  public ThreadResponse {
    replies = List.copyOf(replies);
  }

  /// 从视图转换。
  ///
  /// @param view 视图
  /// @return 响应
  public static ThreadResponse from(ThreadView view) {
    return new ThreadResponse(
        CommentResponse.from(view.root()),
        CommentResponse.fromAll(view.replies()),
        view.hasMoreReplies(),
        view.repliesCursor());
  }
}

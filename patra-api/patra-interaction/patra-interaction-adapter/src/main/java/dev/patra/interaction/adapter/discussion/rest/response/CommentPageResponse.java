package dev.patra.interaction.adapter.discussion.rest.response;

import dev.patra.interaction.app.discussion.usecase.query.CommentPageView;
import java.util.List;

/// 楼内回复的一页。
///
/// @param items 回复（正序）
/// @param nextCursor 下一页的游标；没有下一页为 `null`
public record CommentPageResponse(List<CommentResponse> items, String nextCursor) {

  /// 防御性拷贝。
  public CommentPageResponse {
    items = List.copyOf(items);
  }

  /// 从视图转换。
  ///
  /// @param view 视图
  /// @return 响应
  public static CommentPageResponse from(CommentPageView view) {
    return new CommentPageResponse(CommentResponse.fromAll(view.items()), view.nextCursor());
  }
}

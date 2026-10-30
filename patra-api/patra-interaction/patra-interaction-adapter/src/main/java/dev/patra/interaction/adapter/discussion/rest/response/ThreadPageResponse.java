package dev.patra.interaction.adapter.discussion.rest.response;

import dev.patra.interaction.app.discussion.usecase.query.ThreadPageView;
import java.util.List;

/// 讨论列表的一页。
///
/// @param items 楼
/// @param nextCursor 下一页的游标；没有下一页为 `null`
/// @param total 对象下未删除的评论总数（根评论与回复，标题条数用，不受分页影响）
public record ThreadPageResponse(List<ThreadResponse> items, String nextCursor, int total) {

  /// 防御性拷贝。
  public ThreadPageResponse {
    items = List.copyOf(items);
  }

  /// 从视图转换。
  ///
  /// @param view 视图
  /// @return 响应
  public static ThreadPageResponse from(ThreadPageView view) {
    return new ThreadPageResponse(
        view.items().stream().map(ThreadResponse::from).toList(), view.nextCursor(), view.total());
  }
}

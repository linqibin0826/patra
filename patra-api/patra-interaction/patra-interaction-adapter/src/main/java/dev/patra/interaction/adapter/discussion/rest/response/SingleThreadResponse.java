package dev.patra.interaction.adapter.discussion.rest.response;

import dev.patra.interaction.app.discussion.usecase.query.SingleThreadView;

/// 单楼视图。
///
/// @param targetId 目标评论
/// @param thread 目标所在的整楼，回复全部给出
public record SingleThreadResponse(Long targetId, ThreadResponse thread) {

  /// 从视图转换。
  ///
  /// @param view 视图
  /// @return 响应
  public static SingleThreadResponse from(SingleThreadView view) {
    return new SingleThreadResponse(view.targetId(), ThreadResponse.from(view.thread()));
  }
}

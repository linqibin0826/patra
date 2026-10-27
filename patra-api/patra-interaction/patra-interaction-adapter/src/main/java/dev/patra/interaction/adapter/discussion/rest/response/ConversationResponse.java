package dev.patra.interaction.adapter.discussion.rest.response;

import dev.patra.interaction.app.discussion.usecase.query.ConversationView;
import java.util.List;

/// 对话链：从根评论到目标（含占位）。
///
/// @param targetId 目标评论
/// @param items 根在前、目标在最后
public record ConversationResponse(Long targetId, List<CommentResponse> items) {

  /// 防御性拷贝。
  public ConversationResponse {
    items = List.copyOf(items);
  }

  /// 从视图转换。
  ///
  /// @param view 视图
  /// @return 响应
  public static ConversationResponse from(ConversationView view) {
    return new ConversationResponse(view.targetId(), CommentResponse.fromAll(view.items()));
  }
}

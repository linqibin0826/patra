package dev.patra.interaction.app.discussion.usecase.query;

import java.util.List;

/// 对话链：从根评论到目标的祖先链（含占位）。
///
/// @param targetId 目标评论
/// @param items 根在前、目标在最后
public record ConversationView(long targetId, List<CommentView> items) {

  /// 防御性拷贝。
  public ConversationView {
    items = List.copyOf(items);
  }

  /// 创建。
  ///
  /// @param targetId 目标
  /// @param items 链
  /// @return 对话链
  public static ConversationView of(long targetId, List<CommentView> items) {
    return new ConversationView(targetId, items);
  }
}

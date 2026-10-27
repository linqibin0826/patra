package dev.patra.interaction.app.discussion.usecase.comment;

import dev.patra.interaction.app.discussion.usecase.query.CommentView;
import dev.patra.tool.cqrs.Command;

/// 编辑自己的评论。
///
/// @param commentId 评论
/// @param body 新正文原文（`@用户名` 形式）
public record EditCommentCommand(long commentId, String body) implements Command<CommentView> {

  /// 创建命令。
  ///
  /// @param commentId 评论
  /// @param body 新正文
  /// @return 命令
  public static EditCommentCommand of(long commentId, String body) {
    return new EditCommentCommand(commentId, body);
  }
}

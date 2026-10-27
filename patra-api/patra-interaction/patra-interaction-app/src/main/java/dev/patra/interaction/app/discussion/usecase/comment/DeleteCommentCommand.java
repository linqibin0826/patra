package dev.patra.interaction.app.discussion.usecase.comment;

import dev.patra.tool.cqrs.Command;

/// 删除自己的评论。
///
/// @param commentId 评论
public record DeleteCommentCommand(long commentId) implements Command<DeleteResult> {

  /// 创建命令。
  ///
  /// @param commentId 评论
  /// @return 命令
  public static DeleteCommentCommand of(long commentId) {
    return new DeleteCommentCommand(commentId);
  }
}

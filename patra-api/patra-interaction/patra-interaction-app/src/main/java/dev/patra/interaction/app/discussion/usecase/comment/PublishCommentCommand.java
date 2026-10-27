package dev.patra.interaction.app.discussion.usecase.comment;

import dev.patra.interaction.app.discussion.usecase.query.CommentView;
import dev.patra.tool.cqrs.Command;

/// 在一个对象下发表根评论或回复。
///
/// @param type 对外类型名（`publication` / `post`）
/// @param key 对外 key
/// @param parentId 回复的是哪一条；根评论为 `null`
/// @param body 正文原文（`@用户名` 形式）
public record PublishCommentCommand(String type, String key, Long parentId, String body)
    implements Command<CommentView> {

  /// 创建命令。
  ///
  /// @param type 对外类型名
  /// @param key 对外 key
  /// @param parentId 父评论，可空
  /// @param body 正文
  /// @return 命令
  public static PublishCommentCommand of(String type, String key, Long parentId, String body) {
    return new PublishCommentCommand(type, key, parentId, body);
  }
}

package dev.patra.analytics.app.history.usecase.delete;

import dev.patra.tool.cqrs.Command;

/// 当前用户从阅读历史里删掉一篇。
///
/// @param articleId 文献 ID，正整数
public record DeleteReadingEntryCommand(long articleId) implements Command<Void> {

  /// 文献 ID 必须是正整数（接口层另有 `@Positive`，HTTP 上返回 422）。
  public DeleteReadingEntryCommand {
    if (articleId <= 0) {
      throw new IllegalArgumentException("articleId 必须是正整数: " + articleId);
    }
  }

  /// 构造。
  ///
  /// @param articleId 文献 ID
  /// @return 命令
  public static DeleteReadingEntryCommand of(long articleId) {
    return new DeleteReadingEntryCommand(articleId);
  }
}

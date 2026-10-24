package dev.patra.interaction.app.collection.usecase.cleanup;

import dev.patra.tool.cqrs.Command;

/// 每日注销清理：分批回查有收藏数据的用户，物理删除已注销用户的收藏与收藏夹。
public record CleanupDeletedUsersCommand() implements Command<CleanupResult> {

  /// 创建命令。
  ///
  /// @return 命令
  public static CleanupDeletedUsersCommand of() {
    return new CleanupDeletedUsersCommand();
  }
}

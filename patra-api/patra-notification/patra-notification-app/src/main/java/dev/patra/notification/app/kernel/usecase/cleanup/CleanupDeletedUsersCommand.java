package dev.patra.notification.app.kernel.usecase.cleanup;

import dev.patra.tool.cqrs.Command;

/// 每日注销清理：合并各子域有数据的用户，分批回查 identity，删除已注销用户在各子域登记的个人数据。
public record CleanupDeletedUsersCommand() implements Command<CleanupResult> {

  /// 创建命令。
  ///
  /// @return 命令
  public static CleanupDeletedUsersCommand of() {
    return new CleanupDeletedUsersCommand();
  }
}

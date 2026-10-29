package dev.patra.identity.app.account.usecase.deletion;

import dev.patra.tool.cqrs.Command;

/// 执行到期的注销：定时任务每小时发一次，无参数。
public record ExecuteDueAccountDeletionsCommand() implements Command<DeletionRunResult> {

  /// 创建命令。
  ///
  /// @return 命令
  public static ExecuteDueAccountDeletionsCommand of() {
    return new ExecuteDueAccountDeletionsCommand();
  }
}

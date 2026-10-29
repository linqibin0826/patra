package dev.patra.identity.app.account.usecase.deletion;

import dev.patra.tool.cqrs.Command;

/// 发到期前提醒：定时任务每小时发一次，无参数。
public record SendAccountDeletionRemindersCommand() implements Command<ReminderRunResult> {

  /// 创建命令。
  ///
  /// @return 命令
  public static SendAccountDeletionRemindersCommand of() {
    return new SendAccountDeletionRemindersCommand();
  }
}

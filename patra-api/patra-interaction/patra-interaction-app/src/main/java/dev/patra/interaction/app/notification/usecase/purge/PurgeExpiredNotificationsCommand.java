package dev.patra.interaction.app.notification.usecase.purge;

import dev.patra.tool.cqrs.Command;

/// 清理 5 个月前的提醒（每日任务 `notificationRetentionJob`），结果是删除的条数。
public record PurgeExpiredNotificationsCommand() implements Command<Integer> {

  /// 构造。
  ///
  /// @return 命令
  public static PurgeExpiredNotificationsCommand of() {
    return new PurgeExpiredNotificationsCommand();
  }
}

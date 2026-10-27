package dev.patra.interaction.app.notification.usecase.read;

import dev.patra.interaction.domain.notification.model.read.UnreadCounts;
import dev.patra.tool.cqrs.Command;

/// 把当前用户的未读提醒全部标为已读（两个标签一起）。
public record MarkAllNotificationsReadCommand() implements Command<UnreadCounts> {

  /// 构造。
  ///
  /// @return 命令
  public static MarkAllNotificationsReadCommand of() {
    return new MarkAllNotificationsReadCommand();
  }
}

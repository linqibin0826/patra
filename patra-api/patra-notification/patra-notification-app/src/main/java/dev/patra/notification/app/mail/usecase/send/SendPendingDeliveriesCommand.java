package dev.patra.notification.app.mail.usecase.send;

import dev.patra.tool.cqrs.Command;

/// 装配并发出到点的提醒邮件投递（spec 7.2、7.3）。
public record SendPendingDeliveriesCommand() implements Command<SendResult> {

  /// 创建命令。
  ///
  /// @return 命令
  public static SendPendingDeliveriesCommand of() {
    return new SendPendingDeliveriesCommand();
  }
}

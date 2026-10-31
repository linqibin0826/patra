package dev.patra.notification.app.mail.usecase.flush;

import dev.patra.tool.cqrs.Command;

/// 把到点的离站排队行处理掉：丢弃已读与看过的，到点的一类合成一条投递（spec 5.1）。
public record FlushDueRemindersCommand() implements Command<FlushResult> {

  /// 创建命令。
  ///
  /// @return 命令
  public static FlushDueRemindersCommand of() {
    return new FlushDueRemindersCommand();
  }
}

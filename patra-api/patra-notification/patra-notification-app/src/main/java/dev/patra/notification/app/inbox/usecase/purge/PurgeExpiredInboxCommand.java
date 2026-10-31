package dev.patra.notification.app.inbox.usecase.purge;

import dev.patra.tool.cqrs.Command;

/// 清理 5 个月前的信箱数据（每天一次的 XXL 任务）。
public record PurgeExpiredInboxCommand() implements Command<PurgeResult> {

  /// 构造。
  ///
  /// @return 命令
  public static PurgeExpiredInboxCommand of() {
    return new PurgeExpiredInboxCommand();
  }
}

package dev.patra.analytics.app.history.usecase.purge;

import dev.patra.tool.cqrs.Command;

/// 删掉过期的阅读历史与删除记录（每天一次）。
public record PurgeExpiredHistoryCommand() implements Command<PurgeResult> {

  /// 构造。
  ///
  /// @return 命令
  public static PurgeExpiredHistoryCommand of() {
    return new PurgeExpiredHistoryCommand();
  }
}

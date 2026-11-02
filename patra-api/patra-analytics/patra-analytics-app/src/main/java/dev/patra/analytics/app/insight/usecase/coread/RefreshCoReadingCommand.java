package dev.patra.analytics.app.insight.usecase.coread;

import dev.patra.tool.cqrs.Command;

/// 重算共现快照（每天一次的 XXL-Job 触发）。
public record RefreshCoReadingCommand() implements Command<RefreshCoReadingResult> {

  /// 构造。
  ///
  /// @return 命令
  public static RefreshCoReadingCommand of() {
    return new RefreshCoReadingCommand();
  }
}

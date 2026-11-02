package dev.patra.analytics.app.insight.usecase.hot;

import dev.patra.tool.cqrs.Command;

/// 重算热读快照（每小时一次的 XXL-Job 触发）。
public record RefreshHotReadingCommand() implements Command<RefreshHotReadingResult> {

  /// 构造。
  ///
  /// @return 命令
  public static RefreshHotReadingCommand of() {
    return new RefreshHotReadingCommand();
  }
}

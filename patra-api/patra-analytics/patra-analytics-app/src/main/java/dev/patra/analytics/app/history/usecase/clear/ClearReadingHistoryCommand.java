package dev.patra.analytics.app.history.usecase.clear;

import dev.patra.tool.cqrs.Command;

/// 当前用户清空自己的阅读历史。
public record ClearReadingHistoryCommand() implements Command<Void> {

  /// 构造。
  ///
  /// @return 命令
  public static ClearReadingHistoryCommand of() {
    return new ClearReadingHistoryCommand();
  }
}

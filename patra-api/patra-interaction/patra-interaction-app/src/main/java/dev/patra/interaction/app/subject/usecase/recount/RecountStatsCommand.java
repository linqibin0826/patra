package dev.patra.interaction.app.subject.usecase.recount;

import dev.patra.tool.cqrs.Command;

/// 手动触发的按明细重算：各子域登记的每项计数，找出与明细不一致的行并修正。
public record RecountStatsCommand() implements Command<RecountResult> {

  /// 创建命令。
  ///
  /// @return 命令
  public static RecountStatsCommand of() {
    return new RecountStatsCommand();
  }
}

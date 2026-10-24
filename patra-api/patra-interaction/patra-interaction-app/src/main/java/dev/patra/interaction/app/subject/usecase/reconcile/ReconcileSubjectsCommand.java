package dev.patra.interaction.app.subject.usecase.reconcile;

import dev.patra.tool.cqrs.Command;

/// 每日对账：按身份锚回查来源，刷新全部登记对象的来源 ID、状态与快照。
public record ReconcileSubjectsCommand() implements Command<ReconciliationResult> {

  /// 创建命令。
  ///
  /// @return 命令
  public static ReconcileSubjectsCommand of() {
    return new ReconcileSubjectsCommand();
  }
}

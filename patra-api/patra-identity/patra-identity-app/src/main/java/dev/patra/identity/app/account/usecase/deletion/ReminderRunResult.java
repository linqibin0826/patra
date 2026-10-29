package dev.patra.identity.app.account.usecase.deletion;

/// 一次提醒任务的结果。
///
/// @param sent 发出（被发信执行器接下）的提醒数
/// @param failed 失败的用户数（标记已退回，下次运行重发）
public record ReminderRunResult(int sent, int failed) {

  /// 创建结果。
  ///
  /// @param sent 发出的提醒数
  /// @param failed 失败的用户数
  /// @return 结果
  public static ReminderRunResult of(int sent, int failed) {
    return new ReminderRunResult(sent, failed);
  }
}

package dev.patra.identity.app.account.usecase.deletion;

/// 一次提醒任务的结果。
///
/// @param sent 登记了提醒的用户数（与提醒标记同一事务提交）
/// @param failed 失败的用户数（这个用户的事务已回滚，下次运行重试）
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

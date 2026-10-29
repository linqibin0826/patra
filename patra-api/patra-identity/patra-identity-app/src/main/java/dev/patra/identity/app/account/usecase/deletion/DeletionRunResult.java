package dev.patra.identity.app.account.usecase.deletion;

/// 一次到期执行的结果。
///
/// @param completed 完成注销的用户数
/// @param failed 失败的用户数（仍在冷静期，下次运行重试）
public record DeletionRunResult(int completed, int failed) {

  /// 创建结果。
  ///
  /// @param completed 完成注销的用户数
  /// @param failed 失败的用户数
  /// @return 结果
  public static DeletionRunResult of(int completed, int failed) {
    return new DeletionRunResult(completed, failed);
  }
}

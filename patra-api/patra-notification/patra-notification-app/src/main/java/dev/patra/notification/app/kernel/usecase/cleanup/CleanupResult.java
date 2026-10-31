package dev.patra.notification.app.kernel.usecase.cleanup;

/// 注销清理的计数。
///
/// @param scannedUsers 核对的用户数（含失败批次里的）
/// @param deletedUsers 清理掉的用户数
/// @param deletedRows 各子域一共删掉的行数
/// @param failedBatches 因 identity 不可用跳过的批次
public record CleanupResult(
    int scannedUsers, int deletedUsers, int deletedRows, int failedBatches) {

  /// 创建。
  ///
  /// @param scannedUsers 核对的用户数
  /// @param deletedUsers 清理掉的用户数
  /// @param deletedRows 删掉的行数
  /// @param failedBatches 失败批次
  /// @return 计数
  public static CleanupResult of(
      int scannedUsers, int deletedUsers, int deletedRows, int failedBatches) {
    return new CleanupResult(scannedUsers, deletedUsers, deletedRows, failedBatches);
  }
}

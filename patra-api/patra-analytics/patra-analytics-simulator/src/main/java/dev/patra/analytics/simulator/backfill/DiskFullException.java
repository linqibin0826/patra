package dev.patra.analytics.simulator.backfill;

/// ClickHouse 返回 `NOT_ENOUGH_SPACE`：磁盘到了红线（PAP-165 的保护），立即停止，不重试。
public class DiskFullException extends RuntimeException {

  /// 创建异常。
  ///
  /// @param message 说明
  public DiskFullException(String message) {
    super(message);
  }

  /// 创建异常。
  ///
  /// @param message 说明
  /// @param cause 原因
  public DiskFullException(String message, Throwable cause) {
    super(message, cause);
  }
}

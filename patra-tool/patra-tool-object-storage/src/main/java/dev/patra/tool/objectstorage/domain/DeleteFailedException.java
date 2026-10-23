package dev.patra.tool.objectstorage.domain;

/// 表示删除对象失败（对象不存在不算失败）。
public class DeleteFailedException extends RuntimeException {

  /// 构造新的删除失败异常。
  ///
  /// @param message 错误消息
  public DeleteFailedException(String message) {
    super(message);
  }

  /// 构造新的删除失败异常。
  ///
  /// @param message 错误消息
  /// @param cause 原因异常
  public DeleteFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}

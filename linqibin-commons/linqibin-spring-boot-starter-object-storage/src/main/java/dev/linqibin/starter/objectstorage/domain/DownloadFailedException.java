package dev.linqibin.starter.objectstorage.domain;

/// 表示在重试后下载对象失败。
///
/// 此异常表示可重试的下载错误（如网络超时），与 {@link ObjectNotFoundException}
/// 和 {@link InvalidDownloadRequestException} 区分，后两者是不可重试的错误。
public class DownloadFailedException extends RuntimeException {

  /// 构造新的下载失败异常。
  ///
  /// @param message 错误消息
  public DownloadFailedException(String message) {
    super(message);
  }

  /// 构造新的下载失败异常。
  ///
  /// @param message 错误消息
  /// @param cause 原因异常
  public DownloadFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}

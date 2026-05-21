package dev.linqibin.starter.objectstorage.domain;

/// 表示下载请求参数无效。
///
/// 此异常是永久性错误，不应重试。调用者应该检查参数是否正确。
public class InvalidDownloadRequestException extends DownloadFailedException {

  /// 构造新的下载请求无效异常。
  ///
  /// @param message 错误消息
  public InvalidDownloadRequestException(String message) {
    super(message);
  }
}

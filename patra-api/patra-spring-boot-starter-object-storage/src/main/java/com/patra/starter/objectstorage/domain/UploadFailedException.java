package com.patra.starter.objectstorage.domain;

/** 表示在重试后上传对象失败。 */
public class UploadFailedException extends RuntimeException {

  /**
   * 构造新的上传失败异常。
   *
   * @param message 错误消息
   */
  public UploadFailedException(String message) {
    super(message);
  }

  /**
   * 构造新的上传失败异常。
   *
   * @param message 错误消息
   * @param cause 原因异常
   */
  public UploadFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}

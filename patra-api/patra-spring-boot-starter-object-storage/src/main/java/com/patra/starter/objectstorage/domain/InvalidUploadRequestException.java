package com.patra.starter.objectstorage.domain;

/// 表示上传请求包含无效参数。
/// 
/// 此异常表示**永久故障(客户端错误)**,**不应**重试。 在尝试实际上传操作之前,当请求参数(bucket、key、metadata)验证失败时抛出。
/// 
/// @see UploadFailedException 可重试的瞬时上传失败
public class InvalidUploadRequestException extends UploadFailedException {

  /// 构造一个新的无效上传请求异常,带有指定的详细消息。
/// 
/// @param message 解释请求为何无效的详细消息
  public InvalidUploadRequestException(String message) {
    super(message);
  }

  /// 构造一个新的无效上传请求异常,带有指定的详细消息和原因。
/// 
/// @param message 详细消息
/// @param cause 底层原因
  public InvalidUploadRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}

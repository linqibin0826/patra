package com.patra.common.json;

/// JSON 规范化失败时抛出的异常。
/// 
/// 失败原因包括解析错误、非法数值(NaN、Infinity)、超出深度或长度限制,或遇到禁止的键。
public class JsonNormalizationException extends RuntimeException {

  /// 使用指定消息创建异常。
/// 
/// @param message 描述规范化失败的错误消息
  public JsonNormalizationException(String message) {
    super(message);
  }

  /// 使用指定消息和原因创建异常。
/// 
/// @param message 描述规范化失败的错误消息
/// @param cause 失败的根本原因
  public JsonNormalizationException(String message, Throwable cause) {
    super(message, cause);
  }
}

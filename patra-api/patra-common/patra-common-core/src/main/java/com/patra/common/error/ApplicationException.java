package com.patra.common.error;

import com.patra.common.error.codes.ErrorCodeLike;

/// 携带结构化业务错误码的应用层异常。
/// 
/// 用于包装领域异常或表示使用 {@link ErrorCodeLike} 丰富的应用错误。 嵌入的错误码在确定 HTTP 状态和序列化响应时指导错误解析算法。
/// 
/// @author linqibin
/// @since 0.1.0
public class ApplicationException extends RuntimeException {

  /// 与此异常关联的业务错误码。
  private final ErrorCodeLike errorCode;

  /// 使用提供的错误码和消息创建异常。
/// 
/// @param errorCode 业务错误码,不能为 null
/// @param message 异常消息
/// @throws IllegalArgumentException 如果错误码为 null
  public ApplicationException(ErrorCodeLike errorCode, String message) {
    super(message);
    if (errorCode == null) {
      throw new IllegalArgumentException("ErrorCode 不能为 null");
    }
    this.errorCode = errorCode;
  }

  /// 使用提供的错误码、消息和根本原因创建异常。
/// 
/// @param errorCode 业务错误码,不能为 null
/// @param message 异常消息
/// @param cause 根本原因
/// @throws IllegalArgumentException 如果错误码为 null
  public ApplicationException(ErrorCodeLike errorCode, String message, Throwable cause) {
    super(message, cause);
    if (errorCode == null) {
      throw new IllegalArgumentException("ErrorCode 不能为 null");
    }
    this.errorCode = errorCode;
  }

  /// 返回关联的业务错误码。
/// 
/// @return 错误码
  public ErrorCodeLike getErrorCode() {
    return errorCode;
  }
}

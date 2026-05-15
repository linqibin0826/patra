package com.patra.starter.redisson.exception;

import dev.linqibin.commons.error.ApplicationException;

/// SpEL 表达式解析错误异常。
///
/// 当 `@DistributedLock` 注解中的 SpEL 表达式无法解析时抛出。
/// 客户端收到 500 Internal Server Error。
///
/// @author Patra Team
/// @since 1.0.0
public class LockExpressionException extends ApplicationException {

  /// 创建 SpEL 表达式解析错误异常。
  ///
  /// @param expression SpEL 表达式
  /// @param cause      根本原因
  public LockExpressionException(String expression, Throwable cause) {
    super(LockErrorCode.EXPRESSION_ERROR, String.format("无法解析 SpEL 表达式: %s", expression), cause);
  }

  /// 创建 SpEL 表达式解析错误异常（自定义消息）。
  ///
  /// @param message    自定义消息
  /// @param expression SpEL 表达式
  /// @param cause      根本原因
  public LockExpressionException(String message, String expression, Throwable cause) {
    super(
        LockErrorCode.EXPRESSION_ERROR,
        String.format("%s (expression: %s)", message, expression),
        cause);
  }

  /// 创建 SpEL 表达式解析错误异常（无 cause）。
  ///
  /// @param expression SpEL 表达式
  public LockExpressionException(String expression) {
    super(LockErrorCode.EXPRESSION_ERROR, String.format("无法解析 SpEL 表达式: %s", expression));
  }
}

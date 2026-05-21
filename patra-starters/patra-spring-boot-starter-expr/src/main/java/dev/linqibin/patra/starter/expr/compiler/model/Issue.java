package dev.linqibin.patra.starter.expr.compiler.model;

import java.util.Map;
import java.util.Objects;

/// 编译问题,包含严重性、代码、消息和上下文。
///
/// @param severity 严重性级别
/// @param code 问题代码
/// @param message 问题消息
/// @param context 问题上下文
/// @author linqibin
/// @since 0.1.0
public record Issue(
    IssueSeverity severity, String code, String message, Map<String, Object> context) {
  public Issue {
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(code, "code");
    message = message == null ? "" : message;
    if (context != null) {
      context = Map.copyOf(context);
    }
  }

  /// 创建错误问题。
  ///
  /// @param code 问题代码
  /// @param message 问题消息
  /// @param context 问题上下文
  /// @return 错误问题
  public static Issue error(String code, String message, Map<String, Object> context) {
    return new Issue(IssueSeverity.ERROR, code, message, context);
  }

  /// 创建警告问题。
  ///
  /// @param code 问题代码
  /// @param message 问题消息
  /// @param context 问题上下文
  /// @return 警告问题
  public static Issue warn(String code, String message, Map<String, Object> context) {
    return new Issue(IssueSeverity.WARN, code, message, context);
  }

  /// 自定义 toString 方法,防止上下文包含复杂对象时的无限递归。
  ///
  /// 避免对上下文值调用 toString(),这些值可能包含 Expr 或其他具有深度/循环引用的对象, 在调试期间会导致 StackOverflowError。
  @Override
  public String toString() {
    String contextSummary =
        context == null
            ? "null"
            : "Map{size=" + context.size() + ", keys=" + context.keySet() + "}";
    return "Issue[severity="
        + severity
        + ", code="
        + code
        + ", message="
        + message
        + ", context="
        + contextSummary
        + "]";
  }
}

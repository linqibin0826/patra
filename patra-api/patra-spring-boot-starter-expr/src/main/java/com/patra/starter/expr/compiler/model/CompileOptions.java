package com.patra.starter.expr.compiler.model;

/**
 * 编译选项。
 *
 * @param strict 严格模式:缺少函数/转换时快速失败
 * @param maxQueryLength 查询长度限制(0 表示禁用)
 * @param timezone 时区(默认 UTC)
 * @param traceEnabled 启用渲染跟踪
 * @author linqibin
 * @since 0.1.0
 */
public record CompileOptions(
    boolean strict, int maxQueryLength, String timezone, boolean traceEnabled) {
  /**
   * 创建默认编译选项。
   *
   * @return 默认选项
   */
  public static CompileOptions defaults() {
    return new CompileOptions(false, 0, "UTC", false);
  }

  /**
   * 返回设置严格模式的新选项。
   *
   * @param value 严格模式值
   * @return 新选项
   */
  public CompileOptions withStrict(boolean value) {
    return new CompileOptions(value, maxQueryLength, timezone, traceEnabled);
  }

  /**
   * 返回设置最大查询长度的新选项。
   *
   * @param value 最大查询长度
   * @return 新选项
   */
  public CompileOptions withMaxQueryLength(int value) {
    return new CompileOptions(strict, value, timezone, traceEnabled);
  }

  /**
   * 返回设置时区的新选项。
   *
   * @param value 时区
   * @return 新选项
   */
  public CompileOptions withTimezone(String value) {
    return new CompileOptions(strict, maxQueryLength, value, traceEnabled);
  }

  /**
   * 返回设置跟踪启用的新选项。
   *
   * @param value 跟踪启用值
   * @return 新选项
   */
  public CompileOptions withTraceEnabled(boolean value) {
    return new CompileOptions(strict, maxQueryLength, timezone, value);
  }
}

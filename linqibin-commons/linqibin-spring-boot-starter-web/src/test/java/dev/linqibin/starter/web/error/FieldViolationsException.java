package dev.linqibin.starter.web.error;

import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.commons.error.field.HasFieldViolations;
import java.util.List;

/// 带字段错误的测试异常，`ProblemDetailBuilderTest` 和 `GlobalRestExceptionHandlerTest` 共用。
///
/// 字段错误可以为 `null`，用来模拟实现返回 `null` 的情况。
public final class FieldViolationsException extends RuntimeException implements HasFieldViolations {

  private final List<FieldViolation> violations;

  /// 创建测试异常。
  ///
  /// @param violations 字段错误，可以为 `null`
  public FieldViolationsException(List<FieldViolation> violations) {
    super("字段不合法");
    this.violations = violations == null ? null : List.copyOf(violations);
  }

  /// 返回字段错误。
  ///
  /// @return 字段错误，可能为 `null`
  @Override
  public List<FieldViolation> getFieldViolations() {
    return violations;
  }
}

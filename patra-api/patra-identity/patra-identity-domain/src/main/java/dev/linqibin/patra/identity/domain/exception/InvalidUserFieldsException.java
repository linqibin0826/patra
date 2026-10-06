package dev.linqibin.patra.identity.domain.exception;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.commons.error.field.HasFieldViolations;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import java.util.List;

/// 注册或登录的字段不合法，返回 422，`errors[]` 里列出全部字段错误。
public final class InvalidUserFieldsException extends DomainException
    implements HasFieldViolations {

  private final List<FieldViolation> fieldViolations;

  /// 创建异常。
  ///
  /// @param fieldViolations 字段错误，至少一条
  public InvalidUserFieldsException(List<FieldViolation> fieldViolations) {
    super("请求参数不合法", StandardErrorTrait.RULE_VIOLATION);
    if (fieldViolations == null || fieldViolations.isEmpty()) {
      throw new IllegalArgumentException("至少要有一条字段错误");
    }
    this.fieldViolations = List.copyOf(fieldViolations);
  }

  /// 返回字段错误。
  ///
  /// @return 字段错误的副本
  @Override
  public List<FieldViolation> getFieldViolations() {
    return List.copyOf(fieldViolations);
  }
}

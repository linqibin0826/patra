package dev.patra.interaction.domain.collection.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.error.field.HasFieldViolations;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.util.List;

/// 收藏夹名称、简介或备注不合法（422），`errors[]` 里列出字段错误。
public final class InvalidCollectionFieldsException extends DomainException
    implements HasFieldViolations {

  private final List<FieldViolation> fieldViolations;

  /// 创建异常。
  ///
  /// @param fieldViolations 字段错误，至少一条
  public InvalidCollectionFieldsException(List<FieldViolation> fieldViolations) {
    super("请求参数不合法", StandardErrorTrait.RULE_VIOLATION);
    if (fieldViolations == null || fieldViolations.isEmpty()) {
      throw new IllegalArgumentException("至少要有一条字段错误");
    }
    this.fieldViolations = List.copyOf(fieldViolations);
  }

  /// 返回字段错误。
  ///
  /// @return 字段错误
  @Override
  public List<FieldViolation> getFieldViolations() {
    return fieldViolations;
  }
}

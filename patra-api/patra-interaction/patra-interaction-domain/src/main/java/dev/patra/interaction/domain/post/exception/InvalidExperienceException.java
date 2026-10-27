package dev.patra.interaction.domain.post.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.error.field.HasFieldViolations;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.util.List;

/// 投稿经验的字段不合法（422），`errors[]` 一次列出全部字段错误。
public final class InvalidExperienceException extends DomainException
    implements HasFieldViolations {

  private final List<FieldViolation> fieldViolations;

  /// 创建异常。
  ///
  /// @param fieldViolations 字段错误，至少一条
  public InvalidExperienceException(List<FieldViolation> fieldViolations) {
    super("投稿经验填写有误", StandardErrorTrait.RULE_VIOLATION);
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

package dev.patra.identity.domain.account.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.error.field.HasFieldViolations;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.util.List;

/// 邮箱已注册或用户名已被使用，返回 409，`errors[]` 里按字段给原因码（`email` / `ALREADY_REGISTERED`、
/// `username` / `ALREADY_TAKEN`），两条可以同时出现。并发撞上唯一约束、别人的旧名还在保留期内也抛它。
public final class UserIdentifierTakenException extends DomainException
    implements HasFieldViolations {

  private final List<FieldViolation> fieldViolations;

  /// 创建异常。文案固定，不带邮箱和用户名。
  ///
  /// @param fieldViolations 被占用的字段，至少一条
  public UserIdentifierTakenException(List<FieldViolation> fieldViolations) {
    super("邮箱或用户名已被使用", StandardErrorTrait.CONFLICT);
    if (fieldViolations == null || fieldViolations.isEmpty()) {
      throw new IllegalArgumentException("至少要有一条字段错误");
    }
    this.fieldViolations = List.copyOf(fieldViolations);
  }

  /// 返回被占用的字段。
  ///
  /// @return 字段错误的副本
  @Override
  public List<FieldViolation> getFieldViolations() {
    return List.copyOf(fieldViolations);
  }
}

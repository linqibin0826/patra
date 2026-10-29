package dev.patra.identity.domain.account.exception;

import dev.patra.identity.domain.account.model.vo.avatar.AvatarRejection;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.error.field.HasFieldViolations;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.util.List;

/// 头像上传被拒，返回 422，`errors[]` 里一条 `file` 字段错误，原因码见 `AvatarRejection`。
public final class InvalidAvatarException extends DomainException implements HasFieldViolations {

  private final AvatarRejection rejection;

  /// 创建异常。
  ///
  /// @param rejection 被拒原因
  public InvalidAvatarException(AvatarRejection rejection) {
    super("头像不可用: " + rejection.name(), StandardErrorTrait.RULE_VIOLATION);
    this.rejection = rejection;
  }

  /// 被拒原因。
  ///
  /// @return 原因
  public AvatarRejection getRejection() {
    return rejection;
  }

  /// 返回字段错误。
  ///
  /// @return 一条 `file` 字段错误
  @Override
  public List<FieldViolation> getFieldViolations() {
    return List.of(FieldViolation.of("file", rejection.name(), getMessage()));
  }
}

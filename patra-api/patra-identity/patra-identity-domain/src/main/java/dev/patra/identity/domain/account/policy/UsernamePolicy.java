package dev.patra.identity.domain.account.policy;

import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.tool.core.error.field.FieldViolation;
import java.util.Optional;

/// 用户名规则：先查 {@link Username} 的语法，再查保留字。
///
/// 没有依赖，装配成 Bean 只为了和 {@link PasswordPolicy} 一致。
public final class UsernamePolicy {

  /// 按注册和改名的规则校验，只报第一条。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @return 第一条不满足的规则；合法时为空
  public Optional<FieldViolation> validate(String raw) {
    Optional<FieldViolation> syntax = Username.violation(raw);
    if (syntax.isPresent()) {
      return syntax;
    }
    if (ReservedUsernames.contains(Username.normalize(raw))) {
      return Optional.of(UserFieldViolations.usernameReserved());
    }
    return Optional.empty();
  }
}

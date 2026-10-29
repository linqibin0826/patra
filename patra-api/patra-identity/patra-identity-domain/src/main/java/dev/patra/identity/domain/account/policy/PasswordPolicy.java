package dev.patra.identity.domain.account.policy;

import dev.patra.identity.domain.account.model.vo.PlainPassword;
import dev.patra.identity.domain.account.port.password.CommonPasswordPort;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.tool.core.error.field.FieldViolation;
import java.util.Objects;
import java.util.Optional;

/// 新密码的规则（注册、改密码、重设密码）：不为空、Unicode 合法、8 到 64 个码点、不在常见密码名单里。
///
/// 登录不用注册规则：密码规则以后调整时旧密码不该被拦住。登录只设一个宽松的长度上限
/// {@link #MAX_LOGIN_LENGTH}，拦下不可能是合法密码的超长输入。
public final class PasswordPolicy {

  /// 最短 8 个码点。
  public static final int MIN_LENGTH = 8;

  /// 最长 64 个码点。
  public static final int MAX_LENGTH = 64;

  /// 登录时密码最长 1024 个码点。远大于注册上限，超过的不可能是合法密码，不必再做哈希。
  public static final int MAX_LOGIN_LENGTH = 1024;

  private final CommonPasswordPort commonPasswords;

  /// 创建规则。
  ///
  /// @param commonPasswords 常见密码名单
  public PasswordPolicy(CommonPasswordPort commonPasswords) {
    this.commonPasswords = Objects.requireNonNull(commonPasswords, "commonPasswords 不能为 null");
  }

  /// 按新密码的规则校验（注册、改密码、重设密码都用它），按指定的字段名报错。
  /// 长度按原始输入算，名单按比较键查；超长的输入不做规范化、不查名单。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @param field 报错时的字段名（`password` 或 `newPassword`）
  /// @return 第一条不满足的规则；合法时为空
  public Optional<FieldViolation> validate(String raw, String field) {
    Optional<FieldViolation> basic = PlainPassword.validate(raw, field);
    if (basic.isPresent()) {
      return basic;
    }
    int length = raw.codePointCount(0, raw.length());
    if (length < MIN_LENGTH) {
      return Optional.of(UserFieldViolations.passwordTooShort(field));
    }
    if (length > MAX_LENGTH) {
      return Optional.of(UserFieldViolations.passwordTooLong(field));
    }
    if (commonPasswords.isCommon(PlainPassword.comparisonKeyOf(raw))) {
      return Optional.of(UserFieldViolations.passwordTooCommon(field));
    }
    return Optional.empty();
  }
}

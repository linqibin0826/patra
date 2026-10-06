package dev.linqibin.patra.identity.domain.policy;

import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.patra.identity.domain.model.vo.PlainPassword;
import dev.linqibin.patra.identity.domain.model.vo.UserFieldViolations;
import dev.linqibin.patra.identity.domain.port.password.CommonPasswordPort;
import java.util.Objects;
import java.util.Optional;

/// 注册时的密码规则：不为空、Unicode 合法、8 到 64 个码点、不在常见密码名单里。
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

  /// 按注册规则校验。长度按原始输入算，名单按比较键查；超长的输入不做规范化、不查名单。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @return 第一条不满足的规则；合法时为空
  public Optional<FieldViolation> validateForRegistration(String raw) {
    Optional<FieldViolation> basic = PlainPassword.validate(raw);
    if (basic.isPresent()) {
      return basic;
    }
    int length = raw.codePointCount(0, raw.length());
    if (length < MIN_LENGTH) {
      return Optional.of(UserFieldViolations.passwordTooShort());
    }
    if (length > MAX_LENGTH) {
      return Optional.of(UserFieldViolations.passwordTooLong());
    }
    if (commonPasswords.isCommon(PlainPassword.comparisonKeyOf(raw))) {
      return Optional.of(UserFieldViolations.passwordTooCommon());
    }
    return Optional.empty();
  }
}

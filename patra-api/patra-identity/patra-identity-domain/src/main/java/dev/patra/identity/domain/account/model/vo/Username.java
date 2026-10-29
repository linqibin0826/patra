package dev.patra.identity.domain.account.model.vo;

import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.tool.core.error.field.FieldViolation;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/// 规范化之后的用户名：去掉首尾空白、转成小写。
///
/// 语法规则：3 ~ 30 个字符，只有小写字母、数字、`_` 和 `-`；首尾是字母或数字；`_` 和 `-` 不连续；
/// 不全是数字。保留字不在这里判，由 `UsernamePolicy` 判。
///
/// @param value 规范化之后的用户名
public record Username(String value) {

  /// 最短 3 个字符。
  public static final int MIN_LENGTH = 3;

  /// 最长 30 个字符。
  public static final int MAX_LENGTH = 30;

  private static final Pattern ALLOWED_CHARACTERS = Pattern.compile("^[a-z0-9_-]*$");

  private static final Pattern SYNTAX = Pattern.compile("^[a-z0-9]([a-z0-9]|[_-][a-z0-9])*$");

  private static final Pattern DIGITS_ONLY = Pattern.compile("^[0-9]+$");

  /// 只接受已经规范化且语法合法的值，用于从库里恢复。
  ///
  /// @param value 规范化之后的用户名
  public Username {
    Objects.requireNonNull(value, "value 不能为 null");
    if (!value.equals(normalize(value)) || violation(value).isPresent()) {
      throw new IllegalArgumentException("用户名必须是规范化之后的合法值");
    }
  }

  /// 从用户输入创建。
  ///
  /// @param raw 用户输入
  /// @return 规范化之后的用户名
  /// @throws InvalidUserFieldsException 语法不合法
  public static Username of(String raw) {
    Optional<FieldViolation> violation = violation(raw);
    if (violation.isPresent()) {
      throw new InvalidUserFieldsException(List.of(violation.get()));
    }
    return new Username(normalize(raw));
  }

  /// 校验语法。先规范化，再依次查空、字符、长度、首尾与连续符号、纯数字，只报第一条。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @return 第一条不满足的规则；合法时为空
  public static Optional<FieldViolation> violation(String raw) {
    if (raw == null) {
      return Optional.of(UserFieldViolations.usernameRequired());
    }
    String normalized = normalize(raw);
    if (normalized.isEmpty()) {
      return Optional.of(UserFieldViolations.usernameRequired());
    }
    if (!ALLOWED_CHARACTERS.matcher(normalized).matches()) {
      return Optional.of(UserFieldViolations.usernameInvalidCharacter());
    }
    if (normalized.length() < MIN_LENGTH) {
      return Optional.of(UserFieldViolations.usernameTooShort());
    }
    if (normalized.length() > MAX_LENGTH) {
      return Optional.of(UserFieldViolations.usernameTooLong());
    }
    if (!SYNTAX.matcher(normalized).matches()) {
      return Optional.of(UserFieldViolations.usernameInvalidFormat());
    }
    if (DIGITS_ONLY.matcher(normalized).matches()) {
      return Optional.of(UserFieldViolations.usernameDigitsOnly());
    }
    return Optional.empty();
  }

  /// 去掉首尾空白、转成小写。
  ///
  /// @param raw 用户输入
  /// @return 规范化之后的值
  public static String normalize(String raw) {
    return raw.strip().toLowerCase(Locale.ROOT);
  }
}

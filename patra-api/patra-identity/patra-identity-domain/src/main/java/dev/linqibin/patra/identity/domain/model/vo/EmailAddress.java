package dev.linqibin.patra.identity.domain.model.vo;

import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/// 规范化之后的邮箱：去掉首尾空白、转成小写。
///
/// 格式规则和门户用的 Zod 4.6.5 默认 `z.email()` 是同一条正则，只认 ASCII。
///
/// @param value 规范化之后的邮箱
public record EmailAddress(String value) {

  /// 最长 254 个字符。
  public static final int MAX_LENGTH = 254;

  /// 抄自 `zod/v4/core/regexes.js` 的 `email`（Zod 4.6.5）。
  private static final Pattern FORMAT =
      Pattern.compile(
          "^(?:[A-Za-z0-9_'+\\-]+\\.)*[A-Za-z0-9_'+\\-]*[A-Za-z0-9_+-]"
              + "@(?:[A-Za-z0-9][A-Za-z0-9\\-]*\\.)+[A-Za-z]{2,}$");

  /// 只接受已经规范化的合法值，用于从库里恢复。
  ///
  /// @param value 规范化之后的邮箱
  public EmailAddress {
    Objects.requireNonNull(value, "value 不能为 null");
    if (!value.equals(normalize(value)) || validate(value).isPresent()) {
      throw new IllegalArgumentException("邮箱必须是规范化之后的合法值");
    }
  }

  /// 从用户输入创建。
  ///
  /// @param raw 用户输入
  /// @return 规范化之后的邮箱
  /// @throws InvalidUserFieldsException 输入不合法
  public static EmailAddress of(String raw) {
    Optional<FieldViolation> violation = validate(raw);
    if (violation.isPresent()) {
      throw new InvalidUserFieldsException(List.of(violation.get()));
    }
    return new EmailAddress(normalize(raw));
  }

  /// 校验用户输入。先去掉首尾空白，再依次查空、长度、格式。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @return 第一条不满足的规则；合法时为空
  public static Optional<FieldViolation> validate(String raw) {
    if (raw == null) {
      return Optional.of(UserFieldViolations.emailRequired());
    }
    String stripped = raw.strip();
    if (stripped.isEmpty()) {
      return Optional.of(UserFieldViolations.emailRequired());
    }
    if (stripped.length() > MAX_LENGTH) {
      return Optional.of(UserFieldViolations.emailTooLong());
    }
    if (!FORMAT.matcher(stripped).matches()) {
      return Optional.of(UserFieldViolations.emailInvalidFormat());
    }
    return Optional.empty();
  }

  /// 去掉首尾空白、转成小写。
  ///
  /// @param raw 用户输入
  /// @return 规范化之后的值
  private static String normalize(String raw) {
    return raw.strip().toLowerCase(Locale.ROOT);
  }
}

package dev.linqibin.patra.identity.domain.model.vo;

import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// 客户端自报的设备标识，去掉首尾空白后最多 128 个字符（按码点数），不含控制字符。
///
/// @param value 规范化之后的值
public record DeviceId(String value) {

  /// 最多 128 个字符。
  public static final int MAX_LENGTH = 128;

  /// 只接受规范化之后的合法值。
  public DeviceId {
    Objects.requireNonNull(value, "value 不能为 null");
    if (value.isEmpty()
        || !value.equals(value.strip())
        || codePoints(value) > MAX_LENGTH
        || hasControlCharacter(value)) {
      throw new IllegalArgumentException("设备标识必须是去掉首尾空白、不含控制字符、不超过 128 个字符的非空值");
    }
  }

  /// 校验用户输入。`null` 和空白按没有设备标识，不算错。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @return 不满足的规则；合法或没有时为空
  public static Optional<FieldViolation> validate(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String stripped = raw.strip();
    if (codePoints(stripped) > MAX_LENGTH) {
      return Optional.of(UserFieldViolations.deviceIdTooLong());
    }
    if (hasControlCharacter(stripped)) {
      return Optional.of(UserFieldViolations.deviceIdInvalidFormat());
    }
    return Optional.empty();
  }

  /// 从用户输入创建。`null` 和空白返回空。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @return 设备标识；没有时为空
  /// @throws InvalidUserFieldsException 太长时
  public static Optional<DeviceId> of(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    Optional<FieldViolation> violation = validate(raw);
    if (violation.isPresent()) {
      throw new InvalidUserFieldsException(List.of(violation.get()));
    }
    return Optional.of(new DeviceId(raw.strip()));
  }

  /// 是否含控制字符（换行、制表、ANSI 转义、NUL 等）：存进库和 Redis 后会在后台展示，不收。
  ///
  /// @param value 字符串
  /// @return 含控制字符时为 `true`
  private static boolean hasControlCharacter(String value) {
    return value.codePoints().anyMatch(Character::isISOControl);
  }

  /// 码点数。
  ///
  /// @param value 字符串
  /// @return 码点数
  private static int codePoints(String value) {
    return value.codePointCount(0, value.length());
  }
}

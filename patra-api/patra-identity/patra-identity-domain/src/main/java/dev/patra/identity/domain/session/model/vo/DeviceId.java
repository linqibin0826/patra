package dev.patra.identity.domain.session.model.vo;

import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.tool.core.error.field.FieldViolation;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/// 客户端的设备标识：门户种在浏览器里的长期 Cookie，值是 32 字节随机数的 base64url（无填充），
/// 恰好 43 个字符。格式由我们定，任何偏离都是坏值，不做去空白之类的宽容处理。
///
/// @param value 设备标识
public record DeviceId(String value) {

  /// 合规的设备标识。
  public static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

  /// 只接受合规的值。
  public DeviceId {
    Objects.requireNonNull(value, "value 不能为 null");
    if (!PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("设备标识必须是 43 个 base64url 字符");
    }
  }

  /// 校验用户输入。`null` 和空白按没有设备标识，不算错。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @return 不满足的规则；合法或没有时为空
  public static Optional<FieldViolation> validate(String raw) {
    if (raw == null || raw.isBlank() || PATTERN.matcher(raw).matches()) {
      return Optional.empty();
    }
    return Optional.of(UserFieldViolations.deviceIdInvalidFormat());
  }

  /// 从用户输入创建。`null` 和空白返回空。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @return 设备标识；没有时为空
  /// @throws InvalidUserFieldsException 格式不对时
  public static Optional<DeviceId> of(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    Optional<FieldViolation> violation = validate(raw);
    if (violation.isPresent()) {
      throw new InvalidUserFieldsException(List.of(violation.get()));
    }
    return Optional.of(new DeviceId(raw));
  }
}

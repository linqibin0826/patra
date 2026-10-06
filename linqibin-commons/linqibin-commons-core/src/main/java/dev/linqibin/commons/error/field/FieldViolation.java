package dev.linqibin.commons.error.field;

import java.io.Serializable;
import java.util.Objects;

/// 一条字段错误：哪个字段、什么原因、给人看的文案。
///
/// 原因码是机器可读的固定字符串（如 `TOO_SHORT`），前端按它选文案，不解析 `message`。
///
/// @param field 字段名，和请求体里的字段名一致
/// @param code 原因码，大写下划线
/// @param message 默认文案，可以为 `null`
public record FieldViolation(String field, String code, String message) implements Serializable {

  /// 校验必填项。
  ///
  /// @param field 字段名
  /// @param code 原因码
  /// @param message 默认文案
  public FieldViolation {
    Objects.requireNonNull(field, "field 不能为 null");
    Objects.requireNonNull(code, "code 不能为 null");
  }

  /// 创建一条字段错误。
  ///
  /// @param field 字段名
  /// @param code 原因码
  /// @param message 默认文案，可以为 `null`
  /// @return 字段错误
  public static FieldViolation of(String field, String code, String message) {
    return new FieldViolation(field, code, message);
  }
}

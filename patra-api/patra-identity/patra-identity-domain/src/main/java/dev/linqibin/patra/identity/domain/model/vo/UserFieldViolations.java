package dev.linqibin.patra.identity.domain.model.vo;

import dev.linqibin.commons.error.field.FieldViolation;

/// 前台用户注册、登录时会报的字段错误。前端按原因码选文案，`message` 只是默认文案。
public final class UserFieldViolations {

  /// 邮箱字段名，和请求体一致。
  public static final String EMAIL = "email";

  /// 密码字段名，和请求体一致。
  public static final String PASSWORD = "password";

  /// 客户端类型字段名，和请求体一致。
  public static final String CLIENT_TYPE = "clientType";

  /// 设备标识字段名，和请求体一致。
  public static final String DEVICE_ID = "deviceId";

  /// 为空。
  public static final String REQUIRED = "REQUIRED";

  /// 太长。
  public static final String TOO_LONG = "TOO_LONG";

  /// 格式不对。
  public static final String INVALID_FORMAT = "INVALID_FORMAT";

  /// 太短。
  public static final String TOO_SHORT = "TOO_SHORT";

  /// 在常见密码名单里。
  public static final String TOO_COMMON = "TOO_COMMON";

  /// 含不合法的 Unicode（孤立的代理项）。
  public static final String INVALID_CHARACTER = "INVALID_CHARACTER";

  /// 只有静态方法。
  private UserFieldViolations() {}

  /// 邮箱为空。
  ///
  /// @return 字段错误
  public static FieldViolation emailRequired() {
    return FieldViolation.of(EMAIL, REQUIRED, "请输入邮箱");
  }

  /// 邮箱太长。
  ///
  /// @return 字段错误
  public static FieldViolation emailTooLong() {
    return FieldViolation.of(EMAIL, TOO_LONG, "邮箱最长 254 个字符");
  }

  /// 邮箱格式不对。
  ///
  /// @return 字段错误
  public static FieldViolation emailInvalidFormat() {
    return FieldViolation.of(EMAIL, INVALID_FORMAT, "邮箱格式不正确");
  }

  /// 密码为空。
  ///
  /// @return 字段错误
  public static FieldViolation passwordRequired() {
    return FieldViolation.of(PASSWORD, REQUIRED, "请输入密码");
  }

  /// 密码含不合法的 Unicode。
  ///
  /// @return 字段错误
  public static FieldViolation passwordInvalidCharacter() {
    return FieldViolation.of(PASSWORD, INVALID_CHARACTER, "密码包含无效字符");
  }

  /// 密码太短。
  ///
  /// @return 字段错误
  public static FieldViolation passwordTooShort() {
    return FieldViolation.of(PASSWORD, TOO_SHORT, "密码至少 8 位");
  }

  /// 密码太长。
  ///
  /// @return 字段错误
  public static FieldViolation passwordTooLong() {
    return FieldViolation.of(PASSWORD, TOO_LONG, "密码最长 64 位");
  }

  /// 密码太常见。
  ///
  /// @return 字段错误
  public static FieldViolation passwordTooCommon() {
    return FieldViolation.of(PASSWORD, TOO_COMMON, "这个密码太常见，容易被猜到，请换一个");
  }

  /// 客户端类型不认识。
  ///
  /// @return 字段错误
  public static FieldViolation clientTypeInvalidFormat() {
    return FieldViolation.of(CLIENT_TYPE, INVALID_FORMAT, "不支持的客户端类型");
  }

  /// 设备标识太长。
  ///
  /// @return 字段错误
  public static FieldViolation deviceIdTooLong() {
    return FieldViolation.of(DEVICE_ID, TOO_LONG, "设备标识最长 128 个字符");
  }

  /// 设备标识含控制字符。
  ///
  /// @return 字段错误
  public static FieldViolation deviceIdInvalidFormat() {
    return FieldViolation.of(DEVICE_ID, INVALID_FORMAT, "设备标识不能包含控制字符");
  }
}

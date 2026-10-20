package dev.patra.identity.domain.model.vo;

import dev.patra.tool.core.error.field.FieldViolation;

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

  /// 含不合法的 Unicode（孤立的代理项）或不允许的字符。
  public static final String INVALID_CHARACTER = "INVALID_CHARACTER";

  /// 用户名字段名，和请求体一致。
  public static final String USERNAME = "username";

  /// 昵称字段名，和请求体一致。
  public static final String DISPLAY_NAME = "displayName";

  /// 全是数字。
  public static final String DIGITS_ONLY = "DIGITS_ONLY";

  /// 保留字或拒绝词。
  public static final String RESERVED = "RESERVED";

  /// 和当前值相同。
  public static final String UNCHANGED = "UNCHANGED";

  /// 已被别人使用。
  public static final String ALREADY_TAKEN = "ALREADY_TAKEN";

  /// 已注册。
  public static final String ALREADY_REGISTERED = "ALREADY_REGISTERED";

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

  /// 邮箱已注册。
  ///
  /// @return 字段错误
  public static FieldViolation emailAlreadyRegistered() {
    return FieldViolation.of(EMAIL, ALREADY_REGISTERED, "该邮箱已注册");
  }

  /// 用户名为空。
  ///
  /// @return 字段错误
  public static FieldViolation usernameRequired() {
    return FieldViolation.of(USERNAME, REQUIRED, "请输入用户名");
  }

  /// 用户名含不允许的字符。
  ///
  /// @return 字段错误
  public static FieldViolation usernameInvalidCharacter() {
    return FieldViolation.of(USERNAME, INVALID_CHARACTER, "用户名只能包含小写字母、数字、_ 和 -");
  }

  /// 用户名太短。
  ///
  /// @return 字段错误
  public static FieldViolation usernameTooShort() {
    return FieldViolation.of(USERNAME, TOO_SHORT, "用户名至少 3 个字符");
  }

  /// 用户名太长。
  ///
  /// @return 字段错误
  public static FieldViolation usernameTooLong() {
    return FieldViolation.of(USERNAME, TOO_LONG, "用户名最长 30 个字符");
  }

  /// 用户名首尾不是字母数字，或 `_`、`-` 连续出现。
  ///
  /// @return 字段错误
  public static FieldViolation usernameInvalidFormat() {
    return FieldViolation.of(USERNAME, INVALID_FORMAT, "用户名首尾须是字母或数字，_ 和 - 不能连续");
  }

  /// 用户名全是数字。
  ///
  /// @return 字段错误
  public static FieldViolation usernameDigitsOnly() {
    return FieldViolation.of(USERNAME, DIGITS_ONLY, "用户名不能全是数字");
  }

  /// 用户名命中保留字。
  ///
  /// @return 字段错误
  public static FieldViolation usernameReserved() {
    return FieldViolation.of(USERNAME, RESERVED, "这个用户名不能使用");
  }

  /// 新用户名与当前相同。
  ///
  /// @return 字段错误
  public static FieldViolation usernameUnchanged() {
    return FieldViolation.of(USERNAME, UNCHANGED, "新用户名与当前相同");
  }

  /// 用户名已被使用。
  ///
  /// @return 字段错误
  public static FieldViolation usernameAlreadyTaken() {
    return FieldViolation.of(USERNAME, ALREADY_TAKEN, "该用户名已被使用");
  }

  /// 昵称为空。
  ///
  /// @return 字段错误
  public static FieldViolation displayNameRequired() {
    return FieldViolation.of(DISPLAY_NAME, REQUIRED, "请输入昵称");
  }

  /// 昵称含不允许的字符。
  ///
  /// @return 字段错误
  public static FieldViolation displayNameInvalidCharacter() {
    return FieldViolation.of(DISPLAY_NAME, INVALID_CHARACTER, "昵称包含不允许的字符");
  }

  /// 昵称太短。
  ///
  /// @return 字段错误
  public static FieldViolation displayNameTooShort() {
    return FieldViolation.of(DISPLAY_NAME, TOO_SHORT, "昵称至少 2 个字");
  }

  /// 昵称太长。
  ///
  /// @return 字段错误
  public static FieldViolation displayNameTooLong() {
    return FieldViolation.of(DISPLAY_NAME, TOO_LONG, "昵称最长 30 个字");
  }

  /// 昵称像网址、邮箱、手机号或微信号。
  ///
  /// @return 字段错误
  public static FieldViolation displayNameInvalidFormat() {
    return FieldViolation.of(DISPLAY_NAME, INVALID_FORMAT, "昵称不能是网址、邮箱、手机号或微信号");
  }

  /// 昵称命中拒绝词。
  ///
  /// @return 字段错误
  public static FieldViolation displayNameReserved() {
    return FieldViolation.of(DISPLAY_NAME, RESERVED, "这个昵称不能使用");
  }
}

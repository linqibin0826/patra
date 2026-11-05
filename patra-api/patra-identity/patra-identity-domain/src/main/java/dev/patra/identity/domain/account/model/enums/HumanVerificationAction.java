package dev.patra.identity.domain.account.model.enums;

/// 要做人机验证的操作。门户渲染组件时传线上值，服务端校验时核对，防止别的操作拿到的令牌被挪用。
public enum HumanVerificationAction {
  /// 登录。
  LOGIN("login"),
  /// 注册。
  REGISTER("register"),
  /// 申请找回密码。
  PASSWORD_RESET("password_reset");

  private final String wireValue;

  /// 创建枚举值。
  ///
  /// @param wireValue 接口里的取值
  HumanVerificationAction(String wireValue) {
    this.wireValue = wireValue;
  }

  /// 接口里的取值。
  ///
  /// @return 取值
  public String wireValue() {
    return wireValue;
  }
}

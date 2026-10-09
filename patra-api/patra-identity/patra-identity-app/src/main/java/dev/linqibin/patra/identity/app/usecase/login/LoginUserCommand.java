package dev.linqibin.patra.identity.app.usecase.login;

import dev.linqibin.commons.cqrs.Command;

/// 前台用户登录：校验凭据并建会话。
///
/// `clientType` 和 `deviceId` 允许为 `null`：处理器里客户端类型回退到 `web`，设备标识按没有。
///
/// @param email 邮箱，原始输入
/// @param password 密码，原始输入
/// @param clientType 客户端类型，原始输入，可以为 `null`
/// @param deviceId 设备标识，原始输入，可以为 `null`
public record LoginUserCommand(String email, String password, String clientType, String deviceId)
    implements Command<LoginUserResult> {

  /// 创建命令。
  ///
  /// @param email 邮箱
  /// @param password 密码
  /// @param clientType 客户端类型，可以为 `null`
  /// @param deviceId 设备标识，可以为 `null`
  /// @return 命令
  public static LoginUserCommand of(
      String email, String password, String clientType, String deviceId) {
    return new LoginUserCommand(email, password, clientType, deviceId);
  }

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "LoginUserCommand[email="
        + email
        + ", password=***, clientType="
        + clientType
        + ", deviceId="
        + deviceId
        + "]";
  }
}

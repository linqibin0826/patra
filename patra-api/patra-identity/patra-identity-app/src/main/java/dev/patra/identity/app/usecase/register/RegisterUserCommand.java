package dev.patra.identity.app.usecase.register;

import dev.patra.tool.cqrs.Command;

/// 注册前台用户，成功后直接建会话（注册即登录）。
///
/// `clientType` 和 `deviceId` 允许为 `null`：处理器里客户端类型回退到 `web`，设备标识按没有。
///
/// @param email 邮箱，原始输入
/// @param password 密码，原始输入
/// @param clientType 客户端类型，原始输入，可以为 `null`
/// @param deviceId 设备标识，原始输入，可以为 `null`
public record RegisterUserCommand(String email, String password, String clientType, String deviceId)
    implements Command<RegisterUserResult> {

  /// 创建命令。
  ///
  /// @param email 邮箱
  /// @param password 密码
  /// @param clientType 客户端类型，可以为 `null`
  /// @param deviceId 设备标识，可以为 `null`
  /// @return 命令
  public static RegisterUserCommand of(
      String email, String password, String clientType, String deviceId) {
    return new RegisterUserCommand(email, password, clientType, deviceId);
  }

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "RegisterUserCommand[email="
        + email
        + ", password=***, clientType="
        + clientType
        + ", deviceId="
        + deviceId
        + "]";
  }
}

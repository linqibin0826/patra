package dev.patra.identity.app.usecase.register;

import dev.patra.tool.cqrs.Command;
import lombok.Builder;

/// 注册前台用户，成功后直接建会话（注册即登录）。
///
/// `clientType` 和 `deviceId` 允许为 `null`：处理器里客户端类型回退到 `web`，设备标识按没有。
///
/// @param email 邮箱，原始输入
/// @param username 用户名，原始输入
/// @param password 密码，原始输入
/// @param clientType 客户端类型，原始输入，可以为 `null`
/// @param deviceId 设备标识，原始输入，可以为 `null`
@Builder
public record RegisterUserCommand(
    String email, String username, String password, String clientType, String deviceId)
    implements Command<RegisterUserResult> {

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "RegisterUserCommand[email="
        + email
        + ", username="
        + username
        + ", password=***, clientType="
        + clientType
        + ", deviceId="
        + deviceId
        + "]";
  }
}

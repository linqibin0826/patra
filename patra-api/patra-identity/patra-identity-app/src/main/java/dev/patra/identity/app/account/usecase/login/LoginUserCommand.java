package dev.patra.identity.app.account.usecase.login;

import dev.patra.tool.cqrs.Command;
import lombok.Builder;

/// 前台用户登录：校验凭据并建会话。
///
/// `clientType` 和 `deviceId` 允许为 `null`：处理器里客户端类型回退到 `web`，设备标识按没有。
/// `clientIp` 与 `userAgent` 来自请求（经受信转发链路），写进登录记录并用于新设备邮件。
///
/// @param email 邮箱，原始输入
/// @param password 密码，原始输入
/// @param clientType 客户端类型，原始输入，可以为 `null`
/// @param deviceId 设备标识，原始输入，可以为 `null`
/// @param clientIp 客户端 IP，可以为 `null`
/// @param userAgent User-Agent，可以为 `null`
@Builder
public record LoginUserCommand(
    String email,
    String password,
    String clientType,
    String deviceId,
    String clientIp,
    String userAgent)
    implements Command<AuthenticatedUser> {

  /// 不输出密码、设备标识原文和 UA 原文。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "LoginUserCommand[email="
        + email
        + ", password=***, clientType="
        + clientType
        + ", deviceId="
        + (deviceId == null ? null : "***")
        + ", clientIp="
        + clientIp
        + ", userAgentLength="
        + (userAgent == null ? 0 : userAgent.length())
        + "]";
  }
}

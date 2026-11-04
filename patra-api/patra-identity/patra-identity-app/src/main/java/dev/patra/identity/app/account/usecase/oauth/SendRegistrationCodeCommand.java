package dev.patra.identity.app.account.usecase.oauth;

import dev.patra.tool.cqrs.Command;

/// 第三方首次注册：提交用户名和邮箱，发验证码。重发也是它。
///
/// @param ticket 待补填凭证原文，可以为 `null`
/// @param username 用户名，原始输入
/// @param email 邮箱，原始输入
/// @param clientIp 客户端 IP，可以为 `null`
public record SendRegistrationCodeCommand(
    String ticket, String username, String email, String clientIp)
    implements Command<RegistrationCodeSent> {

  /// 创建命令。
  ///
  /// @param ticket 待补填凭证原文，可以为 `null`
  /// @param username 用户名，原始输入
  /// @param email 邮箱，原始输入
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @return 命令
  public static SendRegistrationCodeCommand of(
      String ticket, String username, String email, String clientIp) {
    return new SendRegistrationCodeCommand(ticket, username, email, clientIp);
  }

  /// 不输出凭证。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "SendRegistrationCodeCommand[ticket=***, username="
        + username
        + ", email="
        + email
        + ", clientIp="
        + clientIp
        + "]";
  }
}

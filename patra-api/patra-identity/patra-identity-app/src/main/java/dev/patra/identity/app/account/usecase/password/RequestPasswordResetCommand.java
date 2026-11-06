package dev.patra.identity.app.account.usecase.password;

import dev.patra.tool.cqrs.Command;

/// 找回密码：申请一封重设邮件。
///
/// @param email 邮箱，原始输入
/// @param clientIp 客户端 IP，可以为 `null`
/// @param verificationToken 人机验证令牌，每次都必须带；这里允许为 `null`，由处理器报字段错误
public record RequestPasswordResetCommand(String email, String clientIp, String verificationToken)
    implements Command<PasswordResetRequested> {

  /// 创建命令。
  ///
  /// @param email 邮箱
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @param verificationToken 人机验证令牌
  /// @return 命令
  public static RequestPasswordResetCommand of(
      String email, String clientIp, String verificationToken) {
    return new RequestPasswordResetCommand(email, clientIp, verificationToken);
  }

  /// 不输出人机验证令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "RequestPasswordResetCommand[email=" + email + ", clientIp=" + clientIp + "]";
  }
}

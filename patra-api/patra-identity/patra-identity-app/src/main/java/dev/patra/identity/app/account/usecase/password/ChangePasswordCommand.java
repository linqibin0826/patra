package dev.patra.identity.app.account.usecase.password;

import dev.patra.identity.app.account.usecase.login.AuthenticatedUser;
import dev.patra.tool.cqrs.Command;

/// 改密码：输当前密码换新密码，本机换令牌保留登录，其他会话全部撤销。
///
/// @param currentPassword 当前密码，原始输入
/// @param newPassword 新密码，原始输入
/// @param clientIp 客户端 IP，可以为 `null`
/// @param userAgent User-Agent，可以为 `null`；安全通知里的设备（PAP-99）
public record ChangePasswordCommand(
    String currentPassword, String newPassword, String clientIp, String userAgent)
    implements Command<AuthenticatedUser> {

  /// 创建命令。
  ///
  /// @param currentPassword 当前密码
  /// @param newPassword 新密码
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @param userAgent User-Agent，可以为 `null`
  /// @return 命令
  public static ChangePasswordCommand of(
      String currentPassword, String newPassword, String clientIp, String userAgent) {
    return new ChangePasswordCommand(currentPassword, newPassword, clientIp, userAgent);
  }

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "ChangePasswordCommand[currentPassword=***, newPassword=***, clientIp="
        + clientIp
        + ", userAgentLength="
        + (userAgent == null ? 0 : userAgent.length())
        + "]";
  }
}

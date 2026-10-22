package dev.patra.identity.app.usecase.password;

import dev.patra.identity.app.usecase.auth.AuthenticatedUser;
import dev.patra.tool.cqrs.Command;

/// 改密码：输当前密码换新密码，本机换令牌保留登录，其他会话全部撤销。
///
/// @param currentPassword 当前密码，原始输入
/// @param newPassword 新密码，原始输入
/// @param clientIp 客户端 IP，可以为 `null`
public record ChangePasswordCommand(String currentPassword, String newPassword, String clientIp)
    implements Command<AuthenticatedUser> {

  /// 创建命令。
  ///
  /// @param currentPassword 当前密码
  /// @param newPassword 新密码
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @return 命令
  public static ChangePasswordCommand of(
      String currentPassword, String newPassword, String clientIp) {
    return new ChangePasswordCommand(currentPassword, newPassword, clientIp);
  }

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "ChangePasswordCommand[currentPassword=***, newPassword=***, clientIp=" + clientIp + "]";
  }
}

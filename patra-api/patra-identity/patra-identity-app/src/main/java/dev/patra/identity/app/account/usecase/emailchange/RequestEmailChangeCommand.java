package dev.patra.identity.app.account.usecase.emailchange;

import dev.patra.tool.cqrs.Command;

/// 改邮箱：申请把账号邮箱改为新地址，向新地址发确认链接。
///
/// @param newEmail 新邮箱，原始输入
/// @param clientIp 客户端 IP，可以为 `null`
public record RequestEmailChangeCommand(String newEmail, String clientIp)
    implements Command<EmailChangeRequested> {

  /// 创建命令。
  ///
  /// @param newEmail 新邮箱
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @return 命令
  public static RequestEmailChangeCommand of(String newEmail, String clientIp) {
    return new RequestEmailChangeCommand(newEmail, clientIp);
  }
}

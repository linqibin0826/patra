package dev.patra.identity.app.account.usecase.deletion;

import dev.patra.tool.cqrs.Command;

/// 发起注销：输入自己的用户名确认，进入冷静期。
///
/// @param username 输入的用户名，原始输入
/// @param clientIp 客户端 IP，可以为 `null`
/// @param userAgent User-Agent，可以为 `null`；进「注销已提交」通知的设备
public record RequestAccountDeletionCommand(String username, String clientIp, String userAgent)
    implements Command<AccountDeletionRequested> {

  /// 创建命令。
  ///
  /// @param username 输入的用户名
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @param userAgent User-Agent，可以为 `null`
  /// @return 命令
  public static RequestAccountDeletionCommand of(
      String username, String clientIp, String userAgent) {
    return new RequestAccountDeletionCommand(username, clientIp, userAgent);
  }
}

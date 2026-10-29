package dev.patra.identity.app.account.usecase.deletion;

import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.tool.cqrs.Command;

/// 取消注销：冷静期内回到正常。
///
/// @param clientIp 客户端 IP，可以为 `null`
/// @param userAgent User-Agent，可以为 `null`；进「已取消注销」通知的设备
public record CancelAccountDeletionCommand(String clientIp, String userAgent)
    implements Command<UserAccountReadModel> {

  /// 创建命令。
  ///
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @param userAgent User-Agent，可以为 `null`
  /// @return 命令
  public static CancelAccountDeletionCommand of(String clientIp, String userAgent) {
    return new CancelAccountDeletionCommand(clientIp, userAgent);
  }
}

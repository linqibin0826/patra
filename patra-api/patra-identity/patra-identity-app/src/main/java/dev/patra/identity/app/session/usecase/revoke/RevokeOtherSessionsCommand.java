package dev.patra.identity.app.session.usecase.revoke;

import dev.patra.tool.cqrs.Command;

/// 踢掉除本机外的全部会话（`DELETE /me/sessions/others`）。
public record RevokeOtherSessionsCommand() implements Command<SessionsRevoked> {

  /// 创建命令。
  ///
  /// @return 命令
  public static RevokeOtherSessionsCommand of() {
    return new RevokeOtherSessionsCommand();
  }
}

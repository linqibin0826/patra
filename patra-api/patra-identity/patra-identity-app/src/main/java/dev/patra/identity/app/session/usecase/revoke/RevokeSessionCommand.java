package dev.patra.identity.app.session.usecase.revoke;

import dev.patra.tool.cqrs.Command;

/// 在登录设备列表里踢掉一个会话（`DELETE /me/sessions/{id}`）。
///
/// @param sessionId 要踢掉的会话 ID
public record RevokeSessionCommand(long sessionId) implements Command<SessionsRevoked> {

  /// 创建命令。
  ///
  /// @param sessionId 要踢掉的会话 ID
  /// @return 命令
  public static RevokeSessionCommand of(long sessionId) {
    return new RevokeSessionCommand(sessionId);
  }
}

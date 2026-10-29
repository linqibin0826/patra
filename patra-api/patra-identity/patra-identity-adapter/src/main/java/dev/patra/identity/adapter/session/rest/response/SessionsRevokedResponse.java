package dev.patra.identity.adapter.session.rest.response;

import dev.patra.identity.app.session.usecase.revoke.SessionsRevoked;

/// 踢设备的响应：真的被踢掉的会话数，0 也是成功。
///
/// @param revokedSessions 被踢掉的会话数
public record SessionsRevokedResponse(int revokedSessions) {

  /// 从命令结果映射。
  ///
  /// @param revoked 命令结果
  /// @return 响应
  public static SessionsRevokedResponse from(SessionsRevoked revoked) {
    return new SessionsRevokedResponse(revoked.revokedSessions());
  }
}

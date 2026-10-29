package dev.patra.identity.app.session.usecase.revoke;

/// 踢设备的结果：真的被踢掉的会话数（门户的「已退出其他 N 台设备」用它）。
///
/// @param revokedSessions 被踢掉的会话数
public record SessionsRevoked(int revokedSessions) {

  /// 创建。
  ///
  /// @param revokedSessions 被踢掉的会话数
  /// @return 结果
  public static SessionsRevoked of(int revokedSessions) {
    return new SessionsRevoked(revokedSessions);
  }
}

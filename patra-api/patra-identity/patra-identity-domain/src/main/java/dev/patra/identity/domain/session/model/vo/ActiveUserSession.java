package dev.patra.identity.domain.session.model.vo;

import java.time.Instant;
import java.util.Objects;

/// Redis 里一条还活着的会话：设备列表只用它的会话 ID、登录时间与最后活跃（PAP-99 设计第 10.2 节）。
///
/// @param sessionId 会话 ID，等于登录记录的 ID
/// @param createdAt 登录时间
/// @param lastActiveAt 最后活跃，精度 60 秒
public record ActiveUserSession(long sessionId, Instant createdAt, Instant lastActiveAt) {

  /// 时间都不能为 `null`。
  public ActiveUserSession {
    Objects.requireNonNull(createdAt, "createdAt 不能为 null");
    Objects.requireNonNull(lastActiveAt, "lastActiveAt 不能为 null");
  }

  /// 创建。
  ///
  /// @param sessionId 会话 ID
  /// @param createdAt 登录时间
  /// @param lastActiveAt 最后活跃
  /// @return 活会话
  public static ActiveUserSession of(long sessionId, Instant createdAt, Instant lastActiveAt) {
    return new ActiveUserSession(sessionId, createdAt, lastActiveAt);
  }
}

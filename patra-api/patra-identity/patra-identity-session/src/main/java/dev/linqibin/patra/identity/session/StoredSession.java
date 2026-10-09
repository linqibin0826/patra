package dev.linqibin.patra.identity.session;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;

/// Redis 里查到的会话。
///
/// @param userId 用户 ID
/// @param sessionId 会话 ID
/// @param accountType 账号类型
/// @param clientType 客户端类型
/// @param deviceId 设备标识，可以为 `null`
/// @param createdAt 创建时间
/// @param lastActiveAt 最后活跃时间，最多落后真实活跃时间一个续期间隔
/// @param expiresAt 绝对过期时间
@Builder
public record StoredSession(
    long userId,
    long sessionId,
    AccountType accountType,
    ClientType clientType,
    String deviceId,
    Instant createdAt,
    Instant lastActiveAt,
    Instant expiresAt) {

  /// 校验各字段。
  public StoredSession {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId 必须是正数，实际值: " + userId);
    }
    if (sessionId <= 0) {
      throw new IllegalArgumentException("sessionId 必须是正数，实际值: " + sessionId);
    }
    Objects.requireNonNull(accountType, "accountType 不能为 null");
    Objects.requireNonNull(clientType, "clientType 不能为 null");
    Objects.requireNonNull(createdAt, "createdAt 不能为 null");
    Objects.requireNonNull(lastActiveAt, "lastActiveAt 不能为 null");
    Objects.requireNonNull(expiresAt, "expiresAt 不能为 null");
  }

  /// 转成当前用户，网关据此建认证对象。
  ///
  /// @return 当前用户
  public CurrentUser toCurrentUser() {
    return CurrentUser.of(userId, sessionId, accountType, clientType);
  }

  /// 设备标识。
  ///
  /// @return 设备标识；没有时为空
  public Optional<String> device() {
    return Optional.ofNullable(deviceId);
  }
}

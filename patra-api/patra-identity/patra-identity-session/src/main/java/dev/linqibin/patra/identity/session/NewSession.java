package dev.linqibin.patra.identity.session;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 要建的会话：调用方算好时间后交给 `RedisSessionStore.create`。
///
/// `createdAt` 和 `expiresAt` 由调用方按同一个 `now` 算出，Redis 和登录记录的过期时间才一致。
///
/// @param userId 用户 ID，正数
/// @param sessionId 会话 ID，正数，等于登录记录的 ID
/// @param accountType 账号类型
/// @param clientType 客户端类型
/// @param deviceId 设备标识，可以为 `null`；空白按 `null`
/// @param createdAt 创建时间
/// @param expiresAt 绝对过期时间，必须晚于创建时间
/// @param idleTimeout 不活跃过期，正数
/// @param maxSessionsPerUser 每用户的会话上限，至少 1
@Builder
public record NewSession(
    long userId,
    long sessionId,
    AccountType accountType,
    ClientType clientType,
    String deviceId,
    Instant createdAt,
    Instant expiresAt,
    Duration idleTimeout,
    int maxSessionsPerUser) {

  /// 校验各字段。
  public NewSession {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId 必须是正数，实际值: " + userId);
    }
    if (sessionId <= 0) {
      throw new IllegalArgumentException("sessionId 必须是正数，实际值: " + sessionId);
    }
    Objects.requireNonNull(accountType, "accountType 不能为 null");
    Objects.requireNonNull(clientType, "clientType 不能为 null");
    Objects.requireNonNull(createdAt, "createdAt 不能为 null");
    Objects.requireNonNull(expiresAt, "expiresAt 不能为 null");
    Objects.requireNonNull(idleTimeout, "idleTimeout 不能为 null");
    if (!expiresAt.isAfter(createdAt)) {
      throw new IllegalArgumentException("expiresAt 必须晚于 createdAt");
    }
    if (idleTimeout.isZero() || idleTimeout.isNegative()) {
      throw new IllegalArgumentException("idleTimeout 必须是正数");
    }
    if (maxSessionsPerUser < 1) {
      throw new IllegalArgumentException("maxSessionsPerUser 至少是 1，实际值: " + maxSessionsPerUser);
    }
    if (deviceId != null && deviceId.isBlank()) {
      deviceId = null;
    }
  }
}

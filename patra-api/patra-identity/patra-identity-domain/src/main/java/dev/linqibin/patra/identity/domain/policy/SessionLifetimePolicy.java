package dev.linqibin.patra.identity.domain.policy;

import dev.linqibin.patra.common.security.ClientType;
import java.util.Map;
import java.util.Objects;

/// 会话策略：每种客户端的有效期，以及每用户的会话上限。只在 identity 配置，网关不配。
///
/// @param lifetimes 按客户端类型的有效期，至少一行
/// @param maxSessionsPerUser 每用户的会话上限，至少 1
public record SessionLifetimePolicy(
    Map<ClientType, SessionLifetime> lifetimes, int maxSessionsPerUser) {

  /// 拷贝并校验。
  public SessionLifetimePolicy {
    lifetimes = Map.copyOf(Objects.requireNonNull(lifetimes, "lifetimes 不能为 null"));
    if (lifetimes.isEmpty()) {
      throw new IllegalArgumentException("至少要配一种客户端类型的会话有效期");
    }
    if (maxSessionsPerUser < 1) {
      throw new IllegalArgumentException("maxSessionsPerUser 至少是 1，实际值: " + maxSessionsPerUser);
    }
  }

  /// 创建策略。
  ///
  /// @param lifetimes 按客户端类型的有效期
  /// @param maxSessionsPerUser 每用户的会话上限
  /// @return 策略
  public static SessionLifetimePolicy of(
      Map<ClientType, SessionLifetime> lifetimes, int maxSessionsPerUser) {
    return new SessionLifetimePolicy(lifetimes, maxSessionsPerUser);
  }

  /// 某种客户端的有效期。
  ///
  /// @param clientType 客户端类型
  /// @return 有效期
  /// @throws IllegalStateException 没有为这种客户端配置时；启动校验保证本版不会发生
  public SessionLifetime lifetimeFor(ClientType clientType) {
    SessionLifetime lifetime =
        lifetimes.get(Objects.requireNonNull(clientType, "clientType 不能为 null"));
    if (lifetime == null) {
      throw new IllegalStateException("没有为 " + clientType + " 配置会话有效期");
    }
    return lifetime;
  }
}

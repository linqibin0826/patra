package dev.linqibin.patra.gateway;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.identity.session.NewSession;
import dev.linqibin.patra.identity.session.RedisSessionStore;
import java.time.Duration;
import java.time.Instant;

/// 集成测试里直接往 Redis 写会话、拿令牌：不经过 identity，网关只认 Redis 里有没有。
final class GatewayITSessions {

  /// 不活跃过期，和 identity 的默认配置一样。
  static final Duration IDLE = Duration.ofDays(30);

  /// 绝对过期，和 identity 的默认配置一样。
  static final Duration ABSOLUTE = Duration.ofDays(180);

  /// 工具类，不允许实例化。
  private GatewayITSessions() {}

  /// 一条现在创建、前台网页端的会话，字段可以再改。
  ///
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return 构建器
  static NewSession.NewSessionBuilder session(long userId, long sessionId) {
    Instant now = Instant.now();
    return NewSession.builder()
        .userId(userId)
        .sessionId(sessionId)
        .accountType(AccountType.USER)
        .clientType(ClientType.WEB)
        .createdAt(now)
        .expiresAt(now.plus(ABSOLUTE))
        .idleTimeout(IDLE)
        .maxSessionsPerUser(10);
  }

  /// 建会话并返回令牌原文。
  ///
  /// @param sessions 会话存储
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return 令牌，形如 `patra_user_…`
  static String issue(RedisSessionStore sessions, long userId, long sessionId) {
    return sessions.create(session(userId, sessionId).build()).token().value();
  }
}

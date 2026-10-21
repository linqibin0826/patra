package dev.patra.identity.infra.adapter.session;

import dev.patra.identity.domain.model.vo.DeviceId;
import dev.patra.identity.domain.port.session.IssuedUserSession;
import dev.patra.identity.domain.port.session.NewUserSession;
import dev.patra.identity.domain.port.session.SessionStorePort;
import dev.patra.identity.session.IssuedSession;
import dev.patra.identity.session.NewSession;
import dev.patra.identity.session.RedisSessionStore;
import dev.patra.tool.core.security.AccountType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 会话存储端口的实现：把 domain 的记录转成会话模块的类型，交给 `RedisSessionStore`。
///
/// `SessionStoreUnavailableException` 原样穿过，错误引擎按 `DEP_UNAVAILABLE` 映射成 503。
@Component
@RequiredArgsConstructor
public class SessionStoreAdapter implements SessionStorePort {

  private final RedisSessionStore store;

  /// 建会话。
  ///
  /// @param session 要签发的会话
  /// @return 令牌原文和被挤掉的会话 ID
  @Override
  public IssuedUserSession issue(NewUserSession session) {
    IssuedSession issued =
        store.create(
            NewSession.builder()
                .userId(session.userId())
                .sessionId(session.sessionId())
                .accountType(session.accountType())
                .clientType(session.client().clientType())
                .deviceId(session.client().device().map(DeviceId::value).orElse(null))
                .createdAt(session.now())
                .expiresAt(session.expiresAt())
                .idleTimeout(session.lifetime().idle())
                .maxSessionsPerUser(session.maxSessionsPerUser())
                .build());
    return IssuedUserSession.of(issued.token().value(), issued.replacedSessionIds());
  }

  /// 删一条会话。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return 真的删掉了返回 `true`
  @Override
  public boolean revoke(AccountType accountType, long userId, long sessionId) {
    return store.delete(accountType, userId, sessionId);
  }

  /// 删一个用户的全部会话。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @return 真的删掉了的会话 ID
  @Override
  public List<Long> revokeAll(AccountType accountType, long userId) {
    return store.deleteAll(accountType, userId);
  }

  /// 删一个用户除指定一条之外的全部会话。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @param keepSessionId 要保留的会话 ID
  /// @return 真的删掉了的会话 ID
  @Override
  public List<Long> revokeAllExcept(AccountType accountType, long userId, long keepSessionId) {
    return store.deleteAllExcept(accountType, userId, keepSessionId);
  }
}

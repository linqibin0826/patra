package dev.patra.identity.infra.session.store;

import dev.patra.identity.domain.session.model.vo.ActiveUserSession;
import dev.patra.identity.domain.session.model.vo.DeviceId;
import dev.patra.identity.domain.session.model.vo.IssuedUserSession;
import dev.patra.identity.domain.session.model.vo.NewUserSession;
import dev.patra.identity.domain.session.model.vo.SessionRotation;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 会话存储端口的实现：把 domain 的记录转成会话存储的类型，交给 `RedisSessionStore`。
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

  /// 按令牌查会话并节流续期：格式不对的令牌不碰 Redis。
  ///
  /// @param token 令牌原文
  /// @return 当前用户；令牌格式不对或会话不在时为空
  @Override
  public Optional<CurrentUser> findAndTouch(String token) {
    return SessionToken.parse(token).flatMap(store::findAndTouch).map(StoredSession::toCurrentUser);
  }

  /// 换令牌、保留会话 ID；宽限期内不换。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @param grace 换令牌的宽限期
  /// @return 换令牌的结果；会话不在时为空
  @Override
  public Optional<SessionRotation> rotate(
      AccountType accountType, long userId, long sessionId, Duration grace) {
    return store
        .rotate(accountType, userId, sessionId, grace)
        .map(
            rotation ->
                rotation
                    .newToken()
                    .map(SessionToken::value)
                    .map(SessionRotation::rotated)
                    .orElseGet(SessionRotation::kept));
  }

  /// 读再认证时间。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return 再认证时间；会话不在时为空
  @Override
  public Optional<Instant> findAuthenticatedAt(
      AccountType accountType, long userId, long sessionId) {
    return store.find(accountType, userId, sessionId).map(StoredSession::authenticatedAt);
  }

  /// 列出活会话：只取会话 ID、创建时间、最后活跃。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @return 活会话
  @Override
  public List<ActiveUserSession> listActive(AccountType accountType, long userId) {
    return store.list(accountType, userId).stream()
        .map(
            session ->
                ActiveUserSession.of(
                    session.sessionId(), session.createdAt(), session.lastActiveAt()))
        .toList();
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

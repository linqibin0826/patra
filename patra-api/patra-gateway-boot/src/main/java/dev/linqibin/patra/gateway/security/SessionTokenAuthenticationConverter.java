package dev.linqibin.patra.gateway.security;

import dev.linqibin.patra.identity.session.RedisSessionStore;
import dev.linqibin.patra.identity.session.SessionStoreUnavailableException;
import dev.linqibin.patra.identity.session.SessionToken;
import dev.linqibin.patra.identity.session.StoredSession;
import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;

/// 把 `Authorization: Bearer` 里的会话令牌变成认证对象。
///
/// 不是「恰好一个 Bearer 头 + 会话令牌格式 + Redis 里有」的一律按匿名返回 `null`，是否放行交给
/// 路径规则；查会话顺手续期。Redis 暂时不可用抛 `AuthenticationServiceException`（503），
/// 其他失败抛 `SessionLookupFailedException`（500）。不打印请求头，异常文案不含令牌。
public final class SessionTokenAuthenticationConverter implements AuthenticationConverter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final RedisSessionStore sessions;

  /// 创建转换器。
  ///
  /// @param sessions 会话存储
  public SessionTokenAuthenticationConverter(RedisSessionStore sessions) {
    this.sessions = Objects.requireNonNull(sessions, "sessions 不能为 null");
  }

  /// 从请求头建立认证。
  ///
  /// @param request 当前请求
  /// @return 认证对象；按匿名处理时返回 `null`
  /// @throws AuthenticationServiceException 会话存储暂时不可用时
  /// @throws SessionLookupFailedException 查会话遇到非暂时失败时
  @Override
  public Authentication convert(HttpServletRequest request) {
    List<String> values = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
    if (values.size() != 1) {
      return null;
    }
    String value = values.getFirst().strip();
    if (!value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      return null;
    }
    Optional<SessionToken> token =
        SessionToken.parse(value.substring(BEARER_PREFIX.length()).strip());
    if (token.isEmpty()) {
      return null;
    }
    Optional<StoredSession> session;
    try {
      session = sessions.findAndTouch(token.get());
    } catch (SessionStoreUnavailableException e) {
      throw new AuthenticationServiceException("会话存储暂时不可用", e);
    } catch (RuntimeException e) {
      throw new SessionLookupFailedException(e);
    }
    return session.map(found -> new CurrentUserAuthentication(found.toCurrentUser())).orElse(null);
  }
}

package dev.patra.gateway.security;

import dev.patra.identity.api.session.dto.SessionIntrospectionReq;
import dev.patra.identity.api.session.dto.SessionIntrospectionResp;
import dev.patra.identity.api.session.endpoint.SessionEndpoint;
import dev.patra.tool.core.error.remote.RemoteCallException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.security.authentication.CurrentUserAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.web.client.ResourceAccessException;

/// 把 `Authorization: Bearer` 里的会话令牌变成认证对象：经 identity 的内部接口查会话（PAP-106）。
///
/// 不是「恰好一个非空 Bearer 头 + identity 说 active」的一律按匿名返回 `null`，是否放行交给路径规则；
/// 令牌格式只归 identity 判断，网关不预先校验。不缓存查询结果，登出、封禁即时生效。
///
/// identity 暂时不可用（回 503 + `DEP_UNAVAILABLE`、连不上或超时、注册中心里没有实例）抛
/// `AuthenticationServiceException`（503），其他失败抛 `SessionLookupFailedException`（500）。
/// 不打印请求头，异常文案不含令牌。
public final class SessionTokenAuthenticationConverter implements AuthenticationConverter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final SessionEndpoint sessions;

  /// 创建转换器。
  ///
  /// @param sessions identity 查会话接口的代理
  public SessionTokenAuthenticationConverter(SessionEndpoint sessions) {
    this.sessions = Objects.requireNonNull(sessions, "sessions 不能为 null");
  }

  /// 从请求头建立认证。
  ///
  /// @param request 当前请求
  /// @return 认证对象；按匿名处理时返回 `null`
  /// @throws AuthenticationServiceException identity 暂时不可用时
  /// @throws SessionLookupFailedException 查会话遇到其他失败时
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
    String token = value.substring(BEARER_PREFIX.length()).strip();
    if (token.isEmpty()) {
      return null;
    }
    SessionIntrospectionResp session = introspect(token);
    if (!session.active()) {
      return null;
    }
    return new CurrentUserAuthentication(
        CurrentUser.of(
            session.userId(), session.sessionId(), session.accountType(), session.clientType()));
  }

  /// 调一次 identity 并翻译失败。
  ///
  /// @param token 令牌原文
  /// @return 查询结果
  /// @throws AuthenticationServiceException identity 暂时不可用时
  /// @throws SessionLookupFailedException 其他失败时
  private SessionIntrospectionResp introspect(String token) {
    try {
      return sessions.introspect(SessionIntrospectionReq.of(token));
    } catch (RemoteCallException e) {
      if (e.getErrorTraits().contains(StandardErrorTrait.DEP_UNAVAILABLE)) {
        throw new AuthenticationServiceException("会话存储暂时不可用", e);
      }
      throw new SessionLookupFailedException(e);
    } catch (ResourceAccessException | IllegalStateException e) {
      throw new AuthenticationServiceException("identity 暂时不可用", e);
    } catch (RuntimeException e) {
      throw new SessionLookupFailedException(e);
    }
  }
}

package dev.patra.identity.adapter.account.security;

import dev.patra.identity.domain.account.port.oauth.AuthorizationRequestStorePort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import tools.jackson.databind.json.JsonMapper;

/// 按 `state` 把授权请求存进 Redis，identity 不建 `HttpSession`（v0.19 决策 B）。框架默认的实现存在会话里。
///
/// 存的是 `state`、`nonce`、PKCE 的 `code_verifier` 这些授权请求自己的东西，不是令牌，令牌不经过这里。
@RequiredArgsConstructor
public class StoredAuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

  /// 回调取出的授权请求放在这个请求属性里：框架取出后不再暴露它，成功处理器要从中读用途和发起人。
  public static final String CONSUMED_REQUEST_ATTRIBUTE =
      StoredAuthorizationRequestRepository.class.getName() + ".consumed";

  /// 授权请求的有效期：用户在平台上操作的时间。
  static final Duration TTL = Duration.ofMinutes(10);

  private final AuthorizationRequestStorePort store;
  private final JsonMapper jsonMapper;

  /// 按请求参数里的 `state` 读，不删。
  ///
  /// @param request 请求
  /// @return 授权请求；没有时为 `null`
  @Override
  public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    return stateOf(request).flatMap(store::find).map(this::deserialize).orElse(null);
  }

  /// 存。框架约定传 `null` 表示删除。
  ///
  /// @param authorizationRequest 授权请求，可以为 `null`
  /// @param request 请求
  /// @param response 响应
  @Override
  public void saveAuthorizationRequest(
      OAuth2AuthorizationRequest authorizationRequest,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (authorizationRequest == null) {
      removeAuthorizationRequest(request, response);
      return;
    }
    store.save(
        authorizationRequest.getState(),
        jsonMapper.writeValueAsString(StoredRequest.from(authorizationRequest)),
        TTL);
  }

  /// 取出并删除：同一个回调第二次来时拿不到，框架报 `authorization_request_not_found`。
  ///
  /// Redis 暂时不可用时转成框架认识的认证异常，才能走到失败处理器；其他异常会穿过过滤器变成容器的 500。
  ///
  /// @param request 请求
  /// @param response 响应
  /// @return 授权请求；没有时为 `null`
  @Override
  public OAuth2AuthorizationRequest removeAuthorizationRequest(
      HttpServletRequest request, HttpServletResponse response) {
    Optional<String> payload;
    try {
      payload = stateOf(request).flatMap(store::consume);
    } catch (RuntimeException e) {
      throw new InternalAuthenticationServiceException("授权请求存储不可用", e);
    }
    OAuth2AuthorizationRequest consumed = payload.map(this::deserialize).orElse(null);
    request.setAttribute(CONSUMED_REQUEST_ATTRIBUTE, consumed);
    return consumed;
  }

  /// 请求里的 `state`。
  ///
  /// @param request 请求
  /// @return `state`；没有时为空
  private static Optional<String> stateOf(HttpServletRequest request) {
    return Optional.ofNullable(request.getParameter(OAuth2ParameterNames.STATE))
        .filter(state -> !state.isBlank());
  }

  /// 反序列化。
  ///
  /// @param payload JSON
  /// @return 授权请求
  private OAuth2AuthorizationRequest deserialize(String payload) {
    return jsonMapper.readValue(payload, StoredRequest.class).toAuthorizationRequest();
  }

  /// 存进 Redis 的形状：授权请求本身不是为序列化设计的。
  ///
  /// @param authorizationUri 平台的授权地址
  /// @param clientId 客户端 ID
  /// @param redirectUri 回调地址，换令牌时要原样带上
  /// @param scopes 权限范围
  /// @param state `state`
  /// @param additionalParameters 额外参数：`nonce`、`code_challenge`
  /// @param attributes 属性：注册 ID、`nonce` 原文、`code_verifier`、用途与发起人
  /// @param authorizationRequestUri 完整的授权地址
  @Builder
  record StoredRequest(
      String authorizationUri,
      String clientId,
      String redirectUri,
      Set<String> scopes,
      String state,
      Map<String, Object> additionalParameters,
      Map<String, Object> attributes,
      String authorizationRequestUri) {

    /// 从授权请求取值。
    ///
    /// @param request 授权请求
    /// @return 存储形状
    static StoredRequest from(OAuth2AuthorizationRequest request) {
      return StoredRequest.builder()
          .authorizationUri(request.getAuthorizationUri())
          .clientId(request.getClientId())
          .redirectUri(request.getRedirectUri())
          .scopes(request.getScopes())
          .state(request.getState())
          .additionalParameters(request.getAdditionalParameters())
          .attributes(request.getAttributes())
          .authorizationRequestUri(request.getAuthorizationRequestUri())
          .build();
    }

    /// 还原成授权请求。
    ///
    /// @return 授权请求
    OAuth2AuthorizationRequest toAuthorizationRequest() {
      return OAuth2AuthorizationRequest.authorizationCode()
          .authorizationUri(authorizationUri)
          .clientId(clientId)
          .redirectUri(redirectUri)
          .scopes(scopes)
          .state(state)
          .additionalParameters(additionalParameters)
          .attributes(attrs -> attrs.putAll(attributes))
          .authorizationRequestUri(authorizationRequestUri)
          .build();
    }
  }
}

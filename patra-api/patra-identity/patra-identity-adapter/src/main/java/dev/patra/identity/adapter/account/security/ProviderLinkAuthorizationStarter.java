package dev.patra.identity.adapter.account.security;

import dev.patra.identity.domain.account.exception.UnknownIdentityProviderException;
import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import dev.patra.tool.core.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/// 发起绑定：用官方的解析器造授权请求，把发起的用户和会话记进它的属性，存进 Redis，回调时核对。
///
/// 不走 `/oauth2/authorization/{provider}`：框架的重定向过滤器把 resolver 的所有异常都变成 500，再认证的 403 回不到门户。
@RequiredArgsConstructor
public class ProviderLinkAuthorizationStarter {

  private final ClientRegistrationRepository registrations;
  private final OAuth2AuthorizationRequestResolver resolver;
  private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> repository;

  /// 造好授权请求并存起来。
  ///
  /// @param provider 平台
  /// @param initiator 发起人
  /// @param request 请求
  /// @param response 响应
  /// @return 平台的授权地址
  /// @throws UnknownIdentityProviderException 这一家还没配
  public String start(
      IdentityProvider provider,
      CurrentUser initiator,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (registrations.findByRegistrationId(provider.registrationId()) == null) {
      throw new UnknownIdentityProviderException();
    }
    ProviderAuthorizationIntent.Link intent =
        new ProviderAuthorizationIntent.Link(initiator.userId(), initiator.sessionId());
    OAuth2AuthorizationRequest authorizationRequest =
        OAuth2AuthorizationRequest.from(resolver.resolve(request, provider.registrationId()))
            .attributes(attributes -> attributes.putAll(intent.attributes()))
            .build();
    repository.saveAuthorizationRequest(authorizationRequest, request, response);
    return authorizationRequest.getAuthorizationRequestUri();
  }
}

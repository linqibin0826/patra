package dev.patra.identity.adapter.account.security;

import dev.patra.identity.domain.account.exception.ProviderAuthorizationRejectedException;
import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import dev.patra.identity.domain.account.model.vo.ProviderAccount;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

/// 把框架的 `OAuth2User` 转成领域的 `ProviderAccount`：框架类型到这里为止（v0.19 决策 F）。
///
/// 主体就是 `OAuth2User.getName()`，也就是配置里的 `user-name-attribute`：Google、ORCID 是 `sub`，GitHub 是数字 `id`。
public final class ProviderAccountMapper {

  /// 只有静态方法。
  private ProviderAccountMapper() {}

  /// 转换。
  ///
  /// @param authentication 框架的认证结果
  /// @return 平台确认过的账号
  /// @throws ProviderAuthorizationRejectedException 平台不认识，或主体的形状不对
  public static ProviderAccount toAccount(OAuth2AuthenticationToken authentication) {
    IdentityProvider provider =
        IdentityProvider.fromRegistrationId(authentication.getAuthorizedClientRegistrationId())
            .orElseThrow(ProviderAuthorizationRejectedException::new);
    OAuth2User user = authentication.getPrincipal();
    String subject =
        provider
            .normalizeSubject(user.getName())
            .orElseThrow(ProviderAuthorizationRejectedException::new);
    String email = stringAttribute(user, "email");
    return switch (provider) {
      case GOOGLE ->
          ProviderAccount.builder()
              .provider(provider)
              .subject(subject)
              .accountLabel(email != null ? email : subject)
              .suggestedEmail(email)
              .build();
      case GITHUB -> {
        String login = stringAttribute(user, "login");
        yield ProviderAccount.builder()
            .provider(provider)
            .subject(subject)
            .accountLabel(login != null ? login : subject)
            .suggestedUsername(login)
            .suggestedEmail(email)
            .build();
      }
      case ORCID ->
          ProviderAccount.builder()
              .provider(provider)
              .subject(subject)
              .accountLabel(subject)
              .build();
    };
  }

  /// 读一个字符串属性。
  ///
  /// @param user 用户
  /// @param name 属性名
  /// @return 值；没有时为 `null`
  private static String stringAttribute(OAuth2User user, String name) {
    return user.getAttribute(name) instanceof String value ? value : null;
  }
}

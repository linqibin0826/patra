package dev.patra.identity.config;

import dev.patra.identity.config.IdentityProperties.OAuth.Provider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/// 配好了的第三方平台：可能一家都没有（本机没放 `oauth.env`）。框架的 `InMemoryClientRegistrationRepository`
/// 不接受空集合，所以自己实现。
@Slf4j
public final class ConfiguredClientRegistrations implements ClientRegistrationRepository {

  private final Map<String, ClientRegistration> registrations;

  /// 创建。
  ///
  /// @param registrations 注册信息，键是注册 ID
  private ConfiguredClientRegistrations(Map<String, ClientRegistration> registrations) {
    this.registrations = Map.copyOf(registrations);
  }

  /// 从配置建：没配客户端 ID 或密钥的跳过，记一条 `WARN`。
  ///
  /// @param providers 配置，键是注册 ID
  /// @return 仓库
  public static ConfiguredClientRegistrations of(Map<String, Provider> providers) {
    Map<String, ClientRegistration> registrations = new LinkedHashMap<>();
    providers.forEach(
        (id, provider) -> {
          if (provider.configured()) {
            registrations.put(id, toRegistration(id, provider));
          } else {
            log.warn("第三方登录平台 {} 没配客户端 ID 或密钥，入口不可用", id);
          }
        });
    return new ConfiguredClientRegistrations(registrations);
  }

  /// 按注册 ID 查。
  ///
  /// @param registrationId 注册 ID
  /// @return 注册信息；没配时为 `null`
  @Override
  public ClientRegistration findByRegistrationId(String registrationId) {
    return registrations.get(registrationId);
  }

  /// 配好了的注册 ID。
  ///
  /// @return 注册 ID
  public Set<String> registrationIds() {
    return registrations.keySet();
  }

  /// 建一家的注册信息。三家换令牌都按文档走 `client_secret_post`。
  ///
  /// @param id 注册 ID
  /// @param provider 配置
  /// @return 注册信息
  private static ClientRegistration toRegistration(String id, Provider provider) {
    return ClientRegistration.withRegistrationId(id)
        .clientId(provider.clientId())
        .clientSecret(provider.clientSecret())
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri(provider.redirectUri())
        .scope(provider.scopes())
        .authorizationUri(provider.authorizationUri())
        .tokenUri(provider.tokenUri())
        .jwkSetUri(provider.jwkSetUri())
        .userInfoUri(provider.userInfoUri())
        .userNameAttributeName(provider.userNameAttribute())
        .issuerUri(provider.issuer())
        .clientSettings(
            ClientRegistration.ClientSettings.builder()
                .requireProofKey(provider.requireProofKey())
                .build())
        .build();
  }
}

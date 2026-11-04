package dev.patra.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.config.IdentityProperties.OAuth.Provider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/// ConfiguredClientRegistrations 单元测试：没配的平台不注册，注册信息字段齐全。
@DisplayName("ConfiguredClientRegistrations 单元测试")
class ConfiguredClientRegistrationsTest {

  private static Provider.ProviderBuilder google() {
    return Provider.builder()
        .clientId("client-1")
        .clientSecret("secret-1")
        .redirectUri("https://patra.test/api/auth/oauth/callback/google")
        .scopes(List.of("openid", "email"))
        .authorizationUri("https://provider.test/authorize")
        .tokenUri("https://provider.test/token")
        .jwkSetUri("https://provider.test/jwks")
        .userNameAttribute("sub")
        .issuer("https://provider.test")
        .requireProofKey(true);
  }

  @Test
  @DisplayName("配好的平台：字段原样进注册信息，换令牌走 client_secret_post")
  void should_build_registration() {
    ClientRegistration registration =
        ConfiguredClientRegistrations.of(Map.of("google", google().build()))
            .findByRegistrationId("google");

    assertThat(registration.getClientId()).isEqualTo("client-1");
    assertThat(registration.getClientSecret()).isEqualTo("secret-1");
    assertThat(registration.getRedirectUri())
        .isEqualTo("https://patra.test/api/auth/oauth/callback/google");
    assertThat(registration.getScopes()).containsExactlyInAnyOrder("openid", "email");
    assertThat(registration.getClientAuthenticationMethod())
        .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_POST);
    assertThat(registration.getProviderDetails().getIssuerUri()).isEqualTo("https://provider.test");
    assertThat(registration.getClientSettings().isRequireProofKey()).isTrue();
  }

  @Test
  @DisplayName("客户端 ID 或密钥为空：不注册，其余照常")
  void should_skip_unconfigured_provider() {
    ConfiguredClientRegistrations registrations =
        ConfiguredClientRegistrations.of(
            Map.of(
                "google", google().build(),
                "github", google().clientId("").build(),
                "orcid", google().clientSecret(null).build()));

    assertThat(registrations.registrationIds()).containsExactly("google");
    assertThat(registrations.findByRegistrationId("orcid")).isNull();
  }

  @Test
  @DisplayName("一家都没配：能建出空仓库，框架的内存实现不接受空集合")
  void should_allow_no_provider() {
    assertThat(ConfiguredClientRegistrations.of(Map.of()).registrationIds()).isEmpty();
  }

  @Test
  @DisplayName("配置的 toString 不输出密钥")
  void should_hide_secret_in_to_string() {
    assertThat(google().build().toString()).doesNotContain("secret-1");
  }
}

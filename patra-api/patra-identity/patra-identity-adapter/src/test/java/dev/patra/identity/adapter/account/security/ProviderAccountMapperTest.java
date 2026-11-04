package dev.patra.identity.adapter.account.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.exception.ProviderAuthorizationRejectedException;
import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import dev.patra.identity.domain.account.model.vo.ProviderAccount;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

/// ProviderAccountMapper 单元测试：三家的主体、账号标识和预填值。
@DisplayName("ProviderAccountMapper 单元测试")
class ProviderAccountMapperTest {

  private static OAuth2AuthenticationToken token(
      String registrationId, String nameAttribute, Map<String, Object> attributes) {
    DefaultOAuth2User user = new DefaultOAuth2User(List.of(), attributes, nameAttribute);
    return new OAuth2AuthenticationToken(user, List.of(), registrationId);
  }

  @Test
  @DisplayName("Google：主体是 sub，账号标识和预填邮箱是 email")
  void should_map_google() {
    ProviderAccount account =
        ProviderAccountMapper.toAccount(
            token("google", "sub", Map.of("sub", "1098", "email", "a@example.com")));

    assertThat(account.provider()).isEqualTo(IdentityProvider.GOOGLE);
    assertThat(account.subject()).isEqualTo("1098");
    assertThat(account.accountLabel()).isEqualTo("a@example.com");
    assertThat(account.suggestedEmail()).isEqualTo("a@example.com");
    assertThat(account.suggestedUsername()).isNull();
  }

  @Test
  @DisplayName("Google 没给邮箱：账号标识退回主体")
  void should_fall_back_to_subject_when_google_has_no_email() {
    ProviderAccount account =
        ProviderAccountMapper.toAccount(token("google", "sub", Map.of("sub", "1098")));

    assertThat(account.accountLabel()).isEqualTo("1098");
    assertThat(account.suggestedEmail()).isNull();
  }

  @Test
  @DisplayName("GitHub：主体是数字 id 转字符串，账号标识和预填用户名是 login")
  void should_map_github() {
    ProviderAccount account =
        ProviderAccountMapper.toAccount(
            token("github", "id", Map.of("id", 583231, "login", "octocat")));

    assertThat(account.subject()).isEqualTo("583231");
    assertThat(account.accountLabel()).isEqualTo("octocat");
    assertThat(account.suggestedUsername()).isEqualTo("octocat");
    assertThat(account.suggestedEmail()).isNull();
  }

  @Test
  @DisplayName("ORCID：主体规范化成裸 iD，账号标识也是它")
  void should_map_orcid_to_bare_id() {
    ProviderAccount account =
        ProviderAccountMapper.toAccount(
            token("orcid", "sub", Map.of("sub", "https://orcid.org/0000-0002-1825-0097")));

    assertThat(account.subject()).isEqualTo("0000-0002-1825-0097");
    assertThat(account.accountLabel()).isEqualTo("0000-0002-1825-0097");
  }

  @Test
  @DisplayName("ORCID 主体形状不对：按协议失败处理，不建任何东西")
  void should_reject_malformed_orcid_subject() {
    assertThatThrownBy(
            () ->
                ProviderAccountMapper.toAccount(token("orcid", "sub", Map.of("sub", "not-an-id"))))
        .isInstanceOf(ProviderAuthorizationRejectedException.class);
  }
}

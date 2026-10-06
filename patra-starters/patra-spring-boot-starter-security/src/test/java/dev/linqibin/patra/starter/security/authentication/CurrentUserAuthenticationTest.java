package dev.linqibin.patra.starter.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

/// CurrentUserAuthentication 单元测试。
@DisplayName("CurrentUserAuthentication 单元测试")
class CurrentUserAuthenticationTest {

  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);

  @Test
  @DisplayName("构造出来就是已认证状态，主体是当前用户，没有凭据和权限")
  void should_be_authenticated_with_user_as_principal() {
    CurrentUserAuthentication authentication = new CurrentUserAuthentication(USER);

    assertThat(authentication.isAuthenticated()).isTrue();
    assertThat(authentication.getPrincipal()).isEqualTo(USER);
    assertThat(authentication.getCredentials()).isNull();
    assertThat(authentication.getAuthorities()).isEmpty();
  }

  @Test
  @DisplayName("名字只含用户 ID，不带整条记录")
  void should_use_user_id_as_name() {
    assertThat(new CurrentUserAuthentication(USER).getName()).isEqualTo("1001");
  }

  @Test
  @DisplayName("可以带上权限列表")
  void should_expose_given_authorities() {
    CurrentUserAuthentication authentication =
        new CurrentUserAuthentication(USER, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));

    assertThat(authentication.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_ADMIN");
  }

  @Test
  @DisplayName("主体为空时拒绝创建")
  void should_reject_null_principal() {
    assertThatThrownBy(() -> new CurrentUserAuthentication(null))
        .isInstanceOf(NullPointerException.class);
  }
}

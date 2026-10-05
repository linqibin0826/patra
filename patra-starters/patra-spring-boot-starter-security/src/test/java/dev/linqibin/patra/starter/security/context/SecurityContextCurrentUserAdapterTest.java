package dev.linqibin.patra.starter.security.context;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/// SecurityContextCurrentUserAdapter 单元测试。
@DisplayName("SecurityContextCurrentUserAdapter 单元测试")
class SecurityContextCurrentUserAdapterTest {

  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.PORTAL, ClientType.WEB);

  private final SecurityContextCurrentUserAdapter adapter = new SecurityContextCurrentUserAdapter();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("安全上下文为空时没有当前用户")
  void should_be_empty_when_security_context_is_empty() {
    assertThat(adapter.current()).isEmpty();
  }

  @Test
  @DisplayName("认证对象的主体是 CurrentUser 时返回它")
  void should_return_user_when_principal_is_current_user() {
    SecurityContextHolder.getContext().setAuthentication(new CurrentUserAuthentication(USER));

    assertThat(adapter.current()).contains(USER);
  }

  @Test
  @DisplayName("匿名认证对象不会被当成用户")
  void should_be_empty_when_authentication_is_anonymous() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

    assertThat(adapter.current()).isEmpty();
  }

  @Test
  @DisplayName("主体是别的类型时没有当前用户")
  void should_be_empty_when_principal_is_other_type() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("someone", "n/a"));

    assertThat(adapter.current()).isEmpty();
  }
}

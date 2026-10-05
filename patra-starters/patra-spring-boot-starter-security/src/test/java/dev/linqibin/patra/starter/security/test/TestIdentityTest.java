package dev.linqibin.patra.starter.security.test;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.header.IdentityHeaders;
import dev.linqibin.patra.starter.security.header.IdentityParseResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/// TestIdentity 单元测试。
@DisplayName("TestIdentity 单元测试")
class TestIdentityTest {

  @Test
  @DisplayName("默认的请求头带内部令牌，并能解析回默认用户")
  void should_produce_headers_that_parse_back_to_default_user() {
    HttpHeaders headers = TestIdentity.headers();

    assertThat(headers.getFirst(IdentityHeaders.GATEWAY_TOKEN))
        .isEqualTo(TestIdentity.GATEWAY_TOKEN);
    assertThat(IdentityHeaders.parse(headers))
        .isEqualTo(
            IdentityParseResult.identified(
                CurrentUser.of(1001L, 2001L, AccountType.PORTAL, ClientType.WEB)));
  }

  @Test
  @DisplayName("可以只指定用户 ID")
  void should_use_given_user_id() {
    assertThat(IdentityHeaders.parse(TestIdentity.headers(42L)))
        .isEqualTo(
            IdentityParseResult.identified(
                CurrentUser.of(42L, 2001L, AccountType.PORTAL, ClientType.WEB)));
  }

  @Test
  @DisplayName("可以指定整个用户")
  void should_use_given_user() {
    CurrentUser user = CurrentUser.of(7L, 8L, AccountType.PORTAL, ClientType.WEB);

    assertThat(IdentityHeaders.parse(TestIdentity.headers(user)))
        .isEqualTo(IdentityParseResult.identified(user));
  }
}

package dev.linqibin.patra.starter.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.header.IdentityHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;

/// GatewayHeaderAuthenticationConverter 单元测试：spec 第 8.2 节的四种情况。
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("GatewayHeaderAuthenticationConverter 单元测试")
class GatewayHeaderAuthenticationConverterTest {

  private static final String TOKEN = "test-gateway-token";
  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);

  private final GatewayHeaderAuthenticationConverter converter =
      new GatewayHeaderAuthenticationConverter(TOKEN);

  private static MockHttpServletRequest request() {
    return new MockHttpServletRequest("GET", "/probe/whoami");
  }

  private static void addIdentityHeaders(MockHttpServletRequest request) {
    request.addHeader(IdentityHeaders.USER_ID, "1001");
    request.addHeader(IdentityHeaders.SESSION_ID, "2001");
    request.addHeader(IdentityHeaders.ACCOUNT_TYPE, "user");
    request.addHeader(IdentityHeaders.CLIENT_TYPE, "web");
  }

  @Test
  @DisplayName("内部令牌正确、身份头齐全且合法：得到认证对象")
  void should_authenticate_when_token_valid_and_identity_headers_complete() {
    MockHttpServletRequest request = request();
    request.addHeader(IdentityHeaders.GATEWAY_TOKEN, TOKEN);
    addIdentityHeaders(request);

    Authentication authentication = converter.convert(request);

    assertThat(authentication).isInstanceOf(CurrentUserAuthentication.class);
    assertThat(authentication.getPrincipal()).isEqualTo(USER);
  }

  @Test
  @DisplayName("内部令牌正确、没有身份头：匿名")
  void should_return_null_when_token_valid_and_no_identity_headers() {
    MockHttpServletRequest request = request();
    request.addHeader(IdentityHeaders.GATEWAY_TOKEN, TOKEN);

    assertThat(converter.convert(request)).isNull();
  }

  @Test
  @DisplayName("内部令牌缺失：身份头不被采信，并记一条警告")
  void should_ignore_identity_and_warn_when_token_missing(CapturedOutput output) {
    MockHttpServletRequest request = request();
    addIdentityHeaders(request);

    assertThat(converter.convert(request)).isNull();
    assertThat(output).contains("内部令牌");
  }

  @Test
  @DisplayName("内部令牌不对：身份头不被采信，警告里不出现令牌的值")
  void should_ignore_identity_and_warn_when_token_wrong(CapturedOutput output) {
    MockHttpServletRequest request = request();
    request.addHeader(IdentityHeaders.GATEWAY_TOKEN, "wrong-token");
    addIdentityHeaders(request);

    assertThat(converter.convert(request)).isNull();
    assertThat(output).contains("内部令牌").doesNotContain("wrong-token").doesNotContain(TOKEN);
  }

  @Test
  @DisplayName("内部令牌头出现多次：按令牌不对处理")
  void should_ignore_identity_when_token_repeated() {
    MockHttpServletRequest request = request();
    request.addHeader(IdentityHeaders.GATEWAY_TOKEN, TOKEN);
    request.addHeader(IdentityHeaders.GATEWAY_TOKEN, TOKEN);
    addIdentityHeaders(request);

    assertThat(converter.convert(request)).isNull();
  }

  @Test
  @DisplayName("内部令牌缺失、也没有身份头：匿名，不记警告")
  void should_not_warn_when_token_missing_and_no_identity_headers(CapturedOutput output) {
    assertThat(converter.convert(request())).isNull();
    assertThat(output).doesNotContain("内部令牌");
  }

  @Test
  @DisplayName("内部令牌不对时，即使身份头不合法也按匿名处理，不抛异常")
  void should_return_null_when_token_wrong_and_identity_headers_malformed() {
    MockHttpServletRequest request = request();
    request.addHeader(IdentityHeaders.GATEWAY_TOKEN, "wrong-token");
    request.addHeader(IdentityHeaders.USER_ID, "abc");

    assertThat(converter.convert(request)).isNull();
  }

  @Test
  @DisplayName("内部令牌正确、身份头只有一部分：抛出身份头不合法")
  void should_throw_malformed_identity_when_token_valid_and_identity_partial() {
    MockHttpServletRequest request = request();
    request.addHeader(IdentityHeaders.GATEWAY_TOKEN, TOKEN);
    request.addHeader(IdentityHeaders.USER_ID, "1001");

    assertThatThrownBy(() -> converter.convert(request))
        .isInstanceOf(MalformedIdentityException.class);
  }

  @Test
  @DisplayName("内部令牌正确、用户 ID 不是正整数：抛出身份头不合法，消息里没有头的值")
  void should_throw_malformed_identity_when_token_valid_and_user_id_invalid() {
    MockHttpServletRequest request = request();
    request.addHeader(IdentityHeaders.GATEWAY_TOKEN, TOKEN);
    request.addHeader(IdentityHeaders.USER_ID, "not-a-number");
    request.addHeader(IdentityHeaders.SESSION_ID, "2001");
    request.addHeader(IdentityHeaders.ACCOUNT_TYPE, "user");
    request.addHeader(IdentityHeaders.CLIENT_TYPE, "web");

    assertThatThrownBy(() -> converter.convert(request))
        .isInstanceOf(MalformedIdentityException.class)
        .hasMessageContaining(IdentityHeaders.USER_ID)
        .hasMessageNotContaining("not-a-number");
  }

  @Test
  @DisplayName("请求头的名字全小写时结果相同")
  void should_authenticate_when_header_names_are_lowercase() {
    MockHttpServletRequest request = request();
    request.addHeader("x-patra-gateway-token", TOKEN);
    request.addHeader("x-patra-user-id", "1001");
    request.addHeader("x-patra-session-id", "2001");
    request.addHeader("x-patra-account-type", "user");
    request.addHeader("x-patra-client-type", "web");

    Authentication authentication = converter.convert(request);

    assertThat(authentication).isInstanceOf(CurrentUserAuthentication.class);
    assertThat(authentication.getPrincipal()).isEqualTo(USER);
  }
}

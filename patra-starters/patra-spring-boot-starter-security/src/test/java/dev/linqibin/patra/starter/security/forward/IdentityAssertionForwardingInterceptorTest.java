package dev.linqibin.patra.starter.security.forward;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import dev.linqibin.patra.starter.security.context.CurrentUserRunner;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

/// IdentityAssertionForwardingInterceptor 单元测试。
@DisplayName("IdentityAssertionForwardingInterceptor 单元测试")
class IdentityAssertionForwardingInterceptorTest {

  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);
  private static final String ASSERTION = "header.payload.signature";

  private final IdentityAssertionForwardingInterceptor interceptor =
      new IdentityAssertionForwardingInterceptor();
  private final SecurityContextHolderStrategy strategy =
      SecurityContextHolder.getContextHolderStrategy();

  @AfterEach
  void clearContext() {
    strategy.clearContext();
  }

  @Test
  @DisplayName("当前用户带断言：出站请求带上 Authorization: Bearer 断言")
  void should_forward_assertion_when_current_user_has_one() throws IOException {
    authenticate(new CurrentUserAuthentication(USER, ASSERTION));

    HttpHeaders sent = send(new MockClientHttpRequest(HttpMethod.GET, uri()));

    assertThat(sent.get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer " + ASSERTION);
  }

  @Test
  @DisplayName("当前用户没有断言（比如网关构造的）：不加头")
  void should_not_add_header_for_user_without_assertion() throws IOException {
    authenticate(new CurrentUserAuthentication(USER));

    HttpHeaders sent = send(new MockClientHttpRequest(HttpMethod.GET, uri()));

    assertThat(sent.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
  }

  @Test
  @DisplayName("没有安全上下文：不加头")
  void should_not_add_header_without_security_context() throws IOException {
    HttpHeaders sent = send(new MockClientHttpRequest(HttpMethod.GET, uri()));

    assertThat(sent.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
  }

  @Test
  @DisplayName("CurrentUserRunner 放进去的用户：不加头")
  void should_not_add_header_inside_current_user_runner() {
    HttpHeaders sent =
        CurrentUserRunner.callAs(
            USER,
            () -> {
              try {
                return send(new MockClientHttpRequest(HttpMethod.GET, uri()));
              } catch (IOException e) {
                throw new IllegalStateException(e);
              }
            });

    assertThat(sent.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
  }

  @Test
  @DisplayName("业务代码自己设了 Authorization 头：被断言覆盖，不会两个并存")
  void should_overwrite_existing_authorization_header() throws IOException {
    authenticate(new CurrentUserAuthentication(USER, ASSERTION));
    MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, uri());
    request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");

    HttpHeaders sent = send(request);

    assertThat(sent.get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer " + ASSERTION);
  }

  /// 把认证对象放进当前线程的安全上下文。
  private void authenticate(Authentication authentication) {
    SecurityContext context = strategy.createEmptyContext();
    context.setAuthentication(authentication);
    strategy.setContext(context);
  }

  /// 经拦截器发出请求，返回真正发出去的请求头。
  private HttpHeaders send(MockClientHttpRequest request) throws IOException {
    HttpHeaders sent = new HttpHeaders();
    interceptor.intercept(
        request,
        new byte[0],
        (forwarded, body) -> {
          sent.addAll(forwarded.getHeaders());
          return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        });
    return sent;
  }

  private static URI uri() {
    return URI.create("http://patra-registry/_internal/provenances");
  }
}

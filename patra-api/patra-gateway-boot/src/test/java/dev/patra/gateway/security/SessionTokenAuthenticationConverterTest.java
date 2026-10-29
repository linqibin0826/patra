package dev.patra.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.api.session.dto.SessionIntrospectionReq;
import dev.patra.identity.api.session.dto.SessionIntrospectionResp;
import dev.patra.identity.api.session.endpoint.SessionEndpoint;
import dev.patra.tool.core.error.remote.RemoteCallException;
import dev.patra.tool.core.error.trait.ErrorTrait;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.security.authentication.CurrentUserAuthentication;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/// 网关鉴权设计第 7.1 节那张表，查会话改为调 identity 后（PAP-106 设计第 6 节）：
/// 前几行当匿名、不调或调了 inactive；后几行分别是 503 与 500 对应的异常。
class SessionTokenAuthenticationConverterTest {

  private static final String TOKEN = "patra_user_Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy";
  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);

  private final SessionEndpoint sessions = mock(SessionEndpoint.class);
  private final SessionTokenAuthenticationConverter converter =
      new SessionTokenAuthenticationConverter(sessions);

  @Test
  void should_return_null_without_authorization_header() {
    assertThat(converter.convert(request())).isNull();
    verifyNoInteractions(sessions);
  }

  @Test
  void should_return_null_for_multiple_authorization_headers() {
    assertThat(converter.convert(request("Bearer " + TOKEN, "Bearer " + TOKEN))).isNull();
    verifyNoInteractions(sessions);
  }

  @Test
  void should_return_null_for_non_bearer_scheme() {
    assertThat(converter.convert(request("Basic dXNlcjpwYXNz"))).isNull();
    verifyNoInteractions(sessions);
  }

  @Test
  void should_return_null_for_empty_bearer_value() {
    assertThat(converter.convert(request("Bearer"))).isNull();
    assertThat(converter.convert(request("Bearer    "))).isNull();
    verifyNoInteractions(sessions);
  }

  @Test
  void should_return_null_when_identity_reports_inactive() {
    when(sessions.introspect(SessionIntrospectionReq.of(TOKEN)))
        .thenReturn(SessionIntrospectionResp.inactive());

    assertThat(converter.convert(request("Bearer " + TOKEN))).isNull();
  }

  @Test
  void should_authenticate_when_identity_reports_active() {
    when(sessions.introspect(SessionIntrospectionReq.of(TOKEN))).thenReturn(active());

    Authentication authentication = converter.convert(request("Bearer " + TOKEN));

    assertThat(authentication).isInstanceOf(CurrentUserAuthentication.class);
    assertThat(authentication.getPrincipal()).isEqualTo(USER);
    assertThat(authentication.getCredentials()).isNull();
    assertThat(authentication.isAuthenticated()).isTrue();
  }

  @Test
  void should_accept_lowercase_scheme_and_surrounding_whitespace() {
    when(sessions.introspect(SessionIntrospectionReq.of(TOKEN))).thenReturn(active());

    Authentication authentication = converter.convert(request(" bearer  " + TOKEN + " "));

    assertThat(authentication.getPrincipal()).isEqualTo(USER);
  }

  @Test
  void should_translate_identity_dependency_unavailable_into_authentication_service_exception() {
    when(sessions.introspect(any()))
        .thenThrow(remote(503, Set.of(StandardErrorTrait.DEP_UNAVAILABLE)));

    assertUnavailable(RemoteCallException.class);
  }

  @Test
  void should_translate_unreachable_identity_into_authentication_service_exception() {
    when(sessions.introspect(any()))
        .thenThrow(new ResourceAccessException("I/O error", new IOException("refused")));

    assertUnavailable(ResourceAccessException.class);
  }

  @Test
  void should_translate_missing_identity_instance_into_authentication_service_exception() {
    when(sessions.introspect(any()))
        .thenThrow(new IllegalStateException("No instances available for patra-identity"));

    assertUnavailable(IllegalStateException.class);
  }

  @Test
  void should_wrap_other_identity_errors_as_lookup_failed() {
    when(sessions.introspect(any())).thenThrow(remote(500, Set.of()));

    assertThatThrownBy(() -> converter.convert(request("Bearer " + TOKEN)))
        .isInstanceOf(SessionLookupFailedException.class)
        .hasCauseInstanceOf(RemoteCallException.class)
        .hasMessageNotContaining(TOKEN);
  }

  @Test
  void should_wrap_unexpected_client_failures_as_lookup_failed() {
    when(sessions.introspect(any())).thenThrow(new RestClientException("响应解析失败"));

    assertThatThrownBy(() -> converter.convert(request("Bearer " + TOKEN)))
        .isInstanceOf(SessionLookupFailedException.class)
        .hasCauseInstanceOf(RestClientException.class)
        .hasMessageNotContaining(TOKEN);
  }

  private void assertUnavailable(Class<? extends Throwable> cause) {
    assertThatThrownBy(() -> converter.convert(request("Bearer " + TOKEN)))
        .isInstanceOf(AuthenticationServiceException.class)
        .isNotInstanceOf(SessionLookupFailedException.class)
        .hasCauseInstanceOf(cause)
        .hasMessageNotContaining(TOKEN);
  }

  private static SessionIntrospectionResp active() {
    return SessionIntrospectionResp.builder()
        .active(true)
        .userId(USER.userId())
        .sessionId(USER.sessionId())
        .accountType(USER.accountType())
        .clientType(USER.clientType())
        .build();
  }

  private static RemoteCallException remote(int status, Set<ErrorTrait> traits) {
    return new RemoteCallException(
        "IDN-0" + status,
        status,
        "remote",
        "POST /_internal/sessions/introspect",
        null,
        Map.of(),
        traits);
  }

  private static MockHttpServletRequest request(String... authorizations) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/patra-identity/auth/me");
    for (String value : authorizations) {
      request.addHeader(HttpHeaders.AUTHORIZATION, value);
    }
    return request;
  }
}

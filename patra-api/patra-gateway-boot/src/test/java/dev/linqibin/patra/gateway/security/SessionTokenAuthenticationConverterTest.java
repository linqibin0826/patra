package dev.linqibin.patra.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.identity.session.RedisSessionStore;
import dev.linqibin.patra.identity.session.SessionStoreUnavailableException;
import dev.linqibin.patra.identity.session.SessionToken;
import dev.linqibin.patra.identity.session.StoredSession;
import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import io.lettuce.core.RedisCommandExecutionException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;

/// 设计第 7.1 节那张表逐行：前六行当匿名，后两行分别是 503 与 500 对应的异常。
class SessionTokenAuthenticationConverterTest {

  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);

  private final RedisSessionStore sessions = mock(RedisSessionStore.class);
  private final SessionTokenAuthenticationConverter converter =
      new SessionTokenAuthenticationConverter(sessions);
  private final SessionToken token = SessionToken.generate(AccountType.USER, new SecureRandom());

  @Test
  void should_return_null_without_authorization_header() {
    assertThat(converter.convert(request())).isNull();
    verifyNoInteractions(sessions);
  }

  @Test
  void should_return_null_for_multiple_authorization_headers() {
    assertThat(converter.convert(request("Bearer " + token.value(), "Bearer " + token.value())))
        .isNull();
    verifyNoInteractions(sessions);
  }

  @Test
  void should_return_null_for_non_bearer_scheme() {
    assertThat(converter.convert(request("Basic dXNlcjpwYXNz"))).isNull();
    verifyNoInteractions(sessions);
  }

  @Test
  void should_return_null_when_bearer_value_is_not_a_session_token() {
    assertThat(converter.convert(request("Bearer not-a-session-token"))).isNull();
    assertThat(converter.convert(request("Bearer eyJhbGciOiJFUzI1NiJ9.e30.c2ln"))).isNull();
    assertThat(converter.convert(request("Bearer"))).isNull();
    verifyNoInteractions(sessions);
  }

  @Test
  void should_return_null_when_session_is_missing() {
    when(sessions.findAndTouch(token)).thenReturn(Optional.empty());

    assertThat(converter.convert(request("Bearer " + token.value()))).isNull();
  }

  @Test
  void should_authenticate_when_session_exists() {
    when(sessions.findAndTouch(token)).thenReturn(Optional.of(storedSession()));

    Authentication authentication = converter.convert(request("Bearer " + token.value()));

    assertThat(authentication).isInstanceOf(CurrentUserAuthentication.class);
    assertThat(authentication.getPrincipal()).isEqualTo(USER);
    assertThat(authentication.getCredentials()).isNull();
    assertThat(authentication.isAuthenticated()).isTrue();
  }

  @Test
  void should_accept_lowercase_scheme_and_surrounding_whitespace() {
    when(sessions.findAndTouch(token)).thenReturn(Optional.of(storedSession()));

    Authentication authentication = converter.convert(request(" bearer  " + token.value() + " "));

    assertThat(authentication.getPrincipal()).isEqualTo(USER);
  }

  @Test
  void should_translate_store_unavailable_into_authentication_service_exception() {
    when(sessions.findAndTouch(token))
        .thenThrow(
            new SessionStoreUnavailableException(new RedisConnectionFailureException("refused")));

    assertThatThrownBy(() -> converter.convert(request("Bearer " + token.value())))
        .isInstanceOf(AuthenticationServiceException.class)
        .isNotInstanceOf(SessionLookupFailedException.class)
        .hasCauseInstanceOf(SessionStoreUnavailableException.class)
        .hasMessageNotContaining(token.value());
  }

  @Test
  void should_wrap_other_store_failures_as_lookup_failed() {
    when(sessions.findAndTouch(token))
        .thenThrow(
            new RedisSystemException(
                "Error in execution",
                new RedisCommandExecutionException("NOAUTH Authentication required.")));

    assertThatThrownBy(() -> converter.convert(request("Bearer " + token.value())))
        .isInstanceOf(SessionLookupFailedException.class)
        .hasCauseInstanceOf(RedisSystemException.class)
        .hasMessageNotContaining(token.value());
  }

  private static MockHttpServletRequest request(String... authorizations) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/patra-identity/auth/me");
    for (String value : authorizations) {
      request.addHeader(HttpHeaders.AUTHORIZATION, value);
    }
    return request;
  }

  private static StoredSession storedSession() {
    Instant now = Instant.parse("2026-10-09T08:00:00Z");
    return StoredSession.builder()
        .userId(USER.userId())
        .sessionId(USER.sessionId())
        .accountType(AccountType.USER)
        .clientType(ClientType.WEB)
        .createdAt(now)
        .lastActiveAt(now)
        .expiresAt(now.plus(Duration.ofDays(180)))
        .build();
  }
}

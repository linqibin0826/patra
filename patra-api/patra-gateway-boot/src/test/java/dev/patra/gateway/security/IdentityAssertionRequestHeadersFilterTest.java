package dev.patra.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.security.assertion.IdentityAssertionClaims;
import dev.patra.tool.security.assertion.IdentityAssertionDecoders;
import dev.patra.tool.security.assertion.IdentityAssertionSigner;
import dev.patra.tool.security.authentication.CurrentUserAuthentication;
import dev.patra.tool.security.test.TestSigningKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.function.ServerRequest;

/// 出站的 `Authorization`：外部的一律剥掉；已登录时写入现签的断言，匿名什么都不写。
class IdentityAssertionRequestHeadersFilterTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneOffset.UTC);
  private static final ECKey KEY = TestSigningKey.key();
  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);

  private final IdentityAssertionRequestHeadersFilter filter =
      new IdentityAssertionRequestHeadersFilter(new IdentityAssertionSigner(KEY, CLOCK));
  private final ServerRequest request = mock(ServerRequest.class);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void should_strip_every_external_authorization_header_when_anonymous() {
    HttpHeaders input = new HttpHeaders();
    input.add(HttpHeaders.AUTHORIZATION, "Bearer patra_user_external");
    input.add(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");
    input.add("X-Request-Id", "r1");

    HttpHeaders output = filter.apply(HttpHeaders.readOnlyHttpHeaders(input), request);

    assertThat(output.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    assertThat(output.getFirst("X-Request-Id")).isEqualTo("r1");
    assertThat(input.get(HttpHeaders.AUTHORIZATION)).hasSize(2);
  }

  @Test
  void should_write_a_fresh_assertion_for_the_logged_in_user() {
    SecurityContextHolder.getContext().setAuthentication(new CurrentUserAuthentication(USER));
    HttpHeaders input = new HttpHeaders();
    input.setBearerAuth("patra_user_external");

    HttpHeaders output = filter.apply(input, request);

    String value = output.getFirst(HttpHeaders.AUTHORIZATION);
    assertThat(output.get(HttpHeaders.AUTHORIZATION)).hasSize(1);
    assertThat(value).startsWith("Bearer ").doesNotContain("patra_user_external");
    Jwt jwt =
        IdentityAssertionDecoders.forPublicKeys(new JWKSet(KEY.toPublicJWK()), CLOCK)
            .decode(value.substring("Bearer ".length()));
    assertThat(IdentityAssertionClaims.toCurrentUser(jwt)).isEqualTo(USER);
  }

  @Test
  void should_return_a_mutable_copy() {
    HttpHeaders output = filter.apply(HttpHeaders.readOnlyHttpHeaders(new HttpHeaders()), request);

    output.add("X-Later", "ok");

    assertThat(output.getFirst("X-Later")).isEqualTo("ok");
  }

  @Test
  void should_run_before_the_framework_forwarded_filters() {
    assertThat(filter.getOrder()).isLessThan(0);
  }
}

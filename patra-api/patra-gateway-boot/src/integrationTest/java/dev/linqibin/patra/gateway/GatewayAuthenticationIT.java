package dev.linqibin.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.session.NewSession;
import dev.linqibin.patra.identity.session.RedisSessionStore;
import dev.linqibin.patra.identity.session.SessionToken;
import dev.linqibin.starter.test.container.initializer.RedisContainerInitializer;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 会话认证与路径规则（设计第 6.2、6.3、7.1 节）、出站的断言与头（第 8 节）。
/// identity 和 catalog 都由 WireMock 顶替；会话直接写进 Redis 测试容器。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@ContextConfiguration(
    initializers = {RedisContainerInitializer.class, GatewayITSigningKeyInitializer.class})
class GatewayAuthenticationIT {

  private static final String BEARER = "Bearer ";

  @RegisterExtension
  static final WireMockExtension identity =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true).gzipDisabled(true))
          .build();

  @RegisterExtension
  static final WireMockExtension catalog =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true).gzipDisabled(true))
          .build();

  @Autowired private RestTestClient restClient;
  @Autowired private RedisSessionStore sessions;
  @Autowired private StringRedisTemplate redis;

  @Autowired
  @Qualifier("identityAssertionDecoder")
  private JwtDecoder decoder;

  @DynamicPropertySource
  static void downstreams(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-identity[0].uri", identity::baseUrl);
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-catalog[0].uri", catalog::baseUrl);
  }

  @BeforeEach
  void resetDownstreams() {
    identity.resetAll();
    catalog.resetAll();
  }

  @Test
  void should_let_anonymous_requests_reach_public_routes_without_authorization() {
    catalog.stubFor(get(urlPathEqualTo("/portal/venues")).willReturn(okJson("{}")));

    restClient.get().uri("/patra-catalog/portal/venues").exchange().expectStatus().isOk();

    catalog.verify(
        getRequestedFor(urlPathEqualTo("/portal/venues")).withoutHeader(HttpHeaders.AUTHORIZATION));
  }

  @Test
  void should_answer_401_problem_detail_when_anonymous_hits_identity_me() {
    restClient
        .get()
        .uri("/patra-identity/auth/me")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0401")
        .jsonPath("$.detail")
        .isEqualTo("Authentication required")
        .jsonPath("$.instance")
        .isEqualTo("/patra-identity/auth/me");

    identity.verify(0, getRequestedFor(urlPathEqualTo("/auth/me")));
  }

  @Test
  void should_treat_a_well_formed_unknown_token_as_anonymous() {
    String unknown = SessionToken.generate(AccountType.USER, new SecureRandom()).value();
    catalog.stubFor(get(urlPathEqualTo("/portal/venues")).willReturn(okJson("{}")));

    restClient
        .get()
        .uri("/patra-identity/auth/me")
        .header(HttpHeaders.AUTHORIZATION, BEARER + unknown)
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0401");
    restClient
        .get()
        .uri("/patra-catalog/portal/venues")
        .header(HttpHeaders.AUTHORIZATION, BEARER + unknown)
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void should_let_a_valid_session_reach_identity_without_cookies_or_redirects() {
    String token = GatewayITSessions.issue(sessions, 4201L, 9201L);
    identity.stubFor(get(urlPathEqualTo("/auth/me")).willReturn(okJson("{\"userId\":\"4201\"}")));

    restClient
        .get()
        .uri("/patra-identity/auth/me")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .doesNotExist(HttpHeaders.SET_COOKIE)
        .expectHeader()
        .doesNotExist(HttpHeaders.LOCATION)
        .expectBody()
        .json("{\"userId\":\"4201\"}");

    identity.verify(1, getRequestedFor(urlPathEqualTo("/auth/me")));
  }

  @Test
  void should_reject_the_token_once_the_session_is_deleted_but_still_let_logout_through() {
    String token = GatewayITSessions.issue(sessions, 4203L, 9203L);
    identity.stubFor(post(urlPathEqualTo("/auth/logout")).willReturn(aResponse().withStatus(204)));
    assertThat(sessions.delete(AccountType.USER, 4203L, 9203L)).isTrue();

    restClient
        .get()
        .uri("/patra-identity/auth/me")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isUnauthorized();
    restClient
        .post()
        .uri("/patra-identity/auth/logout")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isNoContent();

    identity.verify(1, postRequestedFor(urlPathEqualTo("/auth/logout")));
  }

  /// 会话模块满 60 秒才写回：建一条最后活跃时间在两分钟前的会话，一次请求后它被写回、TTL 重算，
  /// 且不超过绝对过期剩下的十分钟。
  @Test
  void should_renew_the_session_on_request_without_passing_absolute_expiry() {
    Instant twoMinutesAgo = Instant.now().minus(Duration.ofMinutes(2));
    Duration remaining = Duration.ofMinutes(10);
    NewSession stale =
        GatewayITSessions.session(4204L, 9204L)
            .createdAt(twoMinutesAgo)
            .expiresAt(Instant.now().plus(remaining))
            .build();
    SessionToken token = sessions.create(stale).token();
    String key = "idn:session:user:" + token.hash();
    assertThat(redis.opsForHash().get(key, "last_active_at"))
        .isEqualTo(Long.toString(twoMinutesAgo.toEpochMilli()));
    identity.stubFor(get(urlPathEqualTo("/auth/me")).willReturn(okJson("{}")));

    restClient
        .get()
        .uri("/patra-identity/auth/me")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token.value())
        .exchange()
        .expectStatus()
        .isOk();

    long lastActiveAt = Long.parseLong((String) redis.opsForHash().get(key, "last_active_at"));
    assertThat(lastActiveAt).isGreaterThan(twoMinutesAgo.toEpochMilli());
    assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS))
        .isPositive()
        .isLessThanOrEqualTo(remaining.toMillis());
  }

  @Test
  void should_require_login_for_unlisted_identity_paths_and_let_the_public_list_through() {
    identity.stubFor(any(urlPathMatching("/.*")).willReturn(okJson("{}")));
    String token = GatewayITSessions.issue(sessions, 4205L, 9205L);

    restClient.get().uri("/patra-identity/devices").exchange().expectStatus().isUnauthorized();
    restClient
        .get()
        .uri("/patra-identity/devices")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isOk();
    restClient.post().uri("/patra-identity/auth/register").exchange().expectStatus().isOk();
    restClient.post().uri("/patra-identity/auth/login").exchange().expectStatus().isOk();
    restClient.post().uri("/patra-identity/auth/logout").exchange().expectStatus().isOk();
    restClient.get().uri("/patra-identity/v3/api-docs").exchange().expectStatus().isOk();

    identity.verify(1, getRequestedFor(urlPathEqualTo("/devices")));
    identity.verify(1, postRequestedFor(urlPathEqualTo("/auth/register")));
    identity.verify(1, postRequestedFor(urlPathEqualTo("/auth/login")));
    identity.verify(1, postRequestedFor(urlPathEqualTo("/auth/logout")));
    identity.verify(1, getRequestedFor(urlPathEqualTo("/v3/api-docs")));
  }
}

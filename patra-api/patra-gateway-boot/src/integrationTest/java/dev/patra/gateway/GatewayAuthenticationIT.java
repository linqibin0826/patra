package dev.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.patra.identity.session.NewSession;
import dev.patra.identity.session.RedisSessionStore;
import dev.patra.identity.session.SessionToken;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.security.assertion.IdentityAssertionClaims;
import dev.patra.tool.security.test.TestIdentity;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.client.RestClient;

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
  @Autowired private Environment environment;
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

  @Test
  void should_hand_identity_a_signed_assertion_for_the_session_user() {
    String token = GatewayITSessions.issue(sessions, 4211L, 9211L);
    identity.stubFor(get(urlPathEqualTo("/auth/me")).willReturn(okJson("{}")));

    restClient
        .get()
        .uri("/patra-identity/auth/me")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isOk();

    List<LoggedRequest> received = identity.findAll(getRequestedFor(urlPathEqualTo("/auth/me")));
    assertThat(received).hasSize(1);
    String authorization = received.getFirst().getHeader(HttpHeaders.AUTHORIZATION);
    assertThat(authorization).startsWith(BEARER).doesNotContain(token);
    Jwt jwt = decoder.decode(authorization.substring(BEARER.length()));
    assertThat(IdentityAssertionClaims.toCurrentUser(jwt))
        .isEqualTo(CurrentUser.of(4211L, 9211L, AccountType.USER, ClientType.WEB));
  }

  /// 邮件链接谁点都能验证：匿名请求原样到达 identity（不带 `Authorization`）；带会话的请求照样换成断言，
  /// identity 据此保留当前会话。
  @Test
  void should_forward_email_verify_anonymously_and_with_assertion_when_logged_in() {
    identity.stubFor(post(urlPathEqualTo("/auth/email/verify")).willReturn(okJson("{}")));
    String token = GatewayITSessions.issue(sessions, 4221L, 9221L);

    restClient.post().uri("/patra-identity/auth/email/verify").exchange().expectStatus().isOk();
    restClient
        .post()
        .uri("/patra-identity/auth/email/verify")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isOk();

    List<LoggedRequest> received =
        identity.findAll(postRequestedFor(urlPathEqualTo("/auth/email/verify")));
    assertThat(received).hasSize(2);
    assertThat(received.getFirst().getHeader(HttpHeaders.AUTHORIZATION)).isNull();
    String authorization = received.getLast().getHeader(HttpHeaders.AUTHORIZATION);
    assertThat(authorization).startsWith(BEARER).doesNotContain(token);
    assertThat(
            IdentityAssertionClaims.toCurrentUser(
                decoder.decode(authorization.substring(BEARER.length()))))
        .isEqualTo(CurrentUser.of(4221L, 9221L, AccountType.USER, ClientType.WEB));
  }

  /// 找回密码的申请与提交、改邮箱的确认与撤销都是邮件链接或匿名操作：匿名转发且不带 Authorization。
  @Test
  void should_forward_password_reset_and_email_change_links_anonymously() {
    identity.stubFor(
        post(urlPathMatching("/auth/(password|email/change)/.*")).willReturn(okJson("{}")));

    for (String path :
        List.of(
            "/auth/password/forgot",
            "/auth/password/reset",
            "/auth/email/change/confirm",
            "/auth/email/change/revert")) {
      restClient.post().uri("/patra-identity" + path).exchange().expectStatus().isOk();
      identity.verify(
          1, postRequestedFor(urlPathEqualTo(path)).withoutHeader(HttpHeaders.AUTHORIZATION));
    }
  }

  /// 再认证、改密码、改邮箱申请是本人操作：匿名 401、不到下游；带会话才转发，且带断言。
  @Test
  void should_require_session_for_reauth_password_change_and_email_change_request() {
    identity.stubFor(any(urlPathMatching("/.*")).willReturn(okJson("{}")));

    restClient.post().uri("/patra-identity/auth/reauth").exchange().expectStatus().isUnauthorized();
    restClient.put().uri("/patra-identity/me/password").exchange().expectStatus().isUnauthorized();
    restClient
        .post()
        .uri("/patra-identity/me/email/change")
        .exchange()
        .expectStatus()
        .isUnauthorized();
    identity.verify(0, postRequestedFor(urlPathEqualTo("/auth/reauth")));
    identity.verify(0, postRequestedFor(urlPathEqualTo("/me/email/change")));

    String token = GatewayITSessions.issue(sessions, 4231L, 9231L);
    restClient
        .post()
        .uri("/patra-identity/auth/reauth")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isOk();
    identity.verify(
        1,
        postRequestedFor(urlPathEqualTo("/auth/reauth"))
            .withHeader(HttpHeaders.AUTHORIZATION, matching("Bearer .+")));
  }

  /// 注销与取消注销是本人操作（PAP-100）：`/me/**` 默认需登录，匿名 401、不到下游。
  @Test
  void should_require_session_for_account_deletion() {
    identity.stubFor(any(urlPathMatching("/.*")).willReturn(okJson("{}")));

    restClient.post().uri("/patra-identity/me/deletion").exchange().expectStatus().isUnauthorized();
    restClient
        .delete()
        .uri("/patra-identity/me/deletion")
        .exchange()
        .expectStatus()
        .isUnauthorized();
    identity.verify(0, anyRequestedFor(urlPathEqualTo("/me/deletion")));
  }

  /// 头像读取公开：匿名转发，下游的不可变缓存头、ETag、类型原样回到客户端（PAP-97）。
  @Test
  void should_forward_anonymous_avatar_read_with_cache_headers() {
    identity.stubFor(
        get(urlPathEqualTo("/users/chen-yu/avatar"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(HttpHeaders.CONTENT_TYPE, "image/webp")
                    .withHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                    .withHeader(HttpHeaders.ETAG, "\"0123456789abcdef-128\"")
                    .withBody(new byte[] {1, 2, 3})));

    restClient
        .get()
        .uri("/patra-identity/users/chen-yu/avatar?size=128&v=0123456789abcdef")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(HttpHeaders.CONTENT_TYPE, "image/webp")
        .expectHeader()
        .valueEquals(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
        .expectHeader()
        .valueEquals(HttpHeaders.ETAG, "\"0123456789abcdef-128\"");

    identity.verify(
        1,
        getRequestedFor(urlPathEqualTo("/users/chen-yu/avatar"))
            .withoutHeader(HttpHeaders.AUTHORIZATION));
  }

  /// 头像上传要登录；带会话时约 5 MB 的请求体原样流到下游，网关不截断（PAP-97）。
  @Test
  void should_require_login_and_stream_large_avatar_upload() {
    identity.stubFor(post(urlPathEqualTo("/me/avatar")).willReturn(okJson("{}")));
    String token = GatewayITSessions.issue(sessions, 4241L, 9241L);
    byte[] body = new byte[5_000_000];
    new Random(97).nextBytes(body);
    MediaType multipart = MediaType.parseMediaType("multipart/form-data; boundary=avatar-it");

    restClient
        .post()
        .uri("/patra-identity/me/avatar")
        .contentType(multipart)
        .body(body)
        .exchange()
        .expectStatus()
        .isUnauthorized();
    identity.verify(0, postRequestedFor(urlPathEqualTo("/me/avatar")));

    restClient
        .post()
        .uri("/patra-identity/me/avatar")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .contentType(multipart)
        .body(body)
        .exchange()
        .expectStatus()
        .isOk();
    List<LoggedRequest> received = identity.findAll(postRequestedFor(urlPathEqualTo("/me/avatar")));
    assertThat(received).hasSize(1);
    assertThat(received.getFirst().getBody()).isEqualTo(body);
  }

  /// 重发验证邮件是本人操作：匿名 401、不到下游；带会话才转发，且带断言。
  @Test
  void should_require_session_for_email_verification_resend() {
    identity.stubFor(
        post(urlPathEqualTo("/me/email/verification/resend")).willReturn(okJson("{}")));
    String token = GatewayITSessions.issue(sessions, 4222L, 9222L);

    restClient
        .post()
        .uri("/patra-identity/me/email/verification/resend")
        .exchange()
        .expectStatus()
        .isUnauthorized();
    identity.verify(0, postRequestedFor(urlPathEqualTo("/me/email/verification/resend")));

    restClient
        .post()
        .uri("/patra-identity/me/email/verification/resend")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isOk();
    identity.verify(
        1,
        postRequestedFor(urlPathEqualTo("/me/email/verification/resend"))
            .withHeader(HttpHeaders.AUTHORIZATION, matching("Bearer .+")));
  }

  /// `Basic`、随手写的 Bearer、甚至一条用网关公钥能验的断言（泄露后被重放），都到不了下游：
  /// 网关只认会话令牌，其余一律剥掉、当匿名。
  @Test
  void should_strip_external_authorization_that_is_not_a_session_token() {
    catalog.stubFor(get(urlPathEqualTo("/portal/publications")).willReturn(okJson("{}")));
    List<String> external =
        List.of(
            "Basic dXNlcjpwYXNz",
            BEARER + "not-a-session-token",
            BEARER + TestIdentity.assertion());

    for (String value : external) {
      restClient
          .get()
          .uri("/patra-catalog/portal/publications")
          .header(HttpHeaders.AUTHORIZATION, value)
          .exchange()
          .expectStatus()
          .isOk();
    }

    List<LoggedRequest> received =
        catalog.findAll(getRequestedFor(urlPathEqualTo("/portal/publications")));
    assertThat(received)
        .hasSize(3)
        .allSatisfy(request -> assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isNull());
  }

  @Test
  void should_treat_multiple_authorization_headers_as_anonymous_and_strip_them_all() {
    String token = GatewayITSessions.issue(sessions, 4212L, 9212L);
    identity.stubFor(post(urlPathEqualTo("/auth/logout")).willReturn(aResponse().withStatus(204)));

    restClient
        .post()
        .uri("/patra-identity/auth/logout")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isNoContent();

    identity.verify(
        postRequestedFor(urlPathEqualTo("/auth/logout")).withoutHeader(HttpHeaders.AUTHORIZATION));
  }

  /// 会话已失效时拿旧令牌登出：以匿名身份到达 identity，不带任何 `Authorization`，identity 回 204。
  @Test
  void should_let_logout_with_a_dead_token_reach_identity_anonymously() {
    String token = GatewayITSessions.issue(sessions, 4213L, 9213L);
    sessions.delete(AccountType.USER, 4213L, 9213L);
    identity.stubFor(post(urlPathEqualTo("/auth/logout")).willReturn(aResponse().withStatus(204)));

    restClient
        .post()
        .uri("/patra-identity/auth/logout")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isNoContent();

    identity.verify(
        postRequestedFor(urlPathEqualTo("/auth/logout")).withoutHeader(HttpHeaders.AUTHORIZATION));
  }

  @Test
  void should_replace_client_supplied_forwarded_headers_with_gateway_values() {
    catalog.stubFor(get(urlPathEqualTo("/portal/venues")).willReturn(okJson("{}")));

    restClient
        .get()
        .uri("/patra-catalog/portal/venues")
        .header("X-Forwarded-Host", "evil.example")
        .header("X-Forwarded-Prefix", "/evil")
        .header("X-Forwarded-For", "203.0.113.9")
        .header("X-Forwarded-Proto", "https")
        .header("Forwarded", "for=203.0.113.9;host=evil.example;proto=https")
        .exchange()
        .expectStatus()
        .isOk();

    LoggedRequest received =
        catalog.findAll(getRequestedFor(urlPathEqualTo("/portal/venues"))).getFirst();
    assertThat(received.getHeader("X-Forwarded-Host")).matches("localhost(:\\d+)?");
    assertThat(received.getHeader("X-Forwarded-Prefix")).isEqualTo("/patra-catalog");
    assertThat(received.getHeader("X-Forwarded-Proto")).isEqualTo("http");
    assertThat(received.getHeader("X-Forwarded-For")).doesNotContain("203.0.113.9");
    assertThat(received.getHeader("Forwarded"))
        .doesNotContain("evil")
        .doesNotContain("203.0.113.9");
  }

  /// 「仅登录用户可见」的主页靠这一点：公开路由上带令牌来的请求，下游照样拿到身份断言（PAP-95）。
  @Test
  void should_forward_public_user_page_with_assertion_when_logged_in() {
    String token = GatewayITSessions.issue(sessions, 4231L, 9231L);
    identity.stubFor(
        get(urlPathEqualTo("/users/chen-yu")).willReturn(okJson("{\"username\":\"chen-yu\"}")));

    restClient
        .get()
        .uri("/patra-identity/users/chen-yu")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isOk();

    List<LoggedRequest> received =
        identity.findAll(getRequestedFor(urlPathEqualTo("/users/chen-yu")));
    assertThat(received).hasSize(1);
    String authorization = received.getFirst().getHeader(HttpHeaders.AUTHORIZATION);
    assertThat(authorization).startsWith(BEARER).doesNotContain(token);
    assertThat(
            IdentityAssertionClaims.toCurrentUser(
                decoder.decode(authorization.substring(BEARER.length()))))
        .isEqualTo(CurrentUser.of(4231L, 9231L, AccountType.USER, ClientType.WEB));
  }

  @Test
  void should_forward_public_user_page_anonymously() {
    identity.stubFor(
        get(urlPathEqualTo("/users/chen-yu")).willReturn(okJson("{\"username\":\"chen-yu\"}")));

    restClient
        .get()
        .uri("/patra-identity/users/chen-yu")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.username")
        .isEqualTo("chen-yu");

    identity.verify(
        getRequestedFor(urlPathEqualTo("/users/chen-yu")).withoutHeader(HttpHeaders.AUTHORIZATION));
  }

  @Test
  void should_pass_through_301_from_identity_unchanged() {
    identity.stubFor(
        get(urlPathEqualTo("/users/old-name"))
            .willReturn(
                aResponse()
                    .withStatus(301)
                    .withHeader(HttpHeaders.LOCATION, "/users/new-name")
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .withBody("{\"username\":\"new-name\"}")));

    // RestTestClient 在真实端口上会跟着 301 走，这里用不跟随跳转的 JDK 客户端
    RestClient raw =
        RestClient.builder()
            .baseUrl("http://localhost:" + environment.getRequiredProperty("local.server.port"))
            .requestFactory(
                new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()))
            .build();
    String[] observed =
        raw.get()
            .uri("/patra-identity/users/old-name")
            .exchange(
                (request, response) ->
                    new String[] {
                      String.valueOf(response.getStatusCode().value()),
                      response.getHeaders().getFirst(HttpHeaders.LOCATION),
                      new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)
                    });

    assertThat(observed[0]).isEqualTo("301");
    assertThat(observed[1]).isEqualTo("/users/new-name");
    assertThat(observed[2]).contains("new-name");
  }

  @Test
  void should_require_session_for_me_paths_and_forward_them_with_an_assertion() {
    identity.stubFor(patch(urlPathEqualTo("/me/username")).willReturn(okJson("{}")));

    restClient
        .patch()
        .uri("/patra-identity/me/username")
        .exchange()
        .expectStatus()
        .isUnauthorized();
    identity.verify(0, patchRequestedFor(urlPathEqualTo("/me/username")));

    String token = GatewayITSessions.issue(sessions, 4221L, 9221L);
    restClient
        .patch()
        .uri("/patra-identity/me/username")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isOk();
    identity.verify(
        1,
        patchRequestedFor(urlPathEqualTo("/me/username"))
            .withHeader(HttpHeaders.AUTHORIZATION, matching("Bearer .+")));
  }
}

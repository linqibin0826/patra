package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.api.account.event.EmailVerificationMailEvent;
import dev.patra.identity.config.IdentityClockITSpringConfig;
import dev.patra.identity.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.identity.config.IdentitySecurityMailITFixture;
import dev.patra.identity.config.IdentitySecurityMailITSpringConfig;
import dev.patra.identity.infra.session.store.SessionToken;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.security.assertion.IdentityAssertionSigner;
import dev.patra.tool.security.test.TestSigningKey;
import dev.patra.tool.test.clock.MutableClock;
import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// 邮箱验证的完整流程：真实的 PostgreSQL、Redis、RabbitMQ（捕获队列收验证邮件事件）和安全过滤器链，时钟可拨动。
///
/// 全部请求走裸的 JDK HttpClient：`RestTestClient` 在真实端口上的 Apache 客户端会按 `Retry-After` 自动重试 429，
/// 60 秒的冷却等于挂死。断言用测试时钟签：identity 验签 `exp` 用的也是这个时钟。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(
    initializers = {
      IdentityITPostgreSQLContainerInitializer.class,
      RedisContainerInitializer.class,
      RabbitMQContainerInitializer.class
    })
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@Import({IdentityClockITSpringConfig.class, IdentitySecurityMailITSpringConfig.class})
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("邮箱验证的完整流程")
class EmailVerificationFlowIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String PASSWORD = "Correct-Horse-02";
  private static final Instant T0 = IdentityClockITSpringConfig.START;
  private static final Pattern TOKEN_IN_LINK = Pattern.compile("#token=([A-Za-z0-9_-]{43})");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StringRedisTemplate redis;
  @Autowired private MutableClock clock;
  @Autowired private Environment environment;

  @Autowired private IdentitySecurityMailITFixture securityMails;

  @BeforeEach
  void reset() {
    clock.set(T0);
    securityMails.purge();
  }

  @Test
  @DisplayName("注册登记一个验证邮件事件：发往注册邮箱，链接只来自配置、不来自转发头；响应和日志没有令牌；库里只有哈希")
  void should_send_verification_mail_on_register_with_links_from_configuration_not_host_header(
      CapturedOutput output) {
    RawResponse registered =
        post(
            "/auth/register",
            Map.of(
                "email", "flow.mail@example.com", "username", "u-flow-mail", "password", PASSWORD),
            headers(
                Map.of(
                    "X-Forwarded-Host", "evil.example",
                    "X-Forwarded-Proto", "https",
                    "Forwarded", "host=evil.example;proto=https")));
    assertThat(registered.status()).as(registered.body()).isEqualTo(201);
    long userId = userIdOf(registered);

    EmailVerificationMailEvent mail =
        securityMails.await(EmailVerificationMailEvent.class, "flow.mail@example.com");
    String token = tokenIn(mail.verifyUrl());
    String expectedPrefix = "http://linqibins-mac-mini:4000/verify-email#token=";
    assertThat(mail.verifyUrl()).isEqualTo(expectedPrefix + token).doesNotContain("evil.example");
    assertThat(mail.linkExpiresAt()).isEqualTo(T0.plus(Duration.ofHours(24)));
    assertThat(registered.body()).doesNotContain(token);
    assertThat(output.getAll()).doesNotContain(token);
    assertThat(
            jdbcTemplate.queryForObject(
                "select token_hash from idn_email_token where user_id = ? and purpose = 'EMAIL_VERIFY'",
                String.class,
                userId))
        .isEqualTo(sha256Hex(token));
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from idn_email_token where token_hash = ?", Integer.class, token))
        .isZero();
  }

  @Test
  @DisplayName("本人带会话验证：写验证时间，保留当前会话、撤销另一条并记 EMAIL_VERIFIED；同一令牌第二次 422")
  void should_verify_keep_current_session_and_revoke_the_other_then_reject_reuse() {
    RawResponse registered = register("flow.verify@example.com", "u-flow-verify");
    long userId = userIdOf(registered);
    String tokenA = sessionTokenOf(registered);
    long sessionA = latestSessionIdOf(userId);
    RawResponse login = login("flow.verify@example.com");
    String tokenB = sessionTokenOf(login);
    long sessionB = latestSessionIdOf(userId);
    String token = verificationToken("flow.verify@example.com", 1);

    RawResponse verified =
        post("/auth/email/verify", Map.of("token", token), assertionFor(userId, sessionA));

    assertThat(verified.status()).as(verified.body()).isEqualTo(200);
    JsonNode body = JSON.readTree(verified.body());
    assertThat(body.get("email").asString()).isEqualTo("flow.verify@example.com");
    assertThat(body.get("emailVerifiedAt").asString()).isEqualTo(T0.toString());
    JsonNode me = JSON.readTree(get("/auth/me", assertionFor(userId, sessionA)).body());
    assertThat(me.get("emailVerified").asBoolean()).isTrue();
    assertThat(redis.hasKey(sessionKey(tokenA))).isTrue();
    assertThat(redis.hasKey(sessionKey(tokenB))).isFalse();
    assertThat(endReasonOf(sessionB)).isEqualTo("EMAIL_VERIFIED");
    assertThat(endReasonOf(sessionA)).isNull();

    RawResponse again = post("/auth/email/verify", Map.of("token", token), new HttpHeaders());
    assertThat(again.status()).isEqualTo(422);
    assertThat(JSON.readTree(again.body()).get("errors").get(0).get("code").asString())
        .isEqualTo("INVALID");
  }

  @Test
  @DisplayName("重发限流：注册首封计入冷却；60 秒后 202 且旧令牌作废；一小时内第 6 次 429；一天内第 11 次 429；24 小时后放行")
  void should_rate_limit_resend_by_cooldown_hour_and_day() {
    RawResponse registered = register("flow.resend@example.com", "u-flow-resend");
    long userId = userIdOf(registered);
    long sessionId = latestSessionIdOf(userId);
    HttpHeaders assertion = assertionFor(userId, sessionId);
    String first = verificationToken("flow.resend@example.com", 1);

    RawResponse tooSoon = resend(assertion);
    assertThat(tooSoon.status()).isEqualTo(429);
    assertThat(tooSoon.header(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
    assertThat(JSON.readTree(tooSoon.body()).get("retryAfterSeconds").asInt()).isEqualTo(60);

    clock.set(T0.plusSeconds(61));
    RawResponse second = resend(assertionFor(userId, sessionId));
    assertThat(second.status()).as(second.body()).isEqualTo(202);
    assertThat(JSON.readTree(second.body()).get("nextResendAt").asString())
        .isEqualTo(T0.plusSeconds(121).toString());
    String replacement = verificationToken("flow.resend@example.com", 2);
    assertThat(replacement).isNotEqualTo(first);
    assertThat(post("/auth/email/verify", Map.of("token", first), new HttpHeaders()).status())
        .isEqualTo(422);

    for (long seconds : new long[] {122, 183, 244}) {
      clock.set(T0.plusSeconds(seconds));
      assertThat(resend(assertionFor(userId, sessionId)).status())
          .as("+%ds", seconds)
          .isEqualTo(202);
    }
    clock.set(T0.plusSeconds(305));
    RawResponse sixth = resend(assertionFor(userId, sessionId));
    assertThat(sixth.status()).isEqualTo(429);
    assertThat(sixth.header(HttpHeaders.RETRY_AFTER)).isEqualTo("3295");

    for (long seconds : new long[] {3601, 3662, 3723, 3784, 3845}) {
      clock.set(T0.plusSeconds(seconds));
      assertThat(resend(assertionFor(userId, sessionId)).status())
          .as("+%ds", seconds)
          .isEqualTo(202);
    }
    clock.set(T0.plusSeconds(3906));
    RawResponse eleventh = resend(assertionFor(userId, sessionId));
    assertThat(eleventh.status()).isEqualTo(429);
    assertThat(eleventh.header(HttpHeaders.RETRY_AFTER)).isEqualTo("82494");

    clock.set(T0.plus(Duration.ofHours(24)).plusSeconds(1));
    assertThat(resend(assertionFor(userId, sessionId)).status()).isEqualTo(202);
    assertThat(post("/auth/email/verify", Map.of("token", replacement), new HttpHeaders()).status())
        .as("中间那封的令牌早被后面的重发作废")
        .isEqualTo(422);
  }

  @Test
  @DisplayName("匿名验证：该用户的全部会话被撤销、记录全标 EMAIL_VERIFIED；别人的会话不受影响")
  void should_revoke_all_sessions_when_verified_anonymously_and_leave_requester_alone() {
    RawResponse alice = register("flow.alice@example.com", "u-flow-alice");
    long aliceId = userIdOf(alice);
    List<String> aliceTokens =
        List.of(
            sessionTokenOf(alice),
            sessionTokenOf(login("flow.alice@example.com")),
            sessionTokenOf(login("flow.alice@example.com")));
    List<Long> aliceSessions =
        jdbcTemplate.queryForList(
            "select id from idn_user_login_record where user_id = ? order by id",
            Long.class,
            aliceId);
    String aliceToken = verificationToken("flow.alice@example.com", 1);
    RawResponse bob = register("flow.bob@example.com", "u-flow-bob");
    String bobSession = sessionTokenOf(bob);

    RawResponse verified =
        post("/auth/email/verify", Map.of("token", aliceToken), new HttpHeaders());

    assertThat(verified.status()).as(verified.body()).isEqualTo(200);
    assertThat(aliceTokens)
        .allSatisfy(token -> assertThat(redis.hasKey(sessionKey(token))).isFalse());
    assertThat(aliceSessions)
        .hasSize(3)
        .allSatisfy(id -> assertThat(endReasonOf(id)).isEqualTo("EMAIL_VERIFIED"));
    assertThat(redis.hasKey(sessionKey(bobSession))).isTrue();
  }

  @Test
  @DisplayName("已验证的用户重发 409 EMAIL_ALREADY_VERIFIED；注销中的用户重发 409 PENDING_DELETION")
  void should_reject_resend_when_verified_or_pending_deletion() {
    RawResponse verifiedUser = register("flow.done@example.com", "u-flow-done");
    long doneId = userIdOf(verifiedUser);
    long doneSession = latestSessionIdOf(doneId);
    String token = verificationToken("flow.done@example.com", 1);
    assertThat(
            post("/auth/email/verify", Map.of("token", token), assertionFor(doneId, doneSession))
                .status())
        .isEqualTo(200);
    clock.set(T0.plusSeconds(120));

    RawResponse alreadyVerified = resend(assertionFor(doneId, doneSession));
    assertThat(alreadyVerified.status()).isEqualTo(409);
    assertThat(JSON.readTree(alreadyVerified.body()).get("traits").toString())
        .contains("EMAIL_ALREADY_VERIFIED");

    RawResponse pending = register("flow.pending@example.com", "u-flow-pending");
    long pendingId = userIdOf(pending);
    long pendingSession = latestSessionIdOf(pendingId);
    jdbcTemplate.update(
        "update idn_user set status = 'PENDING_DELETION', deletion_due_at = now() + interval '15 days'"
            + " where id = ?",
        pendingId);
    clock.set(T0.plusSeconds(240));

    RawResponse pendingResend = resend(assertionFor(pendingId, pendingSession));
    assertThat(pendingResend.status()).isEqualTo(409);
    assertThat(JSON.readTree(pendingResend.body()).get("traits").toString())
        .contains("PENDING_DELETION");
  }

  /// 注册，密码固定。
  ///
  /// @param email 邮箱
  /// @param username 用户名
  /// @return 响应
  private RawResponse register(String email, String username) {
    RawResponse response =
        post(
            "/auth/register",
            Map.of("email", email, "username", username, "password", PASSWORD),
            new HttpHeaders());
    assertThat(response.status()).as(response.body()).isEqualTo(201);
    return response;
  }

  /// 登录，密码固定。
  ///
  /// @param email 邮箱
  /// @return 响应
  private RawResponse login(String email) {
    RawResponse response =
        post("/auth/login", Map.of("email", email, "password", PASSWORD), new HttpHeaders());
    assertThat(response.status()).as(response.body()).isEqualTo(200);
    return response;
  }

  /// 重发验证邮件。
  ///
  /// @param assertion 断言头
  /// @return 响应
  private RawResponse resend(HttpHeaders assertion) {
    return rawClient()
        .method(HttpMethod.POST)
        .uri("/me/email/verification/resend")
        .headers(h -> h.addAll(assertion))
        .exchange(EmailVerificationFlowIT::toRaw);
  }

  /// 发一个 JSON POST。
  ///
  /// @param uri 路径
  /// @param body 请求体
  /// @param headers 额外的请求头
  /// @return 响应
  private RawResponse post(String uri, Map<String, Object> body, HttpHeaders headers) {
    return rawClient()
        .method(HttpMethod.POST)
        .uri(uri)
        .headers(h -> h.addAll(headers))
        .contentType(MediaType.APPLICATION_JSON)
        .body(JSON.writeValueAsString(body))
        .exchange(EmailVerificationFlowIT::toRaw);
  }

  /// 发一个 GET。
  ///
  /// @param uri 路径
  /// @param headers 请求头
  /// @return 响应
  private RawResponse get(String uri, HttpHeaders headers) {
    return rawClient()
        .get()
        .uri(uri)
        .headers(h -> h.addAll(headers))
        .exchange(EmailVerificationFlowIT::toRaw);
  }

  /// 不重试、不跟随跳转的客户端。
  ///
  /// @return 客户端
  private RestClient rawClient() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + environment.getRequiredProperty("local.server.port"))
        .requestFactory(
            new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()))
        .build();
  }

  /// 把响应读成状态、头和响应体。
  ///
  /// @param request 请求
  /// @param response 响应
  /// @return 原始响应
  private static RawResponse toRaw(HttpRequest request, ConvertibleClientHttpResponse response) {
    try {
      return new RawResponse(
          response.getStatusCode().value(),
          HttpHeaders.readOnlyHttpHeaders(response.getHeaders()),
          new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /// 用测试私钥、按可拨动的时钟签一条断言。
  ///
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return `Authorization: Bearer` 头
  private HttpHeaders assertionFor(long userId, long sessionId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(
        new IdentityAssertionSigner(TestSigningKey.key(), clock)
            .sign(CurrentUser.of(userId, sessionId, AccountType.USER, ClientType.WEB)));
    return headers;
  }

  /// 把键值对变成请求头。
  ///
  /// @param values 键值对
  /// @return 请求头
  private static HttpHeaders headers(Map<String, String> values) {
    HttpHeaders headers = new HttpHeaders();
    values.forEach(headers::add);
    return headers;
  }

  /// 本用例里第 n 个验证邮件事件链接里的令牌。登录还会登记新设备提醒（PAP-99），所以只数验证邮件事件。
  ///
  /// @param recipient 收件地址
  /// @param count 第几个验证邮件事件
  /// @return 令牌
  private String verificationToken(String recipient, int count) {
    List<EmailVerificationMailEvent> events =
        securityMails.all(EmailVerificationMailEvent.class, recipient);
    assertThat(events).as("验证邮件事件").hasSizeGreaterThanOrEqualTo(count);
    return tokenIn(events.get(count - 1).verifyUrl());
  }

  /// 从链接里取令牌。
  ///
  /// @param link 链接
  /// @return 令牌
  private static String tokenIn(String link) {
    Matcher matcher = TOKEN_IN_LINK.matcher(link);
    assertThat(matcher.find()).as("链接里有令牌").isTrue();
    return matcher.group(1);
  }

  /// SHA-256 的十六进制。
  ///
  /// @param value 输入
  /// @return 64 位十六进制
  private static String sha256Hex(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /// 响应里的会话令牌。
  ///
  /// @param response 注册或登录的响应
  /// @return 令牌
  private static String sessionTokenOf(RawResponse response) {
    return JSON.readTree(response.body()).get("sessionToken").asString();
  }

  /// 响应里的用户 ID。
  ///
  /// @param response 注册或登录的响应
  /// @return 用户 ID
  private static long userIdOf(RawResponse response) {
    return Long.parseLong(JSON.readTree(response.body()).get("user").get("userId").asString());
  }

  /// 会话令牌对应的 Redis 键。
  ///
  /// @param token 令牌原文
  /// @return 键
  private static String sessionKey(String token) {
    return "idn:session:"
        + AccountType.USER.getCode()
        + ":"
        + SessionToken.parse(token).orElseThrow().hash();
  }

  /// 用户最新一条登录记录的 ID。
  ///
  /// @param userId 用户 ID
  /// @return 记录 ID
  private long latestSessionIdOf(long userId) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM idn_user_login_record WHERE user_id = ? ORDER BY id DESC LIMIT 1",
        Long.class,
        userId);
  }

  /// 登录记录的结束原因。
  ///
  /// @param sessionId 记录 ID
  /// @return 结束原因；未结束时为 `null`
  private String endReasonOf(long sessionId) {
    return jdbcTemplate.queryForObject(
        "SELECT end_reason FROM idn_user_login_record WHERE id = ?", String.class, sessionId);
  }

  /// 原始响应。
  ///
  /// @param status 状态码
  /// @param headers 响应头
  /// @param body 响应体
  private record RawResponse(int status, HttpHeaders headers, String body) {
    /// 某个响应头的第一个值。
    ///
    /// @param name 头名
    /// @return 值；没有时为 `null`
    String header(String name) {
      return headers.getFirst(name);
    }
  }
}

package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.api.account.event.SecurityMailEvent;
import dev.patra.identity.config.IdentityClockITSpringConfig;
import dev.patra.identity.config.IdentityITMinIOContainerInitializer;
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
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
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

/// 账号安全流程测试的共用基座：真实的 PostgreSQL、Redis、RabbitMQ（捕获队列代替通知服务收安全邮件事件）和安全过滤器链，
/// 时钟可拨动。
///
/// 全部请求走裸的 JDK HttpClient：`RestTestClient` 在真实端口上的 Apache 客户端会按 `Retry-After` 自动重试 429。
/// 断言用测试时钟签：identity 验签 `exp` 用的也是这个时钟。注解组合与 `EmailVerificationFlowIT` 相同，
/// Spring 复用同一个上下文。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(
    initializers = {
      IdentityITPostgreSQLContainerInitializer.class,
      RedisContainerInitializer.class,
      RabbitMQContainerInitializer.class,
      IdentityITMinIOContainerInitializer.class
    })
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@Import({IdentityClockITSpringConfig.class, IdentitySecurityMailITSpringConfig.class})
abstract class IdentityFlowITSupport {

  static final JsonMapper JSON = JsonMapper.builder().build();
  static final String PASSWORD = "Correct-Horse-02";
  static final Instant T0 = IdentityClockITSpringConfig.START;
  static final Pattern TOKEN_IN_LINK = Pattern.compile("#token=([A-Za-z0-9_-]{43})");

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired StringRedisTemplate redis;
  @Autowired MutableClock clock;
  @Autowired Environment environment;

  @Autowired IdentitySecurityMailITFixture securityMails;

  /// 每个用例从 T0 和空的捕获队列开始；清掉找回密码按 IP 的两个窗口键——所有用例同一个 IP，
  /// 而窗口按测试时钟判、上一个用例拨到未来的记录会让这个用例一开始就 429。
  @BeforeEach
  void resetClockAndMailbox() {
    clock.set(T0);
    securityMails.purge();
    String ipHash = sha256Hex("127.0.0.1").substring(0, 32);
    redis.delete(
        List.of("idn:pw-reset:hour:ip:" + ipHash, "idn:pw-reset-submit:minute:ip:" + ipHash));
  }

  /// 注册，密码固定；断言 201。
  ///
  /// @param email 邮箱
  /// @param username 用户名
  /// @return 响应
  RawResponse register(String email, String username) {
    RawResponse response =
        post(
            "/auth/register",
            Map.of("email", email, "username", username, "password", PASSWORD),
            new HttpHeaders());
    assertThat(response.status()).as(response.body()).isEqualTo(201);
    return response;
  }

  /// 登录，不断言状态。
  ///
  /// @param email 邮箱
  /// @param password 密码
  /// @return 响应
  RawResponse login(String email, String password) {
    return post("/auth/login", Map.of("email", email, "password", password), new HttpHeaders());
  }

  /// 发一个 JSON 请求。
  ///
  /// @param method 方法
  /// @param uri 路径
  /// @param body 请求体，可以为 `null`
  /// @param headers 额外的请求头
  /// @return 响应
  RawResponse exchange(
      HttpMethod method, String uri, Map<String, Object> body, HttpHeaders headers) {
    RestClient.RequestBodySpec spec =
        rawClient().method(method).uri(uri).headers(h -> h.addAll(headers));
    if (body != null) {
      spec.contentType(MediaType.APPLICATION_JSON).body(JSON.writeValueAsString(body));
    }
    return spec.exchange(IdentityFlowITSupport::toRaw);
  }

  /// 发一个 JSON POST。
  ///
  /// @param uri 路径
  /// @param body 请求体
  /// @param headers 额外的请求头
  /// @return 响应
  RawResponse post(String uri, Map<String, Object> body, HttpHeaders headers) {
    return exchange(HttpMethod.POST, uri, body, headers);
  }

  /// 发一个 GET。
  ///
  /// @param uri 路径
  /// @param headers 请求头
  /// @return 响应
  RawResponse get(String uri, HttpHeaders headers) {
    return exchange(HttpMethod.GET, uri, null, headers);
  }

  /// 不重试、不跟随跳转的客户端。
  ///
  /// @return 客户端
  RestClient rawClient() {
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
  static RawResponse toRaw(HttpRequest request, ConvertibleClientHttpResponse response) {
    try {
      return new RawResponse(
          response.getStatusCode().value(),
          HttpHeaders.readOnlyHttpHeaders(response.getHeaders()),
          new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /// 没有额外请求头（匿名请求）。
  ///
  /// @return 空头
  static HttpHeaders noHeaders() {
    return new HttpHeaders();
  }

  /// 像浏览器经门户与网关来的请求：深圳的公网 IP 与 Chrome 141 / macOS 的 UA（PAP-99），可叠加在已有的头上。
  ///
  /// @param base 已有的请求头，比如断言
  /// @return 新的请求头
  static HttpHeaders fromShenzhenChrome(HttpHeaders base) {
    HttpHeaders headers = new HttpHeaders();
    headers.addAll(base);
    headers.add("X-Forwarded-For", "113.92.157.29");
    headers.add(
        HttpHeaders.USER_AGENT,
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)"
            + " Chrome/141.0.0.0 Safari/537.36");
    return headers;
  }

  /// 用测试私钥、按可拨动的时钟签一条断言。
  ///
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return `Authorization: Bearer` 头
  HttpHeaders assertionFor(long userId, long sessionId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(
        new IdentityAssertionSigner(TestSigningKey.key(), clock)
            .sign(CurrentUser.of(userId, sessionId, AccountType.USER, ClientType.WEB)));
    return headers;
  }

  /// 发给某个地址的某种安全邮件事件，最多等 5 秒：外送在事务提交后异步进行。
  ///
  /// @param type 事件类型
  /// @param recipient 收件地址
  /// @param <T> 事件类型
  /// @return 第一个匹配的事件
  <T extends SecurityMailEvent> T mailEvent(Class<T> type, String recipient) {
    return securityMails.await(type, recipient);
  }

  /// 从链接里取令牌。
  ///
  /// @param link 带 `#token=` 的链接
  /// @return 令牌
  static String tokenIn(String link) {
    Matcher matcher = TOKEN_IN_LINK.matcher(link);
    assertThat(matcher.find()).as("链接里有令牌").isTrue();
    return matcher.group(1);
  }

  /// SHA-256 的十六进制。
  ///
  /// @param value 输入
  /// @return 64 位十六进制
  static String sha256Hex(String value) {
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
  /// @param response 注册、登录、再认证或改密码的响应
  /// @return 令牌
  static String sessionTokenOf(RawResponse response) {
    return JSON.readTree(response.body()).get("sessionToken").asString();
  }

  /// 响应里的用户 ID。
  ///
  /// @param response 注册或登录的响应
  /// @return 用户 ID
  static long userIdOf(RawResponse response) {
    return Long.parseLong(JSON.readTree(response.body()).get("user").get("userId").asString());
  }

  /// 响应体解析成 JSON。
  ///
  /// @param response 响应
  /// @return JSON
  static JsonNode json(RawResponse response) {
    return JSON.readTree(response.body());
  }

  /// 某个令牌对应的会话键。
  ///
  /// @param token 令牌原文
  /// @return Redis 键
  static String sessionKey(String token) {
    return "idn:session:"
        + AccountType.USER.getCode()
        + ":"
        + SessionToken.parse(token).orElseThrow().hash();
  }

  /// 登录失败限制的锁键与计数键。
  ///
  /// @param email 规范化之后的邮箱
  /// @return 失败计数键、锁键
  static List<String> loginThrottleKeys(String email) {
    String suffix = AccountType.USER.getCode() + ":" + sha256Hex(email);
    return List.of("idn:login-failures:" + suffix, "idn:login-lock:" + suffix);
  }

  /// 某用户最新一条登录记录的 ID，也就是会话 ID。
  ///
  /// @param userId 用户 ID
  /// @return 会话 ID
  long latestSessionIdOf(long userId) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM idn_user_login_record WHERE user_id = ? ORDER BY id DESC LIMIT 1",
        Long.class,
        userId);
  }

  /// 某条登录记录的结束原因。
  ///
  /// @param sessionId 会话 ID
  /// @return 结束原因；未结束时为 `null`
  String endReasonOf(long sessionId) {
    return jdbcTemplate.queryForObject(
        "SELECT end_reason FROM idn_user_login_record WHERE id = ?", String.class, sessionId);
  }

  /// 某用户有效（未消费、未作废）令牌的用途列表。
  ///
  /// @param userId 用户 ID
  /// @return 用途
  List<String> activeTokenPurposesOf(long userId) {
    return jdbcTemplate.queryForList(
        "SELECT purpose FROM idn_email_token WHERE user_id = ?"
            + " AND consumed_at IS NULL AND revoked_at IS NULL ORDER BY id",
        String.class,
        userId);
  }

  /// 主人改主页可见性（专业背景公开、不收录），断言 200。
  ///
  /// @param owner 主人的断言头
  /// @param visibility 主页可见性
  void setPrivacy(HttpHeaders owner, String visibility) {
    RawResponse saved =
        exchange(
            HttpMethod.PUT,
            "/me/privacy",
            Map.of(
                "profileVisibility",
                visibility,
                "professionalVisibility",
                "PUBLIC",
                "searchIndexing",
                false),
            owner);
    assertThat(saved.status()).as(saved.body()).isEqualTo(200);
  }

  /// 用 SQL 标成邮箱已验证。
  ///
  /// @param userId 用户 ID
  void markEmailVerified(long userId) {
    jdbcTemplate.update("UPDATE idn_user SET email_verified_at = now() WHERE id = ?", userId);
  }

  /// 用 SQL 把账号改到给定状态（`ACTIVE`、`ACTIVE_UNVERIFIED` 不动）。冷静期里 `/me/**` 的写接口被拒，
  /// 要改隐私先改再调这里。
  ///
  /// @param userId 用户 ID
  /// @param status 状态
  void applyStatus(long userId, String status) {
    switch (status) {
      case "PENDING_DELETION" ->
          jdbcTemplate.update(
              "UPDATE idn_user SET status = 'PENDING_DELETION',"
                  + " deletion_due_at = now() + interval '15 days' WHERE id = ?",
              userId);
      case "BANNED" ->
          jdbcTemplate.update(
              "UPDATE idn_user SET status = 'BANNED', banned_at = now() WHERE id = ?", userId);
      case "DELETED" -> markDeleted(userId);
      default -> {}
    }
  }

  /// 用 SQL 标成已注销：只改用户行，不动资料（隐私设置保留）。
  ///
  /// @param userId 用户 ID
  void markDeleted(long userId) {
    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'DELETED', deleted_at = now(), email = NULL,"
            + " email_verified_at = NULL WHERE id = ?",
        userId);
  }

  /// 原始响应：状态、头、体。
  ///
  /// @param status 状态码
  /// @param headers 响应头
  /// @param body 响应体
  record RawResponse(int status, HttpHeaders headers, String body) {
    /// 某个响应头的第一个值。
    ///
    /// @param name 头名
    /// @return 值；没有时为 `null`
    String header(String name) {
      return headers.getFirst(name);
    }
  }
}

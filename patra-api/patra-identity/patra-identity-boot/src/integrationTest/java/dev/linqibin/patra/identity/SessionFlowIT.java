package dev.linqibin.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.identity.config.IdentityITPostgreSQLContainerInitializer;
import dev.linqibin.patra.identity.session.SessionToken;
import dev.linqibin.patra.starter.security.test.TestIdentity;
import dev.linqibin.starter.test.container.initializer.RedisContainerInitializer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// 会话的完整流程：真实的 PostgreSQL、Redis、Argon2 和安全过滤器链；身份断言用测试私钥签出。
///
/// 用真实端口：MOCK 环境下的 `RestTestClient` 走 MockMvc，不经过安全过滤器链，断言没人验。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(
    initializers = {
      IdentityITPostgreSQLContainerInitializer.class,
      RedisContainerInitializer.class
    })
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("前台用户会话的完整流程")
class SessionFlowIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String PASSWORD = "Session-Pass-01";

  @Autowired private RestTestClient restClient;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StringRedisTemplate redis;

  @Test
  @DisplayName("注册即登录：响应带令牌，Redis 里只有哈希，登录记录和会话共用一个 ID")
  void should_register_with_session_token_stored_as_hash() {
    EntityExchangeResult<String> registered = register("flow.register@example.com");

    assertThat(registered.getStatus().value()).isEqualTo(201);
    String token = tokenOf(registered);
    long userId = userIdOf(registered);
    assertThat(token).matches("^patra_user_[A-Za-z0-9_-]{43}$");
    String hash = SessionToken.parse(token).orElseThrow().hash();
    Map<Object, Object> session = redis.opsForHash().entries("idn:session:user:" + hash);
    assertThat(session)
        .containsEntry("user_id", Long.toString(userId))
        .containsEntry("account_type", "user")
        .containsEntry("client_type", "web")
        .doesNotContainKey("device_id");
    assertThat(redis.keys("*")).allSatisfy(key -> assertThat(key).doesNotContain(token));
    long sessionId = Long.parseLong((String) session.get("session_id"));
    assertThat(redis.opsForHash().get("idn:user-sessions:user:" + userId, Long.toString(sessionId)))
        .isEqualTo(hash);
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT user_id, client_type, device_id, ended_at FROM idn_user_login_record WHERE id = ?",
            sessionId);
    assertThat(row)
        .containsEntry("user_id", userId)
        .containsEntry("client_type", "WEB")
        .containsEntry("device_id", null)
        .containsEntry("ended_at", null);
  }

  @Test
  @DisplayName("登录带客户端类型和设备标识；当前用户接口带断言 200、不带 401")
  void should_login_with_client_fields_and_read_current_user() {
    long userId = userIdOf(register("flow.login@example.com"));

    EntityExchangeResult<String> login =
        post(
            "/auth/login",
            Map.of(
                "email", "flow.login@example.com",
                "password", PASSWORD,
                "clientType", "web",
                "deviceId", " mac-safari "));

    assertThat(login.getStatus().value()).isEqualTo(200);
    String hash = SessionToken.parse(tokenOf(login)).orElseThrow().hash();
    assertThat(redis.opsForHash().get("idn:session:user:" + hash, "device_id"))
        .isEqualTo("mac-safari");
    long sessionId = latestSessionIdOf(userId);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT device_id FROM idn_user_login_record WHERE id = ?",
                String.class,
                sessionId))
        .isEqualTo("mac-safari");

    EntityExchangeResult<String> me = me(assertionFor(userId, sessionId));
    assertThat(me.getStatus().value()).isEqualTo(200);
    JsonNode body = JSON.readTree(me.getResponseBody());
    assertThat(body.get("userId").asString()).isEqualTo(Long.toString(userId));
    assertThat(body.get("email").asString()).isEqualTo("flow.login@example.com");
    assertThat(body.get("accountType").asString()).isEqualTo("user");

    EntityExchangeResult<String> anonymous = me(new HttpHeaders());
    assertThat(anonymous.getStatus().value()).isEqualTo(401);
    assertThat(JSON.readTree(anonymous.getResponseBody()).get("code").asString())
        .isEqualTo("IDN-0401");
  }

  @Test
  @DisplayName("客户端类型不认识、设备标识太长：422 带原因码")
  void should_reject_invalid_client_fields() {
    register("flow.invalid@example.com");

    EntityExchangeResult<String> badType =
        post(
            "/auth/login",
            Map.of("email", "flow.invalid@example.com", "password", PASSWORD, "clientType", "app"));
    EntityExchangeResult<String> badDevice =
        post(
            "/auth/register",
            Map.of(
                "email",
                "flow.invalid2@example.com",
                "password",
                PASSWORD,
                "deviceId",
                "x".repeat(129)));

    assertThat(badType.getStatus().value()).isEqualTo(422);
    JsonNode typeError = JSON.readTree(badType.getResponseBody()).get("errors").get(0);
    assertThat(typeError.get("field").asString()).isEqualTo("clientType");
    assertThat(typeError.get("code").asString()).isEqualTo("INVALID_FORMAT");
    assertThat(badDevice.getStatus().value()).isEqualTo(422);
    JsonNode deviceError = JSON.readTree(badDevice.getResponseBody()).get("errors").get(0);
    assertThat(deviceError.get("field").asString()).isEqualTo("deviceId");
    assertThat(deviceError.get("code").asString()).isEqualTo("TOO_LONG");
  }

  @Test
  @DisplayName("登出后会话消失、记录标 LOGOUT；重复登出和不带身份登出都 204")
  void should_logout_idempotently() {
    EntityExchangeResult<String> registered = register("flow.logout@example.com");
    long userId = userIdOf(registered);
    String hash = SessionToken.parse(tokenOf(registered)).orElseThrow().hash();
    long sessionId = latestSessionIdOf(userId);
    HttpHeaders assertion = assertionFor(userId, sessionId);

    assertThat(logout(assertion).getStatus().value()).isEqualTo(204);

    assertThat(redis.hasKey("idn:session:user:" + hash)).isFalse();
    assertThat(
            redis.opsForHash().hasKey("idn:user-sessions:user:" + userId, Long.toString(sessionId)))
        .isFalse();
    assertThat(endReasonOf(sessionId)).isEqualTo("LOGOUT");
    assertThat(logout(assertion).getStatus().value()).isEqualTo(204);
    assertThat(logout(new HttpHeaders()).getStatus().value()).isEqualTo(204);
    assertThat(endReasonOf(sessionId)).isEqualTo("LOGOUT");
  }

  @Test
  @DisplayName("封禁后全部会话消失、记录标 BANNED，断言还在有效期内也拿不到当前用户；解封后能重新登录")
  void should_ban_and_revoke_all_sessions() {
    EntityExchangeResult<String> registered = register("flow.ban@example.com");
    long userId = userIdOf(registered);
    long firstSessionId = latestSessionIdOf(userId);
    EntityExchangeResult<String> second = login("flow.ban@example.com");
    long secondSessionId = latestSessionIdOf(userId);
    String firstHash = SessionToken.parse(tokenOf(registered)).orElseThrow().hash();
    String secondHash = SessionToken.parse(tokenOf(second)).orElseThrow().hash();

    restClient
        .post()
        .uri("/admin/users/" + userId + "/ban")
        .exchange()
        .expectStatus()
        .isNoContent();

    assertThat(redis.hasKey("idn:session:user:" + firstHash)).isFalse();
    assertThat(redis.hasKey("idn:session:user:" + secondHash)).isFalse();
    assertThat(redis.hasKey("idn:user-sessions:user:" + userId)).isFalse();
    assertThat(endReasonOf(firstSessionId)).isEqualTo("BANNED");
    assertThat(endReasonOf(secondSessionId)).isEqualTo("BANNED");
    assertThat(me(assertionFor(userId, firstSessionId)).getStatus().value()).isEqualTo(401);
    assertThat(login("flow.ban@example.com").getStatus().value()).isEqualTo(403);

    restClient
        .post()
        .uri("/admin/users/" + userId + "/unban")
        .exchange()
        .expectStatus()
        .isNoContent();
    assertThat(login("flow.ban@example.com").getStatus().value()).isEqualTo(200);
  }

  @Test
  @DisplayName("第 11 次登录挤掉最老的会话，它的记录标 REPLACED")
  void should_replace_oldest_session_over_cap() {
    EntityExchangeResult<String> registered = register("flow.cap@example.com");
    long userId = userIdOf(registered);
    long firstSessionId = latestSessionIdOf(userId);
    String firstHash = SessionToken.parse(tokenOf(registered)).orElseThrow().hash();
    for (int i = 0; i < 10; i++) {
      assertThat(login("flow.cap@example.com").getStatus().value()).isEqualTo(200);
    }

    assertThat(redis.opsForHash().size("idn:user-sessions:user:" + userId)).isEqualTo(10);
    assertThat(redis.hasKey("idn:session:user:" + firstHash)).isFalse();
    assertThat(endReasonOf(firstSessionId)).isEqualTo("REPLACED");
  }

  @Test
  @DisplayName("令牌只出现在注册和登录的响应里：当前用户接口和日志里都找不到")
  void should_never_echo_or_log_session_token(CapturedOutput output) {
    EntityExchangeResult<String> registered = register("flow.leak@example.com");
    long userId = userIdOf(registered);
    EntityExchangeResult<String> login = login("flow.leak@example.com");
    List<String> tokens = List.of(tokenOf(registered), tokenOf(login));
    long sessionId = latestSessionIdOf(userId);

    String me = me(assertionFor(userId, sessionId)).getResponseBody();
    String loggedOut = logout(assertionFor(userId, sessionId)).getResponseBody();

    for (String token : tokens) {
      assertThat(me).doesNotContain(token);
      assertThat(loggedOut == null ? "" : loggedOut).doesNotContain(token);
      assertThat(output.getAll()).doesNotContain(token);
    }
  }

  /// 注册，密码固定。
  ///
  /// @param email 邮箱
  /// @return 响应
  private EntityExchangeResult<String> register(String email) {
    return post("/auth/register", Map.of("email", email, "password", PASSWORD));
  }

  /// 登录，密码固定，不带客户端字段。
  ///
  /// @param email 邮箱
  /// @return 响应
  private EntityExchangeResult<String> login(String email) {
    return post("/auth/login", Map.of("email", email, "password", PASSWORD));
  }

  /// 发一个 JSON POST。
  ///
  /// @param uri 路径
  /// @param body 请求体
  /// @return 响应
  private EntityExchangeResult<String> post(String uri, Map<String, Object> body) {
    return restClient
        .post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new HashMap<>(body))
        .exchange()
        .expectBody(String.class)
        .returnResult();
  }

  /// 登出，带或不带断言。
  ///
  /// @param headers 请求头
  /// @return 响应
  private EntityExchangeResult<String> logout(HttpHeaders headers) {
    return restClient
        .post()
        .uri("/auth/logout")
        .headers(h -> h.addAll(headers))
        .exchange()
        .expectBody(String.class)
        .returnResult();
  }

  /// 当前用户，带或不带断言。
  ///
  /// @param headers 请求头
  /// @return 响应
  private EntityExchangeResult<String> me(HttpHeaders headers) {
    return restClient
        .get()
        .uri("/auth/me")
        .headers(h -> h.addAll(headers))
        .exchange()
        .expectBody(String.class)
        .returnResult();
  }

  /// 用测试私钥签一条断言，模拟网关查完会话后的转发。
  ///
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return `Authorization: Bearer` 头
  private static HttpHeaders assertionFor(long userId, long sessionId) {
    return TestIdentity.headers(
        CurrentUser.of(userId, sessionId, AccountType.USER, ClientType.WEB));
  }

  /// 响应里的令牌。
  ///
  /// @param result 注册或登录的响应
  /// @return 令牌
  private static String tokenOf(EntityExchangeResult<String> result) {
    return JSON.readTree(result.getResponseBody()).get("sessionToken").asString();
  }

  /// 响应里的用户 ID。
  ///
  /// @param result 注册或登录的响应
  /// @return 用户 ID
  private static long userIdOf(EntityExchangeResult<String> result) {
    return Long.parseLong(JSON.readTree(result.getResponseBody()).get("userId").asString());
  }

  /// 用户最新一条登录记录的 ID，也就是最新会话的 ID。
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
}

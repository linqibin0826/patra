package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.identity.config.IdentitySecurityMailITSpringConfig;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.security.test.TestIdentity;
import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/// 登录和「当前用户」按四态分流：状态直接用 SQL 改，真实的 PostgreSQL、Redis、Argon2。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(
    initializers = {
      IdentityITPostgreSQLContainerInitializer.class,
      RedisContainerInitializer.class,
      RabbitMQContainerInitializer.class
    })
@ActiveProfiles("test")
@Import(IdentitySecurityMailITSpringConfig.class)
@AutoConfigureRestTestClient
@DisplayName("登录与当前用户按四态分流")
class UserStatusFlowIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String PASSWORD = "Correct-Horse-01";

  @Autowired private RestTestClient restClient;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("冷静期可登录且响应带状态；封禁 403 且当前用户 401；注销后按密码错误处理")
  void should_branch_login_by_status() {
    EntityExchangeResult<String> registered = register("status@example.com", "status-user");
    assertThat(registered.getStatus().value()).isEqualTo(201);
    long userId = userIdOf(registered);
    JsonNode activeUser = JSON.readTree(registered.getResponseBody()).get("user");
    assertThat(activeUser.get("status").asString()).isEqualTo("ACTIVE");
    assertThat(activeUser.get("deletionDueAt").isNull()).isTrue();

    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'PENDING_DELETION', "
            + "deletion_due_at = now() + interval '15 days' WHERE id = ?",
        userId);
    EntityExchangeResult<String> pending = login("status@example.com", PASSWORD);
    assertThat(pending.getStatus().value()).isEqualTo(200);
    JsonNode pendingUser = JSON.readTree(pending.getResponseBody()).get("user");
    assertThat(pendingUser.get("status").asString()).isEqualTo("PENDING_DELETION");
    assertThat(pendingUser.get("deletionDueAt").isNull()).isFalse();
    assertThat(pendingUser.get("username").asString()).isEqualTo("status-user");
    EntityExchangeResult<String> me = me(assertionFor(userId));
    assertThat(me.getStatus().value()).isEqualTo(200);
    assertThat(JSON.readTree(me.getResponseBody()).get("status").asString())
        .isEqualTo("PENDING_DELETION");

    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'BANNED', banned_at = now(), deletion_due_at = NULL "
            + "WHERE id = ?",
        userId);
    assertThat(login("status@example.com", PASSWORD).getStatus().value()).isEqualTo(403);
    assertThat(me(assertionFor(userId)).getStatus().value()).isEqualTo(401);

    jdbcTemplate.update("DELETE FROM idn_user_password_credential WHERE user_id = ?", userId);
    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'DELETED', deleted_at = now(), banned_at = NULL, "
            + "email = NULL, email_verified_at = NULL WHERE id = ?",
        userId);
    EntityExchangeResult<String> deleted = login("status@example.com", PASSWORD);
    EntityExchangeResult<String> wrong = login("nobody-here@example.com", "Wrong-Pass-01");
    assertThat(deleted.getStatus().value()).isEqualTo(401);
    assertThat(comparable(deleted.getResponseBody()))
        .isEqualTo(comparable(wrong.getResponseBody()));
    assertThat(me(assertionFor(userId)).getStatus().value()).isEqualTo(401);
  }

  /// 注册。
  ///
  /// @param email 邮箱
  /// @param username 用户名
  /// @return 响应
  private EntityExchangeResult<String> register(String email, String username) {
    return post(
        "/auth/register", Map.of("email", email, "username", username, "password", PASSWORD));
  }

  /// 登录。
  ///
  /// @param email 邮箱
  /// @param password 密码
  /// @return 响应
  private EntityExchangeResult<String> login(String email, String password) {
    return post("/auth/login", Map.of("email", email, "password", password));
  }

  /// 发一个 JSON POST。
  ///
  /// @param uri 路径
  /// @param body 请求体
  /// @return 响应
  private EntityExchangeResult<String> post(String uri, Map<String, String> body) {
    return restClient
        .post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectBody(String.class)
        .returnResult();
  }

  /// 当前用户，带断言。
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

  /// 用测试私钥签一条断言，会话 ID 取该用户最新的登录记录。
  ///
  /// @param userId 用户 ID
  /// @return `Authorization: Bearer` 头
  private HttpHeaders assertionFor(long userId) {
    Long sessionId =
        jdbcTemplate.queryForObject(
            "SELECT max(id) FROM idn_user_login_record WHERE user_id = ?", Long.class, userId);
    return TestIdentity.headers(
        CurrentUser.of(userId, sessionId, AccountType.USER, ClientType.WEB));
  }

  /// 注册响应里的用户 ID。
  ///
  /// @param result 响应
  /// @return 用户 ID
  private static long userIdOf(EntityExchangeResult<String> result) {
    return Long.parseLong(
        JSON.readTree(result.getResponseBody()).get("user").get("userId").asString());
  }

  /// 去掉每次都不同的字段，留下可比较的部分。
  ///
  /// @param body 响应体
  /// @return 去掉 `timestamp`、`traceId` 之后的 JSON
  private static JsonNode comparable(String body) {
    ObjectNode node = (ObjectNode) JSON.readTree(body);
    node.remove("timestamp");
    node.remove("traceId");
    return node;
  }

  @Test
  @DisplayName("写接口的状态守卫：冷静期内改名 409 且 traits 含 PENDING_DELETION")
  void should_return_409_with_pending_deletion_trait() {
    long userId = userIdOf(register("guard-pending@example.com", "guard-pending"));
    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'PENDING_DELETION', "
            + "deletion_due_at = now() + interval '15 days' WHERE id = ?",
        userId);

    EntityExchangeResult<String> result = changeUsername(userId, "guard-pending-2");

    assertThat(result.getStatus().value()).isEqualTo(409);
    JsonNode json = JSON.readTree(result.getResponseBody());
    assertThat(json.get("code").asString()).isEqualTo("IDN-0409");
    assertThat(json.get("traits"))
        .extracting(JsonNode::asString)
        .containsExactlyInAnyOrder("CONFLICT", "PENDING_DELETION");
  }

  @Test
  @DisplayName("写接口的状态守卫：断言还在而用户已封禁或已注销，改名 401")
  void should_return_401_when_user_is_banned_or_deleted() {
    long userId = userIdOf(register("guard-gone@example.com", "guard-gone"));

    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'BANNED', banned_at = now() WHERE id = ?", userId);
    assertThat(changeUsername(userId, "guard-gone-2").getStatus().value()).isEqualTo(401);

    jdbcTemplate.update("DELETE FROM idn_user_password_credential WHERE user_id = ?", userId);
    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'DELETED', deleted_at = now(), banned_at = NULL, "
            + "email = NULL, email_verified_at = NULL WHERE id = ?",
        userId);
    assertThat(changeUsername(userId, "guard-gone-2").getStatus().value()).isEqualTo(401);
  }

  /// 以某个用户的身份改名。
  ///
  /// @param userId 用户 ID
  /// @param username 新用户名
  /// @return 响应
  private EntityExchangeResult<String> changeUsername(long userId, String username) {
    return restClient
        .patch()
        .uri("/me/username")
        .headers(h -> h.addAll(assertionFor(userId)))
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("username", username))
        .exchange()
        .expectBody(String.class)
        .returnResult();
  }
}

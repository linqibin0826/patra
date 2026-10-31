package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.patra.identity.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.identity.config.IdentitySecurityMailITSpringConfig;
import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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

/// 前台用户账号的完整流程：真实的 PostgreSQL、Redis、Argon2。
@SpringBootTest(properties = "patra.identity.login-throttle.lock-duration=2s")
@ContextConfiguration(
    initializers = {
      IdentityITPostgreSQLContainerInitializer.class,
      RedisContainerInitializer.class,
      RabbitMQContainerInitializer.class
    })
@ActiveProfiles("test")
@Import(IdentitySecurityMailITSpringConfig.class)
@AutoConfigureRestTestClient
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("前台用户账号的完整流程")
class AccountFlowIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private RestTestClient restClient;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("邮箱的大小写和首尾空白不影响：注册后用任意大小写登录，重复注册返回 409")
  void should_treat_email_case_and_whitespace_as_same_account() {
    EntityExchangeResult<String> registered =
        register("  Case.User@Example.COM ", "Correct-Horse-01");

    assertThat(registered.getStatus().value()).isEqualTo(201);
    JsonNode body = JSON.readTree(registered.getResponseBody());
    assertThat(body.get("user").get("email").asString()).isEqualTo("case.user@example.com");
    assertThat(body.get("user").get("userId").isString()).isTrue();
    assertThat(body.get("user").get("username").asString()).isEqualTo("u-case-user");
    assertThat(body.get("user").get("displayName").asString()).isEqualTo("u-case-user");
    assertThat(login("case.user@example.com", "Correct-Horse-01").getStatus().value())
        .isEqualTo(200);
    assertThat(login("CASE.USER@EXAMPLE.COM", "Correct-Horse-01").getStatus().value())
        .isEqualTo(200);
    EntityExchangeResult<String> duplicate = register("case.user@example.com", "Another-Pass-02");
    assertThat(duplicate.getStatus().value()).isEqualTo(409);
    assertThat(JSON.readTree(duplicate.getResponseBody()).get("code").asString())
        .isEqualTo("IDN-0409");
  }

  @Test
  @DisplayName("密码首尾的空格原样保存：少了空格就是错密码")
  void should_keep_leading_and_trailing_spaces_in_password() {
    register("spaces@example.com", "  padded-secret  ");

    assertThat(login("spaces@example.com", "padded-secret").getStatus().value()).isEqualTo(401);
    assertThat(login("spaces@example.com", "  padded-secret  ").getStatus().value()).isEqualTo(200);
  }

  @Test
  @DisplayName("请求体里夹带的 id、status、role 被忽略")
  void should_ignore_extra_fields_in_request_body() {
    EntityExchangeResult<String> result =
        restClient
            .post()
            .uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                Map.of(
                    "email", "mass@example.com",
                    "username", "mass-user",
                    "password", "Mass-Assign-03",
                    "id", 1,
                    "userId", "1",
                    "status", "BANNED",
                    "role", "ADMIN"))
            .exchange()
            .expectBody(String.class)
            .returnResult();

    assertThat(result.getStatus().value()).isEqualTo(201);
    assertThat(JSON.readTree(result.getResponseBody()).get("user").get("userId").asString())
        .isNotEqualTo("1");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM idn_user WHERE email = ?", String.class, "mass@example.com"))
        .isEqualTo("ACTIVE");
  }

  @Test
  @DisplayName("邮箱不存在和密码错误的响应体，除时间戳和 traceId 外完全相同")
  void should_return_identical_401_for_unknown_email_and_wrong_password() {
    register("victim@example.com", "Victim-Pass-04");

    EntityExchangeResult<String> unknown = login("nobody-here@example.com", "Whatever-Pass-05");
    EntityExchangeResult<String> wrong = login("victim@example.com", "Wrong-Pass-06");

    assertThat(unknown.getStatus().value()).isEqualTo(401);
    assertThat(wrong.getStatus().value()).isEqualTo(401);
    assertThat(comparable(unknown.getResponseBody()))
        .isEqualTo(comparable(wrong.getResponseBody()));
  }

  @Test
  @DisplayName("连续失败 5 次后被锁，正确密码也返回 429；锁到期后恢复")
  void should_lock_after_five_failures_and_recover() {
    register("locked@example.com", "Locked-Pass-07");
    for (int i = 0; i < 4; i++) {
      assertThat(login("locked@example.com", "Wrong-" + i).getStatus().value()).isEqualTo(401);
    }

    EntityExchangeResult<String> fifth = login("locked@example.com", "Wrong-5");
    assertThat(fifth.getStatus().value()).isEqualTo(429);
    assertThat(fifth.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("2");
    JsonNode retryAfterSeconds = JSON.readTree(fifth.getResponseBody()).get("retryAfterSeconds");
    assertThat(retryAfterSeconds.isNumber()).isTrue();
    assertThat(retryAfterSeconds.asLong()).isEqualTo(2);
    assertThat(login("locked@example.com", "Locked-Pass-07").getStatus().value()).isEqualTo(429);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(login("locked@example.com", "Locked-Pass-07").getStatus().value())
                    .isEqualTo(200));
  }

  @Test
  @DisplayName("封禁后密码对返回 403、密码错返回 401；解封后能登录；用户不存在返回 404")
  void should_ban_and_unban() {
    String userId =
        JSON.readTree(register("banned@example.com", "Banned-Pass-08").getResponseBody())
            .get("user")
            .get("userId")
            .asString();

    restClient
        .post()
        .uri("/admin/users/" + userId + "/ban")
        .exchange()
        .expectStatus()
        .isNoContent();
    assertThat(login("banned@example.com", "Banned-Pass-08").getStatus().value()).isEqualTo(403);
    assertThat(login("banned@example.com", "Wrong-Pass-08").getStatus().value()).isEqualTo(401);
    restClient
        .post()
        .uri("/admin/users/" + userId + "/ban")
        .exchange()
        .expectStatus()
        .isNoContent();

    restClient
        .post()
        .uri("/admin/users/" + userId + "/unban")
        .exchange()
        .expectStatus()
        .isNoContent();
    assertThat(login("banned@example.com", "Banned-Pass-08").getStatus().value()).isEqualTo(200);
    restClient.post().uri("/admin/users/1/ban").exchange().expectStatus().isNotFound();
  }

  @Test
  @DisplayName("库里只有哈希；日志和响应里都找不到明文密码")
  void should_never_store_or_echo_plain_password(CapturedOutput output) {
    List<String> passwords =
        List.of(
            "Leak-Probe-Register-09",
            "Leak-Probe-Wrong-10",
            "Leak-Probe-Dup-11",
            "password123",
            "Lp-12");
    List<String> bodies = new ArrayList<>();
    bodies.add(register("leak@example.com", passwords.get(0)).getResponseBody());
    bodies.add(login("leak@example.com", passwords.get(1)).getResponseBody());
    bodies.add(register("leak@example.com", passwords.get(2)).getResponseBody());
    EntityExchangeResult<String> common = register("leak-common@example.com", passwords.get(3));
    EntityExchangeResult<String> tooShort = register("leak-short@example.com", passwords.get(4));
    bodies.add(common.getResponseBody());
    bodies.add(tooShort.getResponseBody());

    assertThat(common.getStatus().value()).isEqualTo(422);
    assertThat(tooShort.getStatus().value()).isEqualTo(422);
    String stored =
        jdbcTemplate.queryForObject(
            "SELECT c.password_hash FROM idn_user_password_credential c "
                + "JOIN idn_user u ON u.id = c.user_id WHERE u.email = ?",
            String.class,
            "leak@example.com");
    assertThat(stored).startsWith("$argon2id$").doesNotContain(passwords.get(0));
    for (String password : passwords) {
      assertThat(bodies).allSatisfy(body -> assertThat(body).doesNotContain(password));
      assertThat(output.getAll()).doesNotContain(password);
    }
  }

  /// 注册。
  ///
  /// @param email 邮箱
  /// @param password 密码
  /// @return 响应
  private EntityExchangeResult<String> register(String email, String password) {
    return post("/auth/register", email, password);
  }

  /// 登录。
  ///
  /// @param email 邮箱
  /// @param password 密码
  /// @return 响应
  private EntityExchangeResult<String> login(String email, String password) {
    return post("/auth/login", email, password);
  }

  /// 发一个带邮箱和密码的 POST。
  ///
  /// @param uri 路径
  /// @param email 邮箱
  /// @param password 密码
  /// @return 响应
  private EntityExchangeResult<String> post(String uri, String email, String password) {
    return restClient
        .post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", email, "username", usernameFor(email), "password", password))
        .exchange()
        .expectBody(String.class)
        .returnResult();
  }

  /// 发一个任意 JSON 体的 POST。
  ///
  /// @param uri 路径
  /// @param body 请求体
  /// @return 响应
  private EntityExchangeResult<String> postJson(String uri, Map<String, ?> body) {
    return restClient
        .post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectBody(String.class)
        .returnResult();
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

  /// 按邮箱派生一个合法的用户名：本地部分小写、`.` 换成 `-`，前面加 `u-`。
  ///
  /// @param email 邮箱
  /// @return 用户名
  private static String usernameFor(String email) {
    String local = email.strip().toLowerCase(Locale.ROOT);
    return "u-" + local.substring(0, local.indexOf('@')).replace('.', '-');
  }

  @Test
  @DisplayName("不带用户名注册：422，errors 只有 username 一条、原因码 REQUIRED")
  void should_return_422_with_reason_code_when_username_missing() {
    EntityExchangeResult<String> result =
        postJson(
            "/auth/register",
            Map.of("email", "no-name@example.com", "password", "Correct-Horse-01"));

    assertThat(result.getStatus().value()).isEqualTo(422);
    JsonNode errors = JSON.readTree(result.getResponseBody()).get("errors");
    assertThat(errors.size()).isEqualTo(1);
    assertThat(errors.get(0).get("field").asString()).isEqualTo("username");
    assertThat(errors.get(0).get("code").asString()).isEqualTo("REQUIRED");
  }

  @Test
  @DisplayName("重复用户名的 409 和重复邮箱的 409 分得开；都重复时两条都在；用户名不分大小写")
  void should_distinguish_duplicate_username_from_duplicate_email() {
    assertThat(registerWith("dup@example.com", "dup-user").getStatus().value()).isEqualTo(201);

    JsonNode sameEmail =
        JSON.readTree(registerWith("dup@example.com", "another").getResponseBody());
    JsonNode sameName =
        JSON.readTree(registerWith("other@example.com", "DUP-USER").getResponseBody());
    JsonNode both = JSON.readTree(registerWith("dup@example.com", "dup-user").getResponseBody());

    assertThat(sameEmail.get("code").asString()).isEqualTo("IDN-0409");
    assertThat(sameEmail.get("errors").size()).isEqualTo(1);
    assertThat(sameEmail.get("errors").get(0).get("field").asString()).isEqualTo("email");
    assertThat(sameEmail.get("errors").get(0).get("code").asString())
        .isEqualTo("ALREADY_REGISTERED");
    assertThat(sameName.get("errors").size()).isEqualTo(1);
    assertThat(sameName.get("errors").get(0).get("field").asString()).isEqualTo("username");
    assertThat(sameName.get("errors").get(0).get("code").asString()).isEqualTo("ALREADY_TAKEN");
    assertThat(both.get("errors").size()).isEqualTo(2);
  }

  @Test
  @DisplayName("冷静期内的用户邮箱仍被占用；注销完成后邮箱可再注册，用户名仍不可用")
  void should_reject_email_of_user_pending_deletion_but_accept_email_of_deleted_user() {
    assertThat(registerWith("gone@example.com", "gone-user").getStatus().value()).isEqualTo(201);
    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'PENDING_DELETION', "
            + "deletion_due_at = now() + interval '15 days' WHERE username = ?",
        "gone-user");

    assertThat(registerWith("gone@example.com", "gone-again").getStatus().value()).isEqualTo(409);

    jdbcTemplate.update(
        "DELETE FROM idn_user_password_credential "
            + "WHERE user_id = (SELECT id FROM idn_user WHERE username = ?)",
        "gone-user");
    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'DELETED', deleted_at = now(), deletion_due_at = NULL, "
            + "email = NULL, email_verified_at = NULL WHERE username = ?",
        "gone-user");

    assertThat(registerWith("gone@example.com", "gone-again").getStatus().value()).isEqualTo(201);
    JsonNode reused =
        JSON.readTree(registerWith("third@example.com", "gone-user").getResponseBody());
    assertThat(reused.get("errors").get(0).get("field").asString()).isEqualTo("username");
  }

  @Test
  @DisplayName("后台对已注销用户封禁、解封都返回 409 IDN-0409")
  void should_reject_ban_and_unban_of_deleted_user_with_409() {
    long userId =
        Long.parseLong(
            JSON.readTree(registerWith("erased@example.com", "erased-user").getResponseBody())
                .get("user")
                .get("userId")
                .asString());
    jdbcTemplate.update("DELETE FROM idn_user_password_credential WHERE user_id = ?", userId);
    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'DELETED', deleted_at = now(), deletion_due_at = NULL, "
            + "email = NULL, email_verified_at = NULL WHERE id = ?",
        userId);

    for (String action : List.of("ban", "unban")) {
      JsonNode json =
          JSON.readTree(
              restClient
                  .post()
                  .uri("/admin/users/" + userId + "/" + action)
                  .exchange()
                  .expectBody(String.class)
                  .returnResult()
                  .getResponseBody());
      assertThat(json.get("status").asInt()).as(action).isEqualTo(409);
      assertThat(json.get("code").asString()).as(action).isEqualTo("IDN-0409");
    }
  }

  /// 用指定的用户名注册，密码固定。
  ///
  /// @param email 邮箱
  /// @param username 用户名
  /// @return 响应
  private EntityExchangeResult<String> registerWith(String email, String username) {
    return postJson(
        "/auth/register",
        Map.of("email", email, "username", username, "password", "Correct-Horse-01"));
  }
}

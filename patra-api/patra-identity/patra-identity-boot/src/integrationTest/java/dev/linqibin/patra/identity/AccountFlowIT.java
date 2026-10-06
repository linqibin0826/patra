package dev.linqibin.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.linqibin.patra.identity.config.IdentityITPostgreSQLContainerInitializer;
import dev.linqibin.starter.test.container.initializer.RedisContainerInitializer;
import java.time.Duration;
import java.util.ArrayList;
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
      RedisContainerInitializer.class
    })
@ActiveProfiles("test")
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
    assertThat(body.get("email").asString()).isEqualTo("case.user@example.com");
    assertThat(body.get("userId").isString()).isTrue();
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
                    "password", "Mass-Assign-03",
                    "id", 1,
                    "userId", "1",
                    "status", "BANNED",
                    "role", "ADMIN"))
            .exchange()
            .expectBody(String.class)
            .returnResult();

    assertThat(result.getStatus().value()).isEqualTo(201);
    assertThat(JSON.readTree(result.getResponseBody()).get("userId").asString()).isNotEqualTo("1");
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
        .body(Map.of("email", email, "password", password))
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
}

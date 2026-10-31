package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.identity.config.IdentitySecurityMailITSpringConfig;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import dev.patra.tool.security.test.TestIdentity;
import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// 公开身份卡的完整流程：未验证对别人 404 对本人 200、验证后公开、旧名 301、封禁后 404。
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
@DisplayName("公开身份卡的完整流程")
class PublicIdentityFlowIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String PASSWORD = "Correct-Horse-01";

  @Autowired private RestTestClient restClient;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private Environment environment;

  @Test
  @DisplayName("未验证：匿名 404、本人 200 且 SELF；验证后公开；改名后昵称跟着变、旧名 301；封禁后都 404")
  void should_expose_public_identity_by_visibility_rules() {
    long userId = register("chen.yu@example.com", "chen-yu");

    assertThat(get("/users/chen-yu").getStatus().value()).isEqualTo(404);
    EntityExchangeResult<String> self = get("/users/chen-yu", assertionFor(userId));
    assertThat(self.getStatus().value()).isEqualTo(200);
    assertThat(JSON.readTree(self.getResponseBody()).get("audience").asString()).isEqualTo("SELF");

    jdbcTemplate.update("UPDATE idn_user SET email_verified_at = now() WHERE id = ?", userId);
    EntityExchangeResult<String> visible = get("/users/chen-yu");
    assertThat(visible.getStatus().value()).isEqualTo(200);
    JsonNode card = JSON.readTree(visible.getResponseBody());
    assertThat(card.get("username").asString()).isEqualTo("chen-yu");
    assertThat(card.get("displayName").asString()).isEqualTo("chen-yu");
    assertThat(card.get("audience").asString()).isEqualTo("PUBLIC");
    assertThat(card.has("email")).isFalse();
    assertThat(get("/users/Chen-Yu").getStatus().value()).isEqualTo(200);
    assertThat(get("/users/ab").getStatus().value()).isEqualTo(404);

    rename(userId, "chen-yu", "chen-yu-2");
    EntityExchangeResult<String> renamed = get("/users/chen-yu-2");
    assertThat(renamed.getStatus().value()).isEqualTo(200);
    assertThat(JSON.readTree(renamed.getResponseBody()).get("displayName").asString())
        .isEqualTo("chen-yu-2");
    RawResponse moved = getWithoutRedirect("/users/chen-yu");
    assertThat(moved.status()).isEqualTo(301);
    assertThat(moved.location()).isEqualTo("/users/chen-yu-2");
    assertThat(JSON.readTree(moved.body()).get("username").asString()).isEqualTo("chen-yu-2");

    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'BANNED', banned_at = now() WHERE id = ?", userId);
    assertThat(get("/users/chen-yu-2").getStatus().value()).isEqualTo(404);
    assertThat(get("/users/chen-yu").getStatus().value()).isEqualTo(404);
  }

  @Test
  @DisplayName("连续改名 A → B → C：旧名 A 跳到当前的 C，不是中间的 B")
  void should_redirect_to_current_name_after_two_renames() {
    long userId = register("alpha@example.com", "alpha");
    jdbcTemplate.update("UPDATE idn_user SET email_verified_at = now() WHERE id = ?", userId);
    rename(userId, "alpha", "beta");
    rename(userId, "beta", "gamma");

    RawResponse moved = getWithoutRedirect("/users/alpha");

    assertThat(moved.status()).isEqualTo(301);
    assertThat(JSON.readTree(moved.body()).get("username").asString()).isEqualTo("gamma");
    assertThat(JSON.readTree(getWithoutRedirect("/users/beta").body()).get("username").asString())
        .isEqualTo("gamma");
  }

  /// 注册，返回用户 ID。
  ///
  /// @param email 邮箱
  /// @param username 用户名
  /// @return 用户 ID
  private long register(String email, String username) {
    EntityExchangeResult<String> result =
        restClient
            .post()
            .uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "username", username, "password", PASSWORD))
            .exchange()
            .expectBody(String.class)
            .returnResult();
    assertThat(result.getStatus().value()).isEqualTo(201);
    return Long.parseLong(
        JSON.readTree(result.getResponseBody()).get("user").get("userId").asString());
  }

  /// 用 SQL 改名并留下一条保留 90 天的旧名记录。
  ///
  /// @param userId 用户 ID
  /// @param from 旧名
  /// @param to 新名
  private void rename(long userId, String from, String to) {
    Instant now = Instant.now();
    jdbcTemplate.update(
        "UPDATE idn_user SET username = ?, username_changed_at = ? WHERE id = ?",
        to,
        Timestamp.from(now),
        userId);
    jdbcTemplate.update(
        "INSERT INTO idn_user_former_username (id, user_id, username, replaced_at, reserved_until) "
            + "VALUES (?, ?, ?, ?, ?)",
        SnowflakeIdGenerator.getId(),
        userId,
        from,
        Timestamp.from(now),
        Timestamp.from(now.plus(Duration.ofDays(90))));
  }

  /// 匿名请求公开身份卡。
  ///
  /// @param uri 路径
  /// @return 响应
  private EntityExchangeResult<String> get(String uri) {
    return get(uri, new HttpHeaders());
  }

  /// 带请求头请求公开身份卡。
  ///
  /// @param uri 路径
  /// @param headers 请求头
  /// @return 响应
  private EntityExchangeResult<String> get(String uri, HttpHeaders headers) {
    return restClient
        .get()
        .uri(uri)
        .headers(h -> h.addAll(headers))
        .exchange()
        .expectBody(String.class)
        .returnResult();
  }

  /// 用测试私钥签一条断言，模拟网关在公开路由上照样翻译了令牌。
  ///
  /// @param userId 用户 ID
  /// @return `Authorization: Bearer` 头
  private static HttpHeaders assertionFor(long userId) {
    return TestIdentity.headers(CurrentUser.of(userId, 1L, AccountType.USER, ClientType.WEB));
  }

  /// 用不跟随跳转的客户端请求：`RestTestClient` 在真实端口上会自动跟着 301 走到新名的 200。
  ///
  /// @param uri 路径
  /// @return 状态、Location 和响应体
  private RawResponse getWithoutRedirect(String uri) {
    RestClient client =
        RestClient.builder()
            .baseUrl("http://localhost:" + environment.getRequiredProperty("local.server.port"))
            .requestFactory(
                new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()))
            .build();
    return client
        .get()
        .uri(uri)
        .exchange(
            (request, response) ->
                new RawResponse(
                    response.getStatusCode().value(),
                    response.getHeaders().getFirst(HttpHeaders.LOCATION),
                    new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
  }

  /// 原始响应。
  ///
  /// @param status 状态码
  /// @param location `Location` 头，没有时为 `null`
  /// @param body 响应体
  private record RawResponse(int status, String location, String body) {}
}

package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.config.IdentityClockITSpringConfig;
import dev.patra.identity.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.identity.config.IdentitySecurityMailITSpringConfig;
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
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// 改名的完整流程（AC 4）：冷却、旧名保留、别人不能占、原主能改回、旧名跳转、90 天后释放。
///
/// 时钟是可拨动的 `MutableClock`；Redis 的 TTL 走真实时间，本用例不依赖会话过期。
/// 改名和公开身份卡的请求走裸的 JDK HttpClient：`RestTestClient` 底层的 Apache 客户端会自动跟随 301。
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
@DisplayName("改名的完整流程")
class UsernameChangeFlowIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String PASSWORD = "Correct-Horse-01";

  @Autowired private RestTestClient restClient;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MutableClock clock;
  @Autowired private Environment environment;

  @Test
  @DisplayName("改名后 30 天内再改 409；旧名 90 天内别人不能注册或改成、原主能改回、/users/旧名 301；90 天后释放")
  void should_enforce_cooldown_reservation_and_redirect() {
    clock.set(IdentityClockITSpringConfig.START);
    long alice = registerAndVerify("alice@example.com", "alice");
    long bob = registerAndVerify("bob@example.com", "bob");

    RawResponse first = changeUsername(alice, "alice-2");
    assertThat(first.status()).as(first.body()).isEqualTo(200);
    RawResponse tooSoon = changeUsername(alice, "alice-3");
    assertThat(tooSoon.status()).isEqualTo(409);
    assertThat(JSON.readTree(tooSoon.body()).get("traits").toString())
        .contains("USERNAME_CHANGE_COOLDOWN");
    JsonNode me = JSON.readTree(me(alice).getResponseBody());
    assertThat(me.get("nextUsernameChangeAt").asString()).isEqualTo("2026-11-19T08:00:00Z");
    assertThat(me.get("username").asString()).isEqualTo("alice-2");
    assertThat(me.get("displayName").asString()).isEqualTo("alice-2");

    assertThat(changeUsername(bob, "alice").status()).isEqualTo(409);
    assertThat(register("carol@example.com", "alice").getStatus().value()).isEqualTo(409);
    RawResponse moved = getWithoutRedirect("/users/alice");
    assertThat(moved.status()).isEqualTo(301);
    assertThat(JSON.readTree(moved.body()).get("username").asString()).isEqualTo("alice-2");

    clock.advance(Duration.ofDays(30));
    RawResponse back = changeUsername(alice, "alice");
    assertThat(back.status()).as(back.body()).isEqualTo(200);

    clock.advance(Duration.ofDays(31));
    assertThat(register("carol@example.com", "alice-2").getStatus().value()).isEqualTo(409);
    clock.advance(Duration.ofDays(60));
    assertThat(register("carol@example.com", "alice-2").getStatus().value()).isEqualTo(201);
    assertThat(getWithoutRedirect("/users/alice-2").status()).isEqualTo(404);
  }

  /// 注册并用 SQL 把邮箱标成已验证，返回用户 ID。
  ///
  /// @param email 邮箱
  /// @param username 用户名
  /// @return 用户 ID
  private long registerAndVerify(String email, String username) {
    EntityExchangeResult<String> result = register(email, username);
    assertThat(result.getStatus().value()).isEqualTo(201);
    long userId =
        Long.parseLong(
            JSON.readTree(result.getResponseBody()).get("user").get("userId").asString());
    jdbcTemplate.update("UPDATE idn_user SET email_verified_at = now() WHERE id = ?", userId);
    return userId;
  }

  /// 注册。
  ///
  /// @param email 邮箱
  /// @param username 用户名
  /// @return 响应
  private EntityExchangeResult<String> register(String email, String username) {
    return restClient
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", email, "username", username, "password", PASSWORD))
        .exchange()
        .expectBody(String.class)
        .returnResult();
  }

  /// 当前用户。
  ///
  /// @param userId 用户 ID
  /// @return 响应
  private EntityExchangeResult<String> me(long userId) {
    return restClient
        .get()
        .uri("/auth/me")
        .headers(h -> h.addAll(assertionFor(userId)))
        .exchange()
        .expectBody(String.class)
        .returnResult();
  }

  /// 以某个用户的身份改名，走裸客户端。
  ///
  /// @param userId 用户 ID
  /// @param username 新用户名
  /// @return 状态和响应体
  private RawResponse changeUsername(long userId, String username) {
    return rawClient()
        .method(HttpMethod.PATCH)
        .uri("/me/username")
        .headers(h -> h.addAll(assertionFor(userId)))
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"username\":\"" + username + "\"}")
        .exchange(UsernameChangeFlowIT::toRaw);
  }

  /// 匿名请求公开身份卡，不跟随跳转。
  ///
  /// @param uri 路径
  /// @return 状态和响应体
  private RawResponse getWithoutRedirect(String uri) {
    return rawClient().get().uri(uri).exchange(UsernameChangeFlowIT::toRaw);
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

  /// 把响应读成状态、`Location` 和响应体。
  ///
  /// @param request 请求
  /// @param response 响应
  /// @return 原始响应
  private static RawResponse toRaw(HttpRequest request, ConvertibleClientHttpResponse response) {
    try {
      return new RawResponse(
          response.getStatusCode().value(),
          response.getHeaders().getFirst(HttpHeaders.LOCATION),
          new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /// 用测试私钥、按可拨动的时钟签一条断言：identity 验签时用的也是这个时钟，拨到 30 天后，
  /// 用真实时间签的 60 秒断言全都「过期」。会话 ID 随便给：改名和当前用户都不查会话。
  ///
  /// @param userId 用户 ID
  /// @return `Authorization: Bearer` 头
  private HttpHeaders assertionFor(long userId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(
        new IdentityAssertionSigner(TestSigningKey.key(), clock)
            .sign(CurrentUser.of(userId, 1L, AccountType.USER, ClientType.WEB)));
    return headers;
  }

  /// 原始响应。
  ///
  /// @param status 状态码
  /// @param location `Location` 头，没有时为 `null`
  /// @param body 响应体
  private record RawResponse(int status, String location, String body) {}
}

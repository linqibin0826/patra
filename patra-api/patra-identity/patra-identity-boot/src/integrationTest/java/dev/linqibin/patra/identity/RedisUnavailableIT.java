package dev.linqibin.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.identity.config.IdentityITPostgreSQLContainerInitializer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

/// Redis 连不上时，注册和登录都返回 503：注册在建会话时失败并整体回滚，登录在失败限制处失败，都不放行。
///
/// 把 Redis 指向一个没人监听的端口，不去停共享的 Redis 测试容器。
@SpringBootTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@DisplayName("Redis 不可用时注册回滚、登录返回 503")
class RedisUnavailableIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private RestTestClient restClient;
  @Autowired private JdbcTemplate jdbcTemplate;

  /// 让 Redis 指向一个没人监听的端口，超时设短。
  ///
  /// @param registry 动态属性
  @DynamicPropertySource
  static void unreachableRedis(DynamicPropertyRegistry registry) {
    int closedPort = closedPort();
    registry.add("spring.data.redis.url", () -> "redis://127.0.0.1:" + closedPort);
    registry.add("spring.data.redis.connect-timeout", () -> "500ms");
    registry.add("spring.data.redis.timeout", () -> "500ms");
  }

  @Test
  @DisplayName("注册回滚、登录拒绝：都是 503 和 IDN-0503，库里没有那个用户")
  void should_return_503_and_roll_back_registration_when_redis_is_unreachable() {
    EntityExchangeResult<String> registered =
        restClient
            .post()
            .uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", "no-redis@example.com", "password", "No-Redis-Pass-13"))
            .exchange()
            .expectBody(String.class)
            .returnResult();

    assertThat(registered.getStatus().value()).isEqualTo(503);
    assertThat(JSON.readTree(registered.getResponseBody()).get("code").asString())
        .isEqualTo("IDN-0503");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM idn_user WHERE email = ?",
                Long.class,
                "no-redis@example.com"))
        .isZero();

    restClient
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "no-redis@example.com", "password", "No-Redis-Pass-13"))
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0503")
        .jsonPath("$.detail")
        .isEqualTo("服务暂时不可用");
  }

  /// 拿一个刚释放、没人监听的端口。
  ///
  /// @return 端口
  private static int closedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

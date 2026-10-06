package dev.linqibin.patra.identity;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/// Redis 连不上时，登录返回 503，不在没有限制的情况下放行（实测点 3）。
///
/// 把 Redis 指向一个没人监听的端口，不去停共享的 Redis 测试容器。
@SpringBootTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@DisplayName("Redis 不可用时登录返回 503")
class RedisUnavailableIT {

  @Autowired private RestTestClient restClient;

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
  @DisplayName("注册不依赖 Redis；登录返回 503 和 IDN-0503")
  void should_return_503_when_redis_is_unreachable() {
    restClient
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "no-redis@example.com", "password", "No-Redis-Pass-13"))
        .exchange()
        .expectStatus()
        .isCreated();

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

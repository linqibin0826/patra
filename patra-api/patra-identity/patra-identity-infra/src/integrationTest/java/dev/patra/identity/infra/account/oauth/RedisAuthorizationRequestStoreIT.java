package dev.patra.identity.infra.account.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/// RedisAuthorizationRequestStore 集成测试：`GETDEL` 取出即删，带过期时间。
@DisplayName("RedisAuthorizationRequestStore 集成测试")
class RedisAuthorizationRequestStoreIT {

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redis;

  /// 连上共享的 Redis 测试容器。
  @BeforeAll
  static void connect() {
    GenericContainer<?> container = RedisContainerInitializer.getRedisContainer();
    connectionFactory =
        new LettuceConnectionFactory(
            new RedisStandaloneConfiguration(container.getHost(), container.getMappedPort(6379)));
    connectionFactory.afterPropertiesSet();
    connectionFactory.start();
    redis = new StringRedisTemplate(connectionFactory);
  }

  /// 断开连接。
  @AfterAll
  static void disconnect() {
    connectionFactory.destroy();
  }

  private final RedisAuthorizationRequestStore store = new RedisAuthorizationRequestStore(redis);

  @Test
  @DisplayName("存了能读，读不删；取出后第二次拿不到")
  void should_consume_only_once() {
    String state = UUID.randomUUID().toString();
    store.save(state, "payload", Duration.ofMinutes(10));

    assertThat(store.find(state)).contains("payload");
    assertThat(store.consume(state)).contains("payload");
    assertThat(store.consume(state)).isEmpty();
    assertThat(store.find(state)).isEmpty();
  }

  @Test
  @DisplayName("键带过期时间")
  void should_expire() {
    String state = UUID.randomUUID().toString();
    store.save(state, "payload", Duration.ofMinutes(10));

    assertThat(redis.getExpire(RedisAuthorizationRequestStore.key(state))).isBetween(500L, 600L);
  }
}

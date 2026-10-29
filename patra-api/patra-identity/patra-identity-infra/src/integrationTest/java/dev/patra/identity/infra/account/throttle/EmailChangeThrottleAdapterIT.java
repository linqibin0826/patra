package dev.patra.identity.infra.account.throttle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.policy.EmailChangePolicy;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/// EmailChangeThrottleAdapter 集成测试：每账号每天的窗口。
@DisplayName("EmailChangeThrottleAdapter 集成测试")
class EmailChangeThrottleAdapterIT {

  private static final Instant T0 = Instant.parse("2026-10-22T08:00:00Z");
  private static final AtomicLong NEXT_USER = new AtomicLong(7_000_000L);

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

  private final EmailChangeThrottleAdapter adapter =
      new EmailChangeThrottleAdapter(redis, EmailChangePolicy.of(3));

  @Test
  @DisplayName("每天第 4 次被拒，要等到最早那次满 24 小时；不同用户互不影响；没有冷却")
  void should_block_fourth_request_per_day_and_isolate_users() {
    long user = NEXT_USER.getAndIncrement();

    assertThat(adapter.acquire(user, T0)).isEmpty();
    assertThat(adapter.acquire(user, T0.plusSeconds(1))).isEmpty();
    assertThat(adapter.acquire(user, T0.plusSeconds(2))).isEmpty();
    assertThat(adapter.acquire(user, T0.plusSeconds(3)))
        .contains(Duration.ofHours(24).minusSeconds(3));
    assertThat(adapter.acquire(NEXT_USER.getAndIncrement(), T0.plusSeconds(3))).isEmpty();
    assertThat(adapter.acquire(user, T0.plus(Duration.ofHours(24)))).isEmpty();
  }
}

package dev.patra.identity.infra.session.throttle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.session.model.vo.DeviceId;
import dev.patra.identity.domain.session.policy.NewDeviceLoginPolicy;
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

/// NewDeviceMailThrottleAdapter 集成测试：同一账号同一设备 24 小时 1 封（PAP-99 设计第 12.3 节）。
@DisplayName("NewDeviceMailThrottleAdapter 集成测试")
class NewDeviceMailThrottleAdapterIT {

  private static final Instant T0 = Instant.parse("2026-10-22T08:00:00Z");
  private static final AtomicLong NEXT_USER = new AtomicLong(8_000_000L);
  private static final DeviceId DEVICE_A =
      new DeviceId("Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy");
  private static final DeviceId DEVICE_B =
      new DeviceId("Zz9_y-8xW7vU6tS5rQ4pO3nM2lK1jI0hG9fE8dC7bAa");

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

  private final NewDeviceMailThrottleAdapter adapter =
      new NewDeviceMailThrottleAdapter(
          redis, NewDeviceLoginPolicy.of(Duration.ofDays(180), Duration.ofHours(24)));

  @Test
  @DisplayName("同设备 24 小时内第二次不放行，满 24 小时放行；别的设备、别的账号互不影响")
  void should_allow_one_mail_per_device_per_day() {
    long user = NEXT_USER.getAndIncrement();

    assertThat(adapter.acquire(user, DEVICE_A, T0)).isTrue();
    assertThat(adapter.acquire(user, DEVICE_A, T0.plus(Duration.ofHours(23)))).isFalse();
    assertThat(adapter.acquire(user, DEVICE_B, T0.plusSeconds(1))).isTrue();
    assertThat(adapter.acquire(NEXT_USER.getAndIncrement(), DEVICE_A, T0.plusSeconds(1))).isTrue();
    assertThat(adapter.acquire(user, DEVICE_A, T0.plus(Duration.ofHours(24)))).isTrue();
  }

  @Test
  @DisplayName("清掉一台设备的冷却后立即放行；别的设备的冷却不受影响；没有设备标识的桶也能清")
  void should_allow_again_after_reset() {
    long user = NEXT_USER.getAndIncrement();
    assertThat(adapter.acquire(user, DEVICE_A, T0)).isTrue();
    assertThat(adapter.acquire(user, DEVICE_B, T0)).isTrue();
    assertThat(adapter.acquire(user, null, T0)).isTrue();

    adapter.reset(user, DEVICE_A);
    adapter.reset(user, null);

    assertThat(adapter.acquire(user, DEVICE_A, T0.plusSeconds(60))).isTrue();
    assertThat(adapter.acquire(user, DEVICE_B, T0.plusSeconds(60))).isFalse();
    assertThat(adapter.acquire(user, null, T0.plusSeconds(60))).isTrue();
  }

  @Test
  @DisplayName("没有设备标识的登录共用一个桶；键里不出现设备标识原文")
  void should_share_one_bucket_without_device_and_hide_device_in_key() {
    long user = NEXT_USER.getAndIncrement();

    assertThat(adapter.acquire(user, null, T0)).isTrue();
    assertThat(adapter.acquire(user, null, T0.plusSeconds(60))).isFalse();
    assertThat(NewDeviceMailThrottleAdapter.key(user, null)).endsWith(":none");
    assertThat(NewDeviceMailThrottleAdapter.key(user, DEVICE_A))
        .doesNotContain(DEVICE_A.value())
        .matches("idn:new-device-mail:user:" + user + ":[0-9a-f]{32}");
  }
}

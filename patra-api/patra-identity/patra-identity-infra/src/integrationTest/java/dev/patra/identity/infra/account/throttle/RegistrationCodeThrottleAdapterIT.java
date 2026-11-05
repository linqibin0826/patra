package dev.patra.identity.infra.account.throttle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.policy.ProviderRegistrationPolicy;
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

/// RegistrationCodeThrottleAdapter 集成测试：同邮箱冷却与小时窗口、同 IP 小时窗口。
@DisplayName("RegistrationCodeThrottleAdapter 集成测试")
class RegistrationCodeThrottleAdapterIT {

  private static final Instant T0 = Instant.parse("2026-11-04T08:00:00Z");
  private static final ProviderRegistrationPolicy POLICY =
      ProviderRegistrationPolicy.builder()
          .pendingTtl(Duration.ofMinutes(30))
          .codeTtl(Duration.ofMinutes(10))
          .maxCodeAttempts(5)
          .resendCooldown(Duration.ofSeconds(60))
          .perEmailHourly(5)
          .perIpHourly(20)
          .linkPerEmailDaily(10)
          .build();
  private static final AtomicLong NEXT = new AtomicLong(1);

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

  private final RegistrationCodeThrottleAdapter adapter =
      new RegistrationCodeThrottleAdapter(redis, POLICY);

  private static EmailAddress uniqueEmail() {
    return EmailAddress.of("code" + NEXT.getAndIncrement() + "@example.com");
  }

  private static String uniqueIp() {
    return "203.0.113." + NEXT.getAndIncrement() % 250;
  }

  @Test
  @DisplayName("同邮箱 60 秒冷却")
  void should_cool_down_same_email() {
    EmailAddress email = uniqueEmail();
    String ip = uniqueIp();

    assertThat(adapter.acquire(email, ip, T0)).isEmpty();
    assertThat(adapter.acquire(email, ip, T0.plusSeconds(10))).contains(Duration.ofSeconds(50));
    assertThat(adapter.acquire(email, ip, T0.plusSeconds(60))).isEmpty();
  }

  @Test
  @DisplayName("同邮箱每小时 5 封：第 6 封被拒")
  void should_limit_same_email_per_hour() {
    EmailAddress email = uniqueEmail();

    for (int i = 0; i < 5; i++) {
      assertThat(adapter.acquire(email, "198.51.100.1", T0.plusSeconds(61L * i))).isEmpty();
    }

    assertThat(adapter.acquire(email, "198.51.100.1", T0.plusSeconds(61L * 5))).isPresent();
  }

  @Test
  @DisplayName("绑定用途同邮箱每天 10 封：第 11 封被拒，等到最早那封滑出窗口；换个邮箱不受影响")
  void should_limit_link_codes_per_email_per_day() {
    EmailAddress email = uniqueEmail();
    Duration step = Duration.ofHours(2);
    for (int i = 0; i < 10; i++) {
      assertThat(adapter.acquireForLink(email, uniqueIp(), T0.plus(step.multipliedBy(i))))
          .isEmpty();
    }
    Instant eleventh = T0.plus(step.multipliedBy(10));

    assertThat(adapter.acquireForLink(email, uniqueIp(), eleventh)).contains(Duration.ofHours(4));
    assertThat(adapter.acquireForLink(uniqueEmail(), uniqueIp(), eleventh)).isEmpty();
    assertThat(adapter.acquireForLink(email, uniqueIp(), T0.plus(Duration.ofHours(24)))).isEmpty();
  }

  @Test
  @DisplayName("注册发码不占绑定的每日配额，也不受它限制")
  void should_not_apply_daily_link_limit_to_registration_codes() {
    EmailAddress email = uniqueEmail();
    Duration step = Duration.ofHours(2);
    for (int i = 0; i < 11; i++) {
      assertThat(adapter.acquire(email, uniqueIp(), T0.plus(step.multipliedBy(i)))).isEmpty();
    }

    assertThat(adapter.acquireForLink(email, uniqueIp(), T0.plus(step.multipliedBy(11)))).isEmpty();
  }

  @Test
  @DisplayName("绑定和注册共用冷却与小时窗口")
  void should_share_cooldown_between_link_and_registration() {
    EmailAddress email = uniqueEmail();

    assertThat(adapter.acquireForLink(email, uniqueIp(), T0)).isEmpty();
    assertThat(adapter.acquire(email, uniqueIp(), T0.plusSeconds(10)))
        .contains(Duration.ofSeconds(50));
  }

  @Test
  @DisplayName("同 IP 每小时 20 封：第 21 封被拒，取不到 IP 的共用一个桶")
  void should_limit_same_ip_per_hour() {
    Instant start = T0.plus(Duration.ofDays(1));
    for (int i = 0; i < 20; i++) {
      assertThat(adapter.acquire(uniqueEmail(), null, start)).isEmpty();
    }

    assertThat(adapter.acquire(uniqueEmail(), null, start)).isPresent();
  }
}

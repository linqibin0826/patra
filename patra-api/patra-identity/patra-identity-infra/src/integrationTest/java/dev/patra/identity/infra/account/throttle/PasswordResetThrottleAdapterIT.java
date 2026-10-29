package dev.patra.identity.infra.account.throttle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.policy.PasswordResetPolicy;
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

/// PasswordResetThrottleAdapter 集成测试：通用脚本在 redis:7.0.15 上的四个维度，时间全部由测试传入。
@DisplayName("PasswordResetThrottleAdapter 集成测试")
class PasswordResetThrottleAdapterIT {

  private static final Instant T0 = Instant.parse("2026-10-22T08:00:00Z");
  private static final PasswordResetPolicy POLICY =
      PasswordResetPolicy.of(Duration.ofSeconds(60), 5, 20, 10);
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

  private final PasswordResetThrottleAdapter adapter =
      new PasswordResetThrottleAdapter(redis, POLICY);

  @Test
  @DisplayName("同邮箱 60 秒冷却：第一次放行，10 秒后要等 50 秒，满 60 秒放行")
  void should_cool_down_same_email_for_sixty_seconds() {
    EmailAddress email = uniqueEmail();
    String ip = uniqueIp();

    assertThat(adapter.acquireRequest(email, ip, T0)).isEmpty();
    assertThat(adapter.acquireRequest(email, ip, T0.plusSeconds(10)))
        .contains(Duration.ofSeconds(50));
    assertThat(adapter.acquireRequest(email, ip, T0.plusSeconds(60))).isEmpty();
  }

  @Test
  @DisplayName("同邮箱每天第 6 次被拒，要等到最早那次满 24 小时")
  void should_block_sixth_request_per_email_per_day() {
    EmailAddress email = uniqueEmail();
    for (int i = 0; i < 5; i++) {
      assertThat(adapter.acquireRequest(email, uniqueIp(), T0.plusSeconds(61L * i))).isEmpty();
    }

    Instant sixth = T0.plusSeconds(305);
    assertThat(adapter.acquireRequest(email, uniqueIp(), sixth))
        .contains(Duration.between(sixth, T0.plus(Duration.ofHours(24))));
    assertThat(adapter.acquireRequest(email, uniqueIp(), T0.plus(Duration.ofHours(24)))).isEmpty();
  }

  @Test
  @DisplayName("同 IP 跨邮箱每小时第 21 次被拒，要等一小时；没有 IP 时所有未知 IP 共用一个桶，同样第 21 次被拒")
  void should_block_twenty_first_request_per_ip_per_hour_and_share_bucket_for_unknown_ip() {
    String ip = uniqueIp();
    for (int i = 0; i < 20; i++) {
      assertThat(adapter.acquireRequest(uniqueEmail(), ip, T0)).isEmpty();
    }

    assertThat(adapter.acquireRequest(uniqueEmail(), ip, T0)).contains(Duration.ofHours(1));

    redis.delete(PasswordResetThrottleAdapter.requestKeys(uniqueEmail(), null).get(2));
    for (int i = 0; i < 20; i++) {
      assertThat(adapter.acquireRequest(uniqueEmail(), null, T0))
          .as("未知 IP 第 %d 次", i + 1)
          .isEmpty();
    }
    assertThat(adapter.acquireRequest(uniqueEmail(), null, T0)).contains(Duration.ofHours(1));
  }

  @Test
  @DisplayName("提交：同 IP 每分钟第 11 次被拒，要等到最早那次满一分钟；没有 IP 时所有未知 IP 共用一个桶，不放行")
  void should_block_eleventh_submit_per_ip_per_minute_and_for_unknown_ip() {
    String ip = uniqueIp();
    for (int i = 0; i < 10; i++) {
      assertThat(adapter.acquireSubmit(ip, T0.plusSeconds(i))).isEmpty();
    }

    assertThat(adapter.acquireSubmit(ip, T0.plusSeconds(30))).contains(Duration.ofSeconds(30));
    assertThat(adapter.acquireSubmit(ip, T0.plusSeconds(60))).isEmpty();

    redis.delete(PasswordResetThrottleAdapter.submitKey(null));
    for (int i = 0; i < 10; i++) {
      assertThat(adapter.acquireSubmit(null, T0)).as("未知 IP 第 %d 次", i + 1).isEmpty();
    }
    assertThat(adapter.acquireSubmit(null, T0.plusSeconds(15))).contains(Duration.ofSeconds(45));
  }

  @Test
  @DisplayName("几个维度同时超时，返回最长的那个")
  void should_return_longest_wait_when_several_dimensions_block() {
    EmailAddress email = uniqueEmail();
    String ip = uniqueIp();
    for (int i = 0; i < 5; i++) {
      assertThat(adapter.acquireRequest(email, ip, T0.plusSeconds(61L * i))).isEmpty();
    }

    // 第 5 次在 +244s；+254s 时冷却还剩 50 秒，天窗口还剩 86400 - 254 秒
    assertThat(adapter.acquireRequest(email, ip, T0.plusSeconds(254)))
        .contains(Duration.ofSeconds(86_400 - 254));
  }

  /// 每个用例自己的邮箱。
  ///
  /// @return 邮箱
  private static EmailAddress uniqueEmail() {
    return EmailAddress.of("reset-" + NEXT.getAndIncrement() + "@example.com");
  }

  /// 每个用例自己的 IP 文本。
  ///
  /// @return 形如 `198.51.100.N`
  private static String uniqueIp() {
    return "198.51.100." + NEXT.getAndIncrement();
  }
}

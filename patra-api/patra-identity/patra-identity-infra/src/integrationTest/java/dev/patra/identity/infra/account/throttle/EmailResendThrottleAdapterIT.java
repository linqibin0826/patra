package dev.patra.identity.infra.account.throttle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.policy.EmailResendPolicy;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/// EmailResendThrottleAdapter 集成测试：一段 Lua 在 redis:7.0.15 上的四个维度，时间全部由测试传入。
@DisplayName("EmailResendThrottleAdapter 集成测试")
class EmailResendThrottleAdapterIT {

  private static final Instant T0 = Instant.parse("2026-10-21T08:00:00Z");
  private static final EmailResendPolicy POLICY =
      EmailResendPolicy.of(Duration.ofSeconds(60), 5, 10, 30);
  private static final AtomicLong NEXT_USER = new AtomicLong(9_000_000L);
  private static final AtomicLong NEXT_IP = new AtomicLong(1);

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redis;

  private final EmailResendThrottleAdapter adapter = new EmailResendThrottleAdapter(redis, POLICY);

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

  @Test
  @DisplayName("第一次放行；60 秒内再试要等剩下的秒数；满 60 秒放行")
  void should_allow_first_send_and_block_within_cooldown() {
    long user = NEXT_USER.getAndIncrement();
    String ip = uniqueIp();

    assertThat(adapter.acquire(user, ip, T0)).isEmpty();
    assertThat(adapter.acquire(user, ip, T0.plusSeconds(10))).contains(Duration.ofSeconds(50));
    assertThat(adapter.acquire(user, ip, T0.plusSeconds(60))).isEmpty();
  }

  @Test
  @DisplayName("一小时内第 6 次被拒，要等到最早那次满一小时；满了放行")
  void should_block_sixth_send_within_an_hour_until_oldest_leaves_window() {
    long user = NEXT_USER.getAndIncrement();
    String ip = uniqueIp();
    for (int i = 0; i < 5; i++) {
      assertThat(adapter.acquire(user, ip, T0.plusSeconds(61L * i))).isEmpty();
    }

    assertThat(adapter.acquire(user, ip, T0.plusSeconds(305)))
        .contains(Duration.ofSeconds(3600 - 305));
    assertThat(adapter.acquire(user, ip, T0.plusSeconds(3600))).isEmpty();
  }

  @Test
  @DisplayName("一天内第 11 次被拒，要等到最早那次满 24 小时；满了放行")
  void should_block_eleventh_send_within_a_day() {
    long user = NEXT_USER.getAndIncrement();
    String ip = uniqueIp();
    Duration tenMinutes = Duration.ofMinutes(10);
    for (int i = 0; i < 10; i++) {
      // 每 10 分钟一次：第 6 次起最早的那次已出小时窗口
      Instant at =
          T0.plus(tenMinutes.multipliedBy(i)).plus(i >= 5 ? Duration.ofMinutes(10) : Duration.ZERO);
      assertThat(adapter.acquire(user, ip, at)).as("第 %d 次", i + 1).isEmpty();
    }

    Instant eleventh = T0.plus(Duration.ofMinutes(110));
    assertThat(adapter.acquire(user, ip, eleventh))
        .contains(Duration.between(eleventh, T0.plus(Duration.ofHours(24))));
    assertThat(adapter.acquire(user, ip, T0.plus(Duration.ofHours(24)))).isEmpty();
  }

  @Test
  @DisplayName("同一 IP 跨用户第 31 次被拒，要等一小时；没有 IP 的请求跳过这一维")
  void should_block_thirty_first_send_from_same_ip_across_users() {
    String ip = uniqueIp();
    for (int i = 0; i < 30; i++) {
      assertThat(adapter.acquire(NEXT_USER.getAndIncrement(), ip, T0)).isEmpty();
    }

    assertThat(adapter.acquire(NEXT_USER.getAndIncrement(), ip, T0)).contains(Duration.ofHours(1));
    assertThat(adapter.acquire(NEXT_USER.getAndIncrement(), null, T0)).isEmpty();
  }

  @Test
  @DisplayName("几个维度同时超时，返回最长的那个")
  void should_return_longest_wait_when_several_dimensions_block() {
    long user = NEXT_USER.getAndIncrement();
    String ip = uniqueIp();
    for (int i = 0; i < 5; i++) {
      assertThat(adapter.acquire(user, ip, T0.plusSeconds(61L * i))).isEmpty();
    }

    // 第 5 次在 +244s；+254s 时冷却还剩 50 秒，小时窗口还剩 3346 秒
    assertThat(adapter.acquire(user, ip, T0.plusSeconds(254))).contains(Duration.ofSeconds(3346));
  }

  @Test
  @DisplayName("markSent 只计数不判：连记六次不抛，之后的 acquire 被小时窗口拦住（比冷却更久）")
  void should_count_without_blocking_on_mark_sent() {
    long user = NEXT_USER.getAndIncrement();
    for (int i = 0; i < 6; i++) {
      adapter.markSent(user, T0);
    }

    assertThat(adapter.acquire(user, uniqueIp(), T0)).contains(Duration.ofHours(1));
    assertThat(adapter.acquire(user, uniqueIp(), T0.plusSeconds(3600))).isEmpty();
  }

  @Test
  @DisplayName("markSent 不计 IP 维度：占位键不存在，30 个用户记过之后带 IP 的 acquire 照样放行")
  void should_not_count_ip_dimension_on_mark_sent() {
    for (int i = 0; i < 30; i++) {
      adapter.markSent(NEXT_USER.getAndIncrement(), T0);
    }

    assertThat(redis.hasKey("idn:email-resend:hour:ip:-")).isFalse();
    assertThat(adapter.acquire(NEXT_USER.getAndIncrement(), uniqueIp(), T0)).isEmpty();
  }

  @Test
  @DisplayName("四个键都带 TTL")
  void should_set_ttl_on_keys() {
    long user = NEXT_USER.getAndIncrement();
    String ip = uniqueIp();
    adapter.acquire(user, ip, T0);

    List<String> keys = EmailResendThrottleAdapter.keys(user, ip);
    assertThat(keys)
        .allSatisfy(key -> assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isPositive());
    assertThat(redis.getExpire(keys.get(0), TimeUnit.MILLISECONDS)).isLessThanOrEqualTo(60_000L);
    assertThat(redis.getExpire(keys.get(2), TimeUnit.MILLISECONDS)).isGreaterThan(3_600_000L);
  }

  @Test
  @DisplayName("Redis 连不上：503 的异常")
  void should_translate_connection_failure_to_503() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    LettuceConnectionFactory broken =
        new LettuceConnectionFactory(
            new RedisStandaloneConfiguration("127.0.0.1", closedPort),
            LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(500))
                .shutdownTimeout(Duration.ZERO)
                .build());
    broken.afterPropertiesSet();
    broken.start();
    try {
      EmailResendThrottleAdapter unreachable =
          new EmailResendThrottleAdapter(new StringRedisTemplate(broken), POLICY);
      assertThatThrownBy(() -> unreachable.acquire(1L, "203.0.113.9", T0))
          .isInstanceOf(TemporarilyUnavailableException.class);
      assertThatThrownBy(() -> unreachable.markSent(1L, T0))
          .isInstanceOf(TemporarilyUnavailableException.class);
    } finally {
      broken.destroy();
    }
  }

  /// 每个用例自己的 IP 文本，避免互相干扰。
  ///
  /// @return 形如 `198.51.100.N`
  private static String uniqueIp() {
    return "198.51.100." + NEXT_IP.getAndIncrement();
  }
}

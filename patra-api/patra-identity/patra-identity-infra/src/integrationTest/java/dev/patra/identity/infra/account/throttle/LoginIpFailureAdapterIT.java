package dev.patra.identity.infra.account.throttle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/// LoginIpFailureAdapter 集成测试：计数脚本在真实 Redis 上的行为。
@DisplayName("LoginIpFailureAdapter 集成测试")
class LoginIpFailureAdapterIT {

  private static final Duration WINDOW = Duration.ofMinutes(15);

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

  @Test
  @DisplayName("记一次失败，计数加一")
  void record_increments_count() {
    LoginIpFailureAdapter adapter = new LoginIpFailureAdapter(redis);
    String ip = uniqueIp();

    adapter.record(ip, WINDOW);
    adapter.record(ip, WINDOW);

    assertThat(adapter.count(ip)).isEqualTo(2);
  }

  @Test
  @DisplayName("拿不到 IP 时计数是 0")
  void count_is_zero_for_null_ip() {
    assertThat(new LoginIpFailureAdapter(redis).count(null)).isZero();
  }

  @Test
  @DisplayName("拿不到 IP 时什么都不写，不把请求算到同一个键")
  void record_is_noop_for_null_ip() {
    LoginIpFailureAdapter adapter = new LoginIpFailureAdapter(redis);

    adapter.record(null, WINDOW);
    adapter.record("not-an-ip", WINDOW);

    assertThat(adapter.count(null)).isZero();
    assertThat(adapter.count("not-an-ip")).isZero();
  }

  @Test
  @DisplayName("窗口从第一次失败算起，后面的失败不续期")
  void first_failure_sets_expiry_and_later_failures_keep_it() throws InterruptedException {
    LoginIpFailureAdapter adapter = new LoginIpFailureAdapter(redis);
    String ip = uniqueIp();
    String key = LoginIpFailureAdapter.key(ClientNetworkKey.of(ip).orElseThrow());

    adapter.record(ip, WINDOW);
    Long first = redis.getExpire(key, TimeUnit.MILLISECONDS);
    Thread.sleep(20);
    adapter.record(ip, WINDOW);
    Long second = redis.getExpire(key, TimeUnit.MILLISECONDS);

    assertThat(first).isPositive().isLessThanOrEqualTo(WINDOW.toMillis());
    assertThat(second).isPositive().isLessThan(first);
  }

  @Test
  @DisplayName("同一个 /64 的 IPv6 共用计数")
  void same_ipv6_network_shares_count() {
    LoginIpFailureAdapter adapter = new LoginIpFailureAdapter(redis);
    int segment = ThreadLocalRandom.current().nextInt(1, 0xffff);
    String network = "2001:db8:" + Integer.toHexString(segment) + ":0:";

    adapter.record(network + "1::1", WINDOW);
    adapter.record(network + "2::2", WINDOW);

    assertThat(adapter.count(network + "3::3")).isEqualTo(2);
  }

  /// 每个测试一个不重复的 IP，共享容器里互不影响。
  ///
  /// @return IP
  private static String uniqueIp() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(256);
  }
}

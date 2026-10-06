package dev.linqibin.patra.identity.infra.adapter.throttle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.exception.LoginTemporarilyLockedException;
import dev.linqibin.patra.identity.domain.exception.TemporarilyUnavailableException;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.policy.LoginThrottlePolicy;
import dev.linqibin.patra.identity.domain.port.throttle.LoginAttempt;
import dev.linqibin.starter.test.container.initializer.RedisContainerInitializer;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/// LoginThrottleAdapter 集成测试：两段 Lua 脚本在 redis:7.0.15 上的行为（实测点 4）。
@DisplayName("LoginThrottleAdapter 集成测试")
class LoginThrottleAdapterIT {

  private static final Duration FIFTEEN_MINUTES = Duration.ofMinutes(15);
  private static final LoginThrottlePolicy DEFAULT_POLICY =
      LoginThrottlePolicy.of(5, FIFTEEN_MINUTES, FIFTEEN_MINUTES, Duration.ofSeconds(30));

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
  @DisplayName("连续失败 5 次，第 5 次上锁并返回锁定时长，之后的尝试被拒")
  void should_lock_on_fifth_failure() {
    LoginThrottleAdapter adapter = new LoginThrottleAdapter(redis, DEFAULT_POLICY);
    EmailAddress email = uniqueEmail();

    for (int i = 0; i < 4; i++) {
      assertThat(adapter.recordFailure(adapter.begin(AccountType.USER, email))).isEmpty();
    }
    Optional<Duration> lock = adapter.recordFailure(adapter.begin(AccountType.USER, email));

    assertThat(lock).contains(FIFTEEN_MINUTES);
    assertThatThrownBy(() -> adapter.begin(AccountType.USER, email))
        .isInstanceOf(LoginTemporarilyLockedException.class)
        .satisfies(
            e ->
                assertThat(((LoginTemporarilyLockedException) e).getRetryAfter())
                    .isPositive()
                    .isLessThanOrEqualTo(FIFTEEN_MINUTES));
  }

  @Test
  @DisplayName("中间成功一次，失败计数清零")
  void should_reset_failures_on_success() {
    LoginThrottleAdapter adapter = new LoginThrottleAdapter(redis, DEFAULT_POLICY);
    EmailAddress email = uniqueEmail();
    failTimes(adapter, email, 4);

    adapter.recordSuccess(adapter.begin(AccountType.USER, email));

    failTimes(adapter, email, 4);
    assertThat(adapter.begin(AccountType.USER, email)).isNotNull();
  }

  @Test
  @DisplayName("并发 20 个错误尝试：只放行 5 个，其余拿到 1 秒的 429；5 个都失败后上锁")
  void should_admit_at_most_five_concurrent_attempts() throws Exception {
    LoginThrottleAdapter adapter = new LoginThrottleAdapter(redis, DEFAULT_POLICY);
    EmailAddress email = uniqueEmail();
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Object>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
      for (int i = 0; i < 20; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    return adapter.begin(AccountType.USER, email);
                  } catch (LoginTemporarilyLockedException e) {
                    return e;
                  }
                }));
      }
      start.countDown();
      List<LoginAttempt> admitted = new ArrayList<>();
      List<LoginTemporarilyLockedException> rejected = new ArrayList<>();
      for (Future<Object> result : results) {
        Object value = result.get();
        if (value instanceof LoginAttempt attempt) {
          admitted.add(attempt);
        } else {
          rejected.add((LoginTemporarilyLockedException) value);
        }
      }

      assertThat(admitted).hasSize(5);
      assertThat(rejected)
          .hasSize(15)
          .allSatisfy(e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(1)));
      long locks = admitted.stream().filter(a -> adapter.recordFailure(a).isPresent()).count();
      assertThat(locks).isEqualTo(1);
    }
    assertThatThrownBy(() -> adapter.begin(AccountType.USER, email))
        .isInstanceOf(LoginTemporarilyLockedException.class);
  }

  @Test
  @DisplayName("同时提交 6 次正确密码：至多一次拿到 429，不上锁")
  void should_not_lock_on_concurrent_successful_attempts() {
    LoginThrottleAdapter adapter = new LoginThrottleAdapter(redis, DEFAULT_POLICY);
    EmailAddress email = uniqueEmail();
    List<LoginAttempt> inFlight = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      inFlight.add(adapter.begin(AccountType.USER, email));
    }

    assertThatThrownBy(() -> adapter.begin(AccountType.USER, email))
        .isInstanceOf(LoginTemporarilyLockedException.class);

    inFlight.forEach(adapter::recordSuccess);
    assertThat(adapter.begin(AccountType.USER, email)).isNotNull();
  }

  @Test
  @DisplayName("5 次取消之后，下一次尝试照常放行")
  void should_release_slots_on_cancel() {
    LoginThrottleAdapter adapter = new LoginThrottleAdapter(redis, DEFAULT_POLICY);
    EmailAddress email = uniqueEmail();
    List<LoginAttempt> inFlight = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      inFlight.add(adapter.begin(AccountType.USER, email));
    }

    inFlight.forEach(adapter::cancel);

    LoginAttempt next = adapter.begin(AccountType.USER, email);
    adapter.recordSuccess(next);
    failTimes(adapter, email, 4);
    assertThat(adapter.begin(AccountType.USER, email)).isNotNull();
  }

  @Test
  @DisplayName("先放行 A，B 成功之后 A 才按失败结算：只计 1 次，不凭旧计数上锁")
  void should_count_late_failure_in_current_window() {
    LoginThrottleAdapter adapter = new LoginThrottleAdapter(redis, DEFAULT_POLICY);
    EmailAddress email = uniqueEmail();
    failTimes(adapter, email, 3);
    LoginAttempt late = adapter.begin(AccountType.USER, email);
    LoginAttempt success = adapter.begin(AccountType.USER, email);

    adapter.recordSuccess(success);

    assertThat(adapter.recordFailure(late)).isEmpty();
    failTimes(adapter, email, 3);
    assertThat(adapter.recordFailure(adapter.begin(AccountType.USER, email))).isPresent();
  }

  @Test
  @DisplayName("锁定期间结算的失败不计数")
  void should_not_count_failure_settled_during_lock() {
    LoginThrottlePolicy shortLock =
        LoginThrottlePolicy.of(5, FIFTEEN_MINUTES, Duration.ofSeconds(1), Duration.ofSeconds(30));
    LoginThrottleAdapter adapter = new LoginThrottleAdapter(redis, shortLock);
    EmailAddress email = uniqueEmail();
    failTimes(adapter, email, 5);

    Optional<Duration> during =
        adapter.recordFailure(LoginAttempt.of(AccountType.USER, email, "stale-ticket"));

    assertThat(during).isPresent();
    await()
        .atMost(Duration.ofSeconds(5))
        .ignoreException(LoginTemporarilyLockedException.class)
        .untilAsserted(() -> adapter.cancel(adapter.begin(AccountType.USER, email)));
    failTimes(adapter, email, 4);
    assertThat(adapter.begin(AccountType.USER, email)).isNotNull();
  }

  @Test
  @DisplayName("没结算的在途登记过期后，名额自动释放")
  void should_expire_in_flight_registrations() {
    LoginThrottlePolicy shortTtl =
        LoginThrottlePolicy.of(5, FIFTEEN_MINUTES, FIFTEEN_MINUTES, Duration.ofMillis(300));
    LoginThrottleAdapter adapter = new LoginThrottleAdapter(redis, shortTtl);
    EmailAddress email = uniqueEmail();
    for (int i = 0; i < 5; i++) {
      adapter.begin(AccountType.USER, email);
    }

    assertThatThrownBy(() -> adapter.begin(AccountType.USER, email))
        .isInstanceOf(LoginTemporarilyLockedException.class);
    await()
        .atMost(Duration.ofSeconds(5))
        .ignoreException(LoginTemporarilyLockedException.class)
        .untilAsserted(() -> assertThat(adapter.begin(AccountType.USER, email)).isNotNull());
  }

  @Test
  @DisplayName("计数窗口过期后，失败次数重新算")
  void should_expire_failure_window() {
    LoginThrottlePolicy shortWindow =
        LoginThrottlePolicy.of(5, Duration.ofMillis(500), FIFTEEN_MINUTES, Duration.ofSeconds(30));
    LoginThrottleAdapter adapter = new LoginThrottleAdapter(redis, shortWindow);
    EmailAddress email = uniqueEmail();
    failTimes(adapter, email, 4);

    await().pollDelay(Duration.ofMillis(800)).atMost(Duration.ofSeconds(2)).until(() -> true);

    failTimes(adapter, email, 4);
    assertThat(adapter.begin(AccountType.USER, email)).isNotNull();
  }

  @Test
  @DisplayName("Redis 连不上时抛 TemporarilyUnavailableException（实测点 3 的一部分）")
  void should_translate_connection_failure() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    LettuceConnectionFactory deadFactory =
        new LettuceConnectionFactory(
            new RedisStandaloneConfiguration("127.0.0.1", closedPort),
            LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());
    deadFactory.afterPropertiesSet();
    deadFactory.start();
    try {
      LoginThrottleAdapter adapter =
          new LoginThrottleAdapter(new StringRedisTemplate(deadFactory), DEFAULT_POLICY);

      assertThatThrownBy(() -> adapter.begin(AccountType.USER, uniqueEmail()))
          .isInstanceOf(TemporarilyUnavailableException.class);
    } finally {
      deadFactory.destroy();
    }
  }

  /// 每个用例用不同的邮箱，互不干扰。
  ///
  /// @return 随机邮箱
  private static EmailAddress uniqueEmail() {
    return EmailAddress.of("throttle-" + UUID.randomUUID() + "@example.com");
  }

  /// 连续失败若干次，每次都没有上锁。
  ///
  /// @param adapter 适配器
  /// @param email 邮箱
  /// @param times 次数
  private static void failTimes(LoginThrottleAdapter adapter, EmailAddress email, int times) {
    for (int i = 0; i < times; i++) {
      adapter.recordFailure(adapter.begin(AccountType.USER, email));
    }
  }
}

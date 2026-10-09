package dev.linqibin.patra.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.starter.test.container.initializer.RedisContainerInitializer;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/// RedisSessionStore 集成测试：四段 Lua 在 redis:7.0.15 上的行为。
@DisplayName("RedisSessionStore 集成测试")
class RedisSessionStoreIT {

  static final Instant START = Instant.parse("2026-10-09T08:00:00Z");
  static final Duration IDLE = Duration.ofDays(30);
  static final Duration ABSOLUTE = Duration.ofDays(180);
  /// PTTL 的断言容差：脚本执行和断言之间会过去几毫秒。
  static final long TOLERANCE_MILLIS = 5_000;

  static LettuceConnectionFactory connectionFactory;
  static StringRedisTemplate redis;

  final AdjustableClock clock = new AdjustableClock(START);
  final RedisSessionStore store = new RedisSessionStore(redis, clock);

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

  /// 每个用例从空库开始。
  @BeforeEach
  void flush() {
    redis.execute(
        (RedisCallback<Void>)
            connection -> {
              connection.serverCommands().flushAll();
              return null;
            });
  }

  @Test
  @DisplayName("建会话后能按令牌查到；Redis 里只有哈希，字段齐全，两个键的 TTL 各按规则")
  void should_create_session_and_find_it_by_token() {
    IssuedSession issued = store.create(newSession(42L, 7001L).build());
    SessionToken token = issued.token();

    assertThat(token.value()).startsWith("patra_user_");
    assertThat(issued.replacedSessionIds()).isEmpty();
    StoredSession found = store.findAndTouch(token).orElseThrow();
    assertThat(found.userId()).isEqualTo(42L);
    assertThat(found.sessionId()).isEqualTo(7001L);
    assertThat(found.accountType()).isEqualTo(AccountType.USER);
    assertThat(found.clientType()).isEqualTo(ClientType.WEB);
    assertThat(found.device()).isEmpty();
    assertThat(found.createdAt()).isEqualTo(START);
    assertThat(found.lastActiveAt()).isEqualTo(START);
    assertThat(found.expiresAt()).isEqualTo(START.plus(ABSOLUTE));

    String sessionKey = "idn:session:user:" + token.hash();
    Map<Object, Object> stored = redis.opsForHash().entries(sessionKey);
    assertThat(stored)
        .containsEntry("user_id", "42")
        .containsEntry("session_id", "7001")
        .containsEntry("account_type", "user")
        .containsEntry("client_type", "web")
        .containsEntry("created_at", Long.toString(START.toEpochMilli()))
        .containsEntry("last_active_at", Long.toString(START.toEpochMilli()))
        .containsEntry("expires_at", Long.toString(START.plus(ABSOLUTE).toEpochMilli()))
        .containsEntry("idle_timeout_ms", Long.toString(IDLE.toMillis()))
        .doesNotContainKey("device_id");
    assertThat(redis.opsForHash().get("idn:user-sessions:user:42", "7001")).isEqualTo(token.hash());
    assertThat(redis.keys("*")).allSatisfy(key -> assertThat(key).doesNotContain(token.value()));
    assertThat(ttl(sessionKey)).isBetween(IDLE.toMillis() - TOLERANCE_MILLIS, IDLE.toMillis());
    assertThat(ttl("idn:user-sessions:user:42"))
        .isBetween(ABSOLUTE.toMillis() - TOLERANCE_MILLIS, ABSOLUTE.toMillis());
  }

  @Test
  @DisplayName("给了设备标识就存进去并能读回")
  void should_store_device_id_when_given() {
    IssuedSession issued = store.create(newSession(42L, 7001L).deviceId("mac-safari").build());

    assertThat(store.findAndTouch(issued.token()).orElseThrow().device()).contains("mac-safari");
    assertThat(redis.opsForHash().get("idn:session:user:" + issued.token().hash(), "device_id"))
        .isEqualTo("mac-safari");
  }

  @Test
  @DisplayName("距上次写入不满 60 秒：只读，不写回最后活跃时间，TTL 不重算")
  void should_not_write_back_within_renew_interval() {
    Duration oneHour = Duration.ofHours(1);
    SessionToken token =
        store.create(newSession(42L, 7001L).expiresAt(START.plus(oneHour)).build()).token();
    String sessionKey = "idn:session:user:" + token.hash();
    clock.advance(Duration.ofSeconds(59));

    StoredSession found = store.findAndTouch(token).orElseThrow();

    assertThat(found.lastActiveAt()).isEqualTo(START);
    assertThat(redis.opsForHash().get(sessionKey, "last_active_at"))
        .isEqualTo(Long.toString(START.toEpochMilli()));
    // 续期会把 TTL 重算成 1 小时减 59 秒；没续期就还是约 1 小时（Redis 的 TTL 走真实时间）
    assertThat(ttl(sessionKey))
        .isBetween(oneHour.toMillis() - TOLERANCE_MILLIS, oneHour.toMillis());
  }

  @Test
  @DisplayName("满 60 秒：写回最后活跃时间，TTL 重新按不活跃过期算")
  void should_renew_after_interval() {
    SessionToken token = store.create(newSession(42L, 7001L).build()).token();
    clock.advance(Duration.ofSeconds(60));

    StoredSession found = store.findAndTouch(token).orElseThrow();

    assertThat(found.lastActiveAt()).isEqualTo(START.plusSeconds(60));
    assertThat(redis.opsForHash().get("idn:session:user:" + token.hash(), "last_active_at"))
        .isEqualTo(Long.toString(START.plusSeconds(60).toEpochMilli()));
    assertThat(ttl("idn:session:user:" + token.hash()))
        .isBetween(IDLE.toMillis() - TOLERANCE_MILLIS, IDLE.toMillis());
  }

  @Test
  @DisplayName("绝对过期比不活跃过期先到：建会话和续期的 TTL 都取绝对过期")
  void should_cap_ttl_by_absolute_expiry() {
    Duration oneHour = Duration.ofHours(1);
    SessionToken token =
        store.create(newSession(42L, 7001L).expiresAt(START.plus(oneHour)).build()).token();
    String sessionKey = "idn:session:user:" + token.hash();
    assertThat(ttl(sessionKey))
        .isBetween(oneHour.toMillis() - TOLERANCE_MILLIS, oneHour.toMillis());

    clock.advance(Duration.ofSeconds(61));
    store.findAndTouch(token).orElseThrow();

    long remaining = oneHour.toMillis() - Duration.ofSeconds(61).toMillis();
    assertThat(ttl(sessionKey)).isBetween(remaining - TOLERANCE_MILLIS, remaining);
  }

  @Test
  @DisplayName("到了绝对过期时间：查不到，键被删掉")
  void should_expire_at_absolute_time() {
    SessionToken token =
        store
            .create(newSession(42L, 7001L).expiresAt(START.plus(Duration.ofHours(1))).build())
            .token();
    clock.advance(Duration.ofHours(1));

    assertThat(store.findAndTouch(token)).isEmpty();
    assertThat(redis.hasKey("idn:session:user:" + token.hash())).isFalse();
  }

  @Test
  @DisplayName("不存在的令牌查到空")
  void should_return_empty_for_unknown_token() {
    SessionToken unknown = SessionToken.generate(AccountType.USER, new SecureRandom());

    assertThat(store.findAndTouch(unknown)).isEmpty();
  }

  /// 一个合法会话的建造器，用当前时钟算时间。
  ///
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return 建造器
  NewSession.NewSessionBuilder newSession(long userId, long sessionId) {
    return NewSession.builder()
        .userId(userId)
        .sessionId(sessionId)
        .accountType(AccountType.USER)
        .clientType(ClientType.WEB)
        .createdAt(clock.instant())
        .expiresAt(clock.instant().plus(ABSOLUTE))
        .idleTimeout(IDLE)
        .maxSessionsPerUser(10);
  }

  /// 键的剩余毫秒。
  ///
  /// @param key 键
  /// @return 剩余毫秒
  static long ttl(String key) {
    Long millis = redis.getExpire(key, TimeUnit.MILLISECONDS);
    return millis == null ? -2 : millis;
  }
}

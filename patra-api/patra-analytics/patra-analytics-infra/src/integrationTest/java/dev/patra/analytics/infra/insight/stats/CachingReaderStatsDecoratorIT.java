package dev.patra.analytics.infra.insight.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.analytics.domain.insight.model.vo.ReaderStats;
import dev.patra.analytics.domain.insight.port.stats.ReaderStatsPort;
import dev.patra.analytics.infra.insight.config.InsightProperties;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

class CachingReaderStatsDecoratorIT {

  /// 每个用例用不重复的文献 ID，互不读到别人的缓存。
  private static final AtomicLong NEXT = new AtomicLong(System.nanoTime() % 1_000_000_000L);

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redis;

  private final ReaderStatsPort delegate = mock(ReaderStatsPort.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

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

  @AfterAll
  static void disconnect() {
    connectionFactory.destroy();
  }

  @Test
  void all_hits_skip_delegate() {
    long a = NEXT.incrementAndGet();
    CachingReaderStatsDecorator cache = decorator(false);
    when(delegate.statsOf(List.of(a))).thenReturn(Map.of(a, ReaderStats.of(a, 5, 2)));
    cache.statsOf(List.of(a));

    Map<Long, ReaderStats> second = cache.statsOf(List.of(a));

    assertThat(second).containsEntry(a, ReaderStats.of(a, 5, 2));
    verify(delegate).statsOf(List.of(a));
    assertThat(meters.get("analytics.insight.stats.cache").tag("result", "hit").counter().count())
        .isEqualTo(1);
  }

  @Test
  void only_misses_go_to_delegate() {
    long a = NEXT.incrementAndGet();
    long b = NEXT.incrementAndGet();
    CachingReaderStatsDecorator cache = decorator(false);
    when(delegate.statsOf(List.of(a))).thenReturn(Map.of(a, ReaderStats.of(a, 1, 0)));
    cache.statsOf(List.of(a));
    when(delegate.statsOf(List.of(b))).thenReturn(Map.of(b, ReaderStats.of(b, 2, 1)));

    Map<Long, ReaderStats> stats = cache.statsOf(List.of(a, b));

    assertThat(stats)
        .containsEntry(a, ReaderStats.of(a, 1, 0))
        .containsEntry(b, ReaderStats.of(b, 2, 1));
    verify(delegate).statsOf(List.of(b));
  }

  @Test
  void zero_is_cached_with_a_60_second_ttl() {
    long a = NEXT.incrementAndGet();
    CachingReaderStatsDecorator cache = decorator(false);
    when(delegate.statsOf(List.of(a))).thenReturn(Map.of());

    cache.statsOf(List.of(a));
    Map<Long, ReaderStats> second = cache.statsOf(List.of(a));

    assertThat(second).isEmpty();
    verify(delegate).statsOf(List.of(a));
    Long ttl = redis.getExpire("analytics:insight:stats:real:" + a);
    assertThat(ttl).isBetween(1L, 60L);
  }

  @Test
  void keys_are_split_by_synthetic_flag() {
    long a = NEXT.incrementAndGet();
    when(delegate.statsOf(List.of(a))).thenReturn(Map.of(a, ReaderStats.of(a, 1, 0)));
    decorator(false).statsOf(List.of(a));

    decorator(true).statsOf(List.of(a));

    verify(delegate, times(2)).statsOf(List.of(a));
    assertThat(redis.hasKey("analytics:insight:stats:all:" + a)).isTrue();
  }

  @Test
  void redis_down_falls_back_to_delegate() {
    long a = NEXT.incrementAndGet();
    LettuceConnectionFactory down =
        new LettuceConnectionFactory(
            new RedisStandaloneConfiguration("127.0.0.1", 1),
            LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());
    down.afterPropertiesSet();
    down.start();
    try {
      CachingReaderStatsDecorator cache =
          new CachingReaderStatsDecorator(
              delegate, new StringRedisTemplate(down), InsightProperties.of(false, 8), meters);
      when(delegate.statsOf(any())).thenReturn(Map.of(a, ReaderStats.of(a, 3, 1)));

      assertThat(cache.statsOf(List.of(a))).containsEntry(a, ReaderStats.of(a, 3, 1));
      assertThat(
              meters.get("analytics.insight.stats.cache").tag("result", "error").counter().count())
          .isEqualTo(1);
    } finally {
      down.destroy();
    }
  }

  @Test
  void empty_request_touches_nothing() {
    assertThat(decorator(false).statsOf(List.of())).isEmpty();
    verify(delegate, never()).statsOf(any());
  }

  private CachingReaderStatsDecorator decorator(boolean includeSynthetic) {
    return new CachingReaderStatsDecorator(
        delegate, redis, InsightProperties.of(includeSynthetic, 8), meters);
  }
}

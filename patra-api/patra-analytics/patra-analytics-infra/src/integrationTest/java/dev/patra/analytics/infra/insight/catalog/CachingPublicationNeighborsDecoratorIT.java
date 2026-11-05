package dev.patra.analytics.infra.insight.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.analytics.domain.insight.exception.InsightUnavailableException;
import dev.patra.analytics.domain.insight.model.enums.AlsoReadSource;
import dev.patra.analytics.domain.insight.model.vo.AlsoReadItem;
import dev.patra.analytics.domain.insight.port.catalog.PublicationCatalogPort;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.json.JsonMapper;

class CachingPublicationNeighborsDecoratorIT {

  private static final AtomicLong NEXT = new AtomicLong(System.nanoTime() % 1_000_000_000L);

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redis;

  private final PublicationCatalogPort delegate = mock(PublicationCatalogPort.class);
  private final CachingPublicationNeighborsDecorator cache =
      new CachingPublicationNeighborsDecorator(
          delegate, redis, JsonMapper.builder().build(), new SimpleMeterRegistry());

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
  void second_call_hits_cache() {
    long x = NEXT.incrementAndGet();
    List<AlsoReadItem> neighbors =
        List.of(
            AlsoReadItem.of(1L, AlsoReadSource.SAME_MESH, "Sepsis", null),
            AlsoReadItem.of(2L, AlsoReadSource.SAME_VENUE, null, "Chest"));
    when(delegate.subjectNeighbors(x, 11)).thenReturn(neighbors);

    cache.subjectNeighbors(x, 11);
    List<AlsoReadItem> second = cache.subjectNeighbors(x, 11);

    assertThat(second).isEqualTo(neighbors);
    verify(delegate).subjectNeighbors(x, 11);
    assertThat(redis.getExpire("analytics:insight:neighbors:" + x)).isBetween(5L * 3600, 6L * 3600);
  }

  @Test
  void failure_is_not_cached() {
    long x = NEXT.incrementAndGet();
    when(delegate.subjectNeighbors(x, 11))
        .thenThrow(new InsightUnavailableException("catalog down", null))
        .thenReturn(List.of());

    assertThatThrownBy(() -> cache.subjectNeighbors(x, 11))
        .isInstanceOf(InsightUnavailableException.class);
    cache.subjectNeighbors(x, 11);

    verify(delegate, times(2)).subjectNeighbors(x, 11);
  }

  @Test
  void other_methods_delegate() {
    when(delegate.eligibleAmong(List.of(1L))).thenReturn(Set.of(1L));
    when(delegate.latestIds(28)).thenReturn(List.of(9L));

    assertThat(cache.eligibleAmong(List.of(1L))).containsExactly(1L);
    assertThat(cache.latestIds(28)).containsExactly(9L);
  }
}

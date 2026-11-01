package dev.patra.analytics.infra.collect.rate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

class RedisDeviceRateAdapterIT {

  private static final Instant T0 = Instant.parse("2026-11-01T08:00:00Z");
  private static final AtomicLong NEXT = new AtomicLong(1);

  private static LettuceConnectionFactory connectionFactory;
  private static RedisDeviceRateAdapter adapter;

  @BeforeAll
  static void connect() {
    GenericContainer<?> container = RedisContainerInitializer.getRedisContainer();
    connectionFactory =
        new LettuceConnectionFactory(
            new RedisStandaloneConfiguration(container.getHost(), container.getMappedPort(6379)));
    connectionFactory.afterPropertiesSet();
    connectionFactory.start();
    adapter = new RedisDeviceRateAdapter(new StringRedisTemplate(connectionFactory));
  }

  @AfterAll
  static void disconnect() {
    connectionFactory.destroy();
  }

  @Test
  void count_grows_with_article_views() {
    String device = uniqueDevice();
    OptionalInt last = OptionalInt.empty();
    for (int i = 0; i < 31; i++) {
      last = adapter.observe(device, "e" + i, true, T0.plusSeconds(i));
    }

    assertThat(last).hasValue(31);
  }

  @Test
  void same_event_id_counts_once() {
    String device = uniqueDevice();
    adapter.observe(device, "dup", true, T0);

    assertThat(adapter.observe(device, "dup", true, T0.plusSeconds(1))).hasValue(1);
  }

  @Test
  void uncounted_events_only_read() {
    String device = uniqueDevice();
    for (int i = 0; i < 3; i++) {
      adapter.observe(device, "e" + i, true, T0);
    }

    assertThat(adapter.observe(device, "page", false, T0.plusSeconds(1))).hasValue(3);
    assertThat(adapter.observe(device, "page2", false, T0.plusSeconds(2))).hasValue(3);
  }

  @Test
  void window_slides_after_five_minutes() {
    String device = uniqueDevice();
    adapter.observe(device, "old", true, T0);

    assertThat(adapter.observe(device, "new", true, T0.plus(Duration.ofMinutes(5)).plusSeconds(1)))
        .hasValue(1);
  }

  @Test
  void different_devices_are_independent() {
    String first = uniqueDevice();
    String second = uniqueDevice();
    adapter.observe(first, "a", true, T0);
    adapter.observe(first, "b", true, T0);

    assertThat(adapter.observe(second, "c", true, T0)).hasValue(1);
  }

  private static String uniqueDevice() {
    return "rate-device-" + NEXT.getAndIncrement();
  }
}

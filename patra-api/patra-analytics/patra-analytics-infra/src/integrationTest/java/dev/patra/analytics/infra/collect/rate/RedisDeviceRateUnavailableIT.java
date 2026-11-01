package dev.patra.analytics.infra.collect.rate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisDeviceRateUnavailableIT {

  private static LettuceConnectionFactory connectionFactory;

  @BeforeAll
  static void connect() {
    connectionFactory =
        new LettuceConnectionFactory(
            new RedisStandaloneConfiguration("127.0.0.1", 1),
            LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());
    connectionFactory.afterPropertiesSet();
    connectionFactory.start();
  }

  @AfterAll
  static void disconnect() {
    connectionFactory.destroy();
  }

  @Test
  void observe_returns_empty_when_redis_unreachable() {
    RedisDeviceRateAdapter adapter =
        new RedisDeviceRateAdapter(new StringRedisTemplate(connectionFactory));

    assertThat(adapter.observe("device", "event", true, Instant.parse("2026-11-01T08:00:00Z")))
        .isEmpty();
  }
}

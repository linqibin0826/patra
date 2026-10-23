package dev.patra.tool.test.clock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// MutableClock 单元测试。
@DisplayName("MutableClock 单元测试")
class MutableClockTest {

  private static final Instant START = Instant.parse("2026-10-20T08:00:00Z");

  @Test
  @DisplayName("从指定时刻开始，固定 UTC，换时区返回自己")
  void should_start_at_given_instant_in_utc() {
    MutableClock clock = MutableClock.startingAt(START);

    assertThat(clock.instant()).isEqualTo(START);
    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    assertThat(clock.withZone(ZoneId.of("Asia/Shanghai"))).isSameAs(clock);
  }

  @Test
  @DisplayName("能往前拨，也能直接设到某个时刻")
  void should_advance_and_set() {
    MutableClock clock = MutableClock.startingAt(START);

    clock.advance(Duration.ofDays(30));
    assertThat(clock.instant()).isEqualTo(START.plus(Duration.ofDays(30)));

    clock.set(START);
    assertThat(clock.instant()).isEqualTo(START);
  }
}

package dev.patra.analytics.simulator.live;

import dev.patra.analytics.simulator.support.Sleeper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/// 可拨的时钟：睡眠就是把时钟往前拨（单线程用）。
final class MutableClock extends Clock {

  private Instant now;
  long slept;

  MutableClock(Instant start) {
    this.now = start;
  }

  Sleeper sleeper() {
    return millis -> {
      if (millis > 0) {
        slept += millis;
        now = now.plusMillis(millis);
      }
    };
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    return now;
  }
}

package dev.patra.analytics.simulator.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LiveStatsTest {

  @Test
  void stats_row_per_second_then_reset() {
    LiveStats stats =
        new LiveStats(Clock.fixed(Instant.parse("2026-11-02T02:00:05Z"), ZoneOffset.UTC));
    stats.onlineSessions(() -> 12);
    stats.inFlight(() -> 3);
    stats.request(202, 10_000_000, 5);
    stats.delivered(5);
    stats.request(503, 20_000_000, 2);
    stats.request(400, 1_000_000, 1);
    stats.failed(1);
    StringWriter csv = new StringWriter();

    stats.flushSecond(csv);
    stats.flushSecond(csv);

    String[] lines = csv.toString().split("\n");
    assertThat(lines[0])
        .isEqualTo(
            "second,sent,delivered,failed,status_202,status_503,status_other,p50_ms,p95_ms,p99_ms,online,in_flight");
    assertThat(lines[1]).startsWith("2026-11-02T02:00:05Z,8,5,1,1,1,1,");
    assertThat(lines[1]).endsWith(",12,3");
    assertThat(lines[2]).startsWith("2026-11-02T02:00:05Z,0,0,0,0,0,0,");
    assertThat(stats.totals().delivered()).isEqualTo(5);
    assertThat(stats.totals().requests()).isEqualTo(3);
  }
}

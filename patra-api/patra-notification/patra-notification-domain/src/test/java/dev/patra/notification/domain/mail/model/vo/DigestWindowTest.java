package dev.patra.notification.domain.mail.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/// 每日汇总的窗口：右端是北京时间当天 8:00（spec 5.2）。
class DigestWindowTest {

  private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

  /// 8:00 正点、8:10 补跑都落在当天 8:00；7:59 落在前一天 8:00。
  @Test
  void scheduled_end_is_eight_oclock_beijing() {
    Instant eight = Instant.parse("2026-10-31T00:00:00Z");

    assertThat(DigestWindow.scheduledEnd(eight, BEIJING)).isEqualTo(eight);
    assertThat(DigestWindow.scheduledEnd(eight.plus(Duration.ofMinutes(10)), BEIJING))
        .isEqualTo(eight);
    assertThat(DigestWindow.scheduledEnd(Instant.parse("2026-10-30T23:59:00Z"), BEIJING))
        .isEqualTo(Instant.parse("2026-10-30T00:00:00Z"));
  }

  /// 手动触发截到分钟。
  @Test
  void manual_end_truncates_to_minute() {
    assertThat(DigestWindow.manualEnd(Instant.parse("2026-10-31T06:03:27.5Z")))
        .isEqualTo(Instant.parse("2026-10-31T06:03:00Z"));
  }

  /// 窗口是右端往前 24 小时。
  @Test
  void window_spans_24_hours_before_end() {
    Instant end = Instant.parse("2026-10-31T00:00:00Z");

    DigestWindow window = DigestWindow.endingAt(end);

    assertThat(window.from()).isEqualTo(Instant.parse("2026-10-30T00:00:00Z"));
    assertThat(window.to()).isEqualTo(end);
  }
}

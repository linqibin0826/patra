package dev.patra.notification.domain.mail.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/// 邮件里的时间一律写北京时间的绝对时间（spec 8.4）。
class ReminderTimeTextTest {

  private static final ReminderTimeText TIME = ReminderTimeText.of(ZoneId.of("Asia/Shanghai"));

  /// 三种写法。
  @Test
  void formats_beijing_time() {
    Instant t = Instant.parse("2026-10-30T07:02:00Z");

    assertThat(TIME.full(t)).isEqualTo("10 月 30 日 15:02");
    assertThat(TIME.clock(t)).isEqualTo("15:02");
    assertThat(TIME.date(t)).isEqualTo("10 月 30 日");
  }

  /// 同一天按北京时间判断。
  @Test
  void same_day_uses_beijing_date() {
    assertThat(
            TIME.sameDay(
                Instant.parse("2026-10-30T16:01:00Z"), Instant.parse("2026-10-30T15:59:00Z")))
        .isFalse();
    assertThat(
            TIME.sameDay(
                Instant.parse("2026-10-30T16:01:00Z"), Instant.parse("2026-10-31T15:00:00Z")))
        .isTrue();
  }
}

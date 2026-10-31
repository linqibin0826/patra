package dev.patra.notification.domain.inbox.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/// 有帮助按「北京时间的一天」合并：日期只由点击时刻决定，不读「现在」。
class HelpfulDayTest {

  /// 北京时间午夜（UTC 16:00）前后的两个时刻分属两天。
  @Test
  void should_split_at_beijing_midnight() {
    assertThat(HelpfulDay.of(Instant.parse("2026-10-30T15:59:59.999999Z")))
        .isEqualTo(LocalDate.of(2026, 10, 30));
    assertThat(HelpfulDay.of(Instant.parse("2026-10-30T16:00:00Z")))
        .isEqualTo(LocalDate.of(2026, 10, 31));
  }

  /// 同一个北京日跨了 UTC 的两天，仍是同一天。
  @Test
  void should_keep_one_beijing_day_across_utc_dates() {
    assertThat(HelpfulDay.of(Instant.parse("2026-10-30T16:00:00Z")))
        .isEqualTo(HelpfulDay.of(Instant.parse("2026-10-31T15:59:59Z")));
  }
}

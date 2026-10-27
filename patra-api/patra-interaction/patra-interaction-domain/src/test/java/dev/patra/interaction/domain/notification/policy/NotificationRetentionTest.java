package dev.patra.interaction.domain.notification.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/// 保留期：北京时间减 5 个自然月。
class NotificationRetentionTest {

  /// 普通日子。
  @Test
  void should_subtract_five_calendar_months_in_beijing_time() {
    assertThat(NotificationRetention.cutoff(Instant.parse("2026-10-27T01:00:00Z")))
        .isEqualTo(Instant.parse("2026-05-27T01:00:00Z"));
  }

  /// 月末：7 月 31 日减 5 个月是 2 月 28 日。
  @Test
  void should_clamp_month_end() {
    Instant now =
        LocalDateTime.parse("2026-07-31T10:00").atZone(NotificationRetention.ZONE).toInstant();

    assertThat(NotificationRetention.cutoff(now))
        .isEqualTo(
            LocalDateTime.parse("2026-02-28T10:00").atZone(NotificationRetention.ZONE).toInstant());
  }
}

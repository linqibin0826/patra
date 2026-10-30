package dev.patra.notification.domain.inbox.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/// 信箱保留 5 个月，按北京时间的自然月算。
class InboxRetentionTest {

  /// 北京时间 8 月 1 日零点 → 3 月 1 日零点。
  @Test
  void should_cut_five_beijing_months() {
    assertThat(InboxRetention.cutoff(Instant.parse("2026-07-31T16:00:00Z")))
        .isEqualTo(Instant.parse("2026-02-28T16:00:00Z"));
    assertThat(InboxRetention.cutoff(Instant.parse("2026-10-31T00:00:00Z")))
        .isEqualTo(Instant.parse("2026-05-31T00:00:00Z"));
  }

  /// 月末对齐到目标月的最后一天：北京时间 7 月 31 日 → 2 月 28 日。
  @Test
  void should_align_month_end() {
    assertThat(InboxRetention.cutoff(Instant.parse("2026-07-31T00:00:00Z")))
        .isEqualTo(Instant.parse("2026-02-28T00:00:00Z"));
  }
}

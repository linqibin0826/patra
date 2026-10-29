package dev.patra.catalog.domain.fulltext.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// `FullTextRetrySchedule` 单元测试。
@DisplayName("FullTextRetrySchedule")
class FullTextRetryScheduleTest {

  private static final Instant NOW = Instant.parse("2026-10-20T05:30:00Z");

  private final FullTextRetrySchedule schedule =
      new FullTextRetrySchedule(Clock.fixed(NOW, ZoneOffset.UTC));

  @ParameterizedTest(name = "[{index}] 第 {0} 次失败后 +{1} 小时")
  @CsvSource({"1, 1", "2, 4", "3, 24", "4, 72", "9, 72"})
  @DisplayName("抓取失败按 1 小时、4 小时、1 天，之后每 3 天退避")
  void should_back_off_after_fetch_failure(int attempt, long hours) {
    assertThat(schedule.afterFetchFailure(attempt)).isEqualTo(NOW.plus(Duration.ofHours(hours)));
  }

  @ParameterizedTest(name = "[{index}] 第 {0} 次不可用后 +{1} 天")
  @CsvSource({"1, 1", "2, 3", "3, 7", "4, 30", "5, 90", "12, 90"})
  @DisplayName("暂不可用按 1、3、7、30 天，之后每 90 天再查")
  void should_recheck_unavailable_by_steps(int attempt, long days) {
    assertThat(schedule.afterUnavailable(attempt, null)).isEqualTo(NOW.plus(Duration.ofDays(days)));
  }

  @Test
  @DisplayName("PMC 放开日期在未来时，在那天 00:00 UTC 再查")
  void should_use_release_date_when_in_future() {
    assertThat(schedule.afterUnavailable(4, LocalDate.of(2027, 1, 15)))
        .isEqualTo(Instant.parse("2027-01-15T00:00:00Z"));
  }

  @Test
  @DisplayName("放开日期已过去或就是今天时按次数再查")
  void should_ignore_release_date_in_past() {
    assertThat(schedule.afterUnavailable(1, LocalDate.of(2026, 1, 1)))
        .isEqualTo(NOW.plus(Duration.ofDays(1)));
    assertThat(schedule.afterUnavailable(1, LocalDate.of(2026, 10, 20)))
        .isEqualTo(NOW.plus(Duration.ofDays(1)));
  }
}

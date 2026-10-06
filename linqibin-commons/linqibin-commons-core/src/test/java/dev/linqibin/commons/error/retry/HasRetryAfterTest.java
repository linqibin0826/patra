package dev.linqibin.commons.error.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// HasRetryAfter 单元测试。
@DisplayName("HasRetryAfter 单元测试")
class HasRetryAfterTest {

  @ParameterizedTest
  @CsvSource({"0, 1", "1, 1", "999, 1", "1000, 1", "1001, 2", "900000, 900", "-5, 1"})
  @DisplayName("剩余秒数向上取整，最小为 1")
  void should_round_up_to_whole_seconds_with_minimum_one(long millis, long expected) {
    HasRetryAfter retryAfter = () -> Duration.ofMillis(millis);

    assertThat(retryAfter.getRetryAfterSeconds()).isEqualTo(expected);
  }
}

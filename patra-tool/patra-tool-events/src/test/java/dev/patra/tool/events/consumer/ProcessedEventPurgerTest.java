package dev.patra.tool.events.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.tool.test.clock.MutableClock;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

@DisplayName("已处理事件清理器")
class ProcessedEventPurgerTest {

  private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

  private final JdbcClient jdbcClient = mock(JdbcClient.class);

  private final StatementSpec statement = mock(StatementSpec.class);

  private final ProcessedEventPurger purger =
      new ProcessedEventPurger(jdbcClient, MutableClock.startingAt(NOW), Duration.ofDays(7));

  @Test
  @DisplayName("删处理时间早于「现在减保留期」的行")
  void should_compute_cutoff_from_clock_and_retention() {
    stubDeletes(0);

    purger.purge();

    verify(statement).param(Timestamp.from(NOW.minus(Duration.ofDays(7))));
  }

  @Test
  @DisplayName("一批删不满也接着删，删到某批为 0 才停：并发清理时不会提前收工")
  void should_keep_deleting_until_a_batch_deletes_nothing() {
    stubDeletes(1000, 700, 300, 0);

    assertThat(purger.purge()).isEqualTo(2000);
  }

  /// 模拟每批 DELETE 依次删掉的行数。
  ///
  /// @param first 第一批
  /// @param rest 之后各批
  private void stubDeletes(int first, Integer... rest) {
    when(jdbcClient.sql(anyString())).thenReturn(statement);
    when(statement.param(any())).thenReturn(statement);
    when(statement.update()).thenReturn(first, rest);
  }
}

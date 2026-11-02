package dev.patra.analytics.app.history.usecase.purge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.analytics.domain.history.port.repository.ReadingHistoryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PurgeExpiredHistoryHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-02T06:40:00Z");

  private final ReadingHistoryRepository history = mock(ReadingHistoryRepository.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final PurgeExpiredHistoryHandler handler =
      new PurgeExpiredHistoryHandler(history, Clock.fixed(NOW, ZoneOffset.UTC), meters);

  @Test
  void purges_until_batch_is_short() {
    when(history.purgeReadBefore(any(), anyInt())).thenReturn(10_000, 10_000, 42);
    when(history.purgeDeletionsBefore(any(), anyInt())).thenReturn(10_000, 7);

    PurgeResult result = handler.handle(PurgeExpiredHistoryCommand.of());

    assertThat(result).isEqualTo(PurgeResult.of(20_042, 10_007));
    assertThat(
            meters
                .get("analytics.history.purged")
                .tag("table", "reading_history")
                .counter()
                .count())
        .isEqualTo(20_042.0);
    assertThat(
            meters.get("analytics.history.purged").tag("table", "deleted_entry").counter().count())
        .isEqualTo(10_007.0);
  }

  @Test
  void cutoffs_come_from_clock() {
    when(history.purgeReadBefore(any(), anyInt())).thenReturn(0);
    when(history.purgeDeletionsBefore(any(), anyInt())).thenReturn(0);

    handler.handle(PurgeExpiredHistoryCommand.of());

    verify(history).purgeReadBefore(Instant.parse("2025-11-02T06:40:00Z"), 10_000);
    verify(history).purgeDeletionsBefore(Instant.parse("2026-10-31T06:40:00Z"), 10_000);
  }
}

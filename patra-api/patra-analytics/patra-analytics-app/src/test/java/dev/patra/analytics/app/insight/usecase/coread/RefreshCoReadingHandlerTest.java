package dev.patra.analytics.app.insight.usecase.coread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.analytics.domain.insight.exception.InsightUnavailableException;
import dev.patra.analytics.domain.insight.model.vo.CoReadingEntry;
import dev.patra.analytics.domain.insight.model.vo.CoReadingPair;
import dev.patra.analytics.domain.insight.port.catalog.PublicationCatalogPort;
import dev.patra.analytics.domain.insight.port.repository.CoReadingSnapshotRepository;
import dev.patra.analytics.domain.insight.port.signal.ReadingSignalPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

class RefreshCoReadingHandlerTest {

  /// 北京时间 11 月 3 日 04:30。
  private static final Instant NOW = Instant.parse("2026-11-02T20:30:00Z");

  private final ReadingSignalPort signals = mock(ReadingSignalPort.class);
  private final PublicationCatalogPort catalog = mock(PublicationCatalogPort.class);
  private final CoReadingSnapshotRepository snapshots = mock(CoReadingSnapshotRepository.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final RefreshCoReadingHandler handler =
      new RefreshCoReadingHandler(
          signals,
          catalog,
          snapshots,
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC),
          meters);

  @Test
  void checks_all_ids_once_and_replaces() {
    when(signals.coReadingPairs(any(), anyInt()))
        .thenReturn(
            List.of(
                CoReadingPair.of(1L, 2L, 0.9, 3),
                CoReadingPair.of(1L, 3L, 0.5, 3),
                CoReadingPair.of(2L, 1L, 0.9, 3)));
    when(catalog.eligibleAmong(any())).thenReturn(Set.of(1L, 2L));

    RefreshCoReadingResult result = handler.handle(RefreshCoReadingCommand.of());

    verify(signals).coReadingPairs(LocalDate.of(2026, 11, 3), 25);
    ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.captor();
    verify(catalog).eligibleAmong(ids.capture());
    assertThat(ids.getValue()).containsExactlyInAnyOrder(1L, 2L, 3L);
    ArgumentCaptor<List<CoReadingEntry>> entries = ArgumentCaptor.captor();
    verify(snapshots).replace(entries.capture(), eq(NOW));
    assertThat(entries.getValue())
        .extracting(CoReadingEntry::articleId, CoReadingEntry::relatedArticleId)
        .containsExactly(tuple(1L, 2L), tuple(2L, 1L));
    assertThat(result).isEqualTo(RefreshCoReadingResult.of(2, 2));
    assertThat(
            meters.get("analytics.insight.snapshot.rows").tag("kind", "co_reading").gauge().value())
        .isEqualTo(2.0);
  }

  @Test
  void failure_does_not_touch_snapshot() {
    when(signals.coReadingPairs(any(), anyInt()))
        .thenReturn(List.of(CoReadingPair.of(1L, 2L, 1, 3)));
    when(catalog.eligibleAmong(any())).thenThrow(new InsightUnavailableException("down", null));

    assertThatThrownBy(() -> handler.handle(RefreshCoReadingCommand.of()))
        .isInstanceOf(InsightUnavailableException.class);
    verify(snapshots, never()).replace(any(), any());
  }
}

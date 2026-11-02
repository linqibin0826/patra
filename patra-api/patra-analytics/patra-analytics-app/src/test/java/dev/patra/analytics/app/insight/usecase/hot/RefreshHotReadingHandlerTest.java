package dev.patra.analytics.app.insight.usecase.hot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.analytics.domain.insight.exception.InsightUnavailableException;
import dev.patra.analytics.domain.insight.model.vo.HotReadingCandidate;
import dev.patra.analytics.domain.insight.model.vo.HotReadingEntry;
import dev.patra.analytics.domain.insight.port.catalog.PublicationCatalogPort;
import dev.patra.analytics.domain.insight.port.repository.HotReadingSnapshotRepository;
import dev.patra.analytics.domain.insight.port.signal.ReadingSignalPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

class RefreshHotReadingHandlerTest {

  /// 北京时间 11 月 2 日 01:00。
  private static final Instant NOW = Instant.parse("2026-11-01T17:00:00Z");

  private final ReadingSignalPort signals = mock(ReadingSignalPort.class);
  private final PublicationCatalogPort catalog = mock(PublicationCatalogPort.class);
  private final HotReadingSnapshotRepository snapshots = mock(HotReadingSnapshotRepository.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final RefreshHotReadingHandler handler =
      new RefreshHotReadingHandler(
          signals,
          catalog,
          snapshots,
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC),
          meters);

  @Test
  void passes_beijing_today() {
    when(signals.hotCandidates(any(), anyInt())).thenReturn(List.of());
    when(catalog.eligibleAmong(any())).thenReturn(Set.of());

    handler.handle(RefreshHotReadingCommand.of());

    verify(signals).hotCandidates(LocalDate.of(2026, 11, 2), 150);
  }

  @Test
  void filters_ineligible_keeps_order_and_backfills() {
    when(signals.hotCandidates(any(), anyInt()))
        .thenReturn(
            List.of(
                HotReadingCandidate.of(3L, 9.0, 5),
                HotReadingCandidate.of(1L, 8.0, 4),
                HotReadingCandidate.of(2L, 7.0, 3)));
    when(catalog.eligibleAmong(List.of(3L, 1L, 2L))).thenReturn(Set.of(3L, 2L));
    when(catalog.latestIds(28)).thenReturn(List.of(2L, 50L, 51L));

    RefreshHotReadingResult result = handler.handle(RefreshHotReadingCommand.of());

    assertThat(result).isEqualTo(RefreshHotReadingResult.of(2, 2));
    assertThat(replaced()).extracting(HotReadingEntry::articleId).containsExactly(3L, 2L, 50L, 51L);
    verify(snapshots).replace(any(), eq(NOW));
    assertThat(
            meters
                .get("analytics.insight.snapshot.rows")
                .tag("kind", "hot_reading")
                .gauge()
                .value())
        .isEqualTo(4.0);
    assertThat(
            meters
                .get("analytics.insight.job")
                .tag("job", "hot_reading")
                .tag("outcome", "success")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void skips_latest_when_ranked_fills_page() {
    List<HotReadingCandidate> candidates =
        LongStream.rangeClosed(1, 14)
            .mapToObj(id -> HotReadingCandidate.of(id, 20.0 - id, 3))
            .toList();
    when(signals.hotCandidates(any(), anyInt())).thenReturn(candidates);
    when(catalog.eligibleAmong(any()))
        .thenReturn(Set.copyOf(LongStream.rangeClosed(1, 14).boxed().toList()));

    handler.handle(RefreshHotReadingCommand.of());

    verify(catalog, never()).latestIds(anyInt());
  }

  @Test
  void hot_job_keeps_previous_snapshot_when_catalog_fails() {
    when(signals.hotCandidates(any(), anyInt()))
        .thenReturn(List.of(HotReadingCandidate.of(1L, 1, 3)));
    when(catalog.eligibleAmong(any())).thenThrow(new InsightUnavailableException("down", null));

    assertThatThrownBy(() -> handler.handle(RefreshHotReadingCommand.of()))
        .isInstanceOf(InsightUnavailableException.class);
    verify(snapshots, never()).replace(any(), any());
    assertThat(
            meters
                .get("analytics.insight.job")
                .tag("job", "hot_reading")
                .tag("outcome", "failure")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void clickhouse_failure_does_not_touch_snapshot() {
    when(signals.hotCandidates(any(), anyInt()))
        .thenThrow(new InsightUnavailableException("down", null));

    assertThatThrownBy(() -> handler.handle(RefreshHotReadingCommand.of()))
        .isInstanceOf(InsightUnavailableException.class);
    verify(snapshots, never()).replace(any(), any());
  }

  private List<HotReadingEntry> replaced() {
    ArgumentCaptor<List<HotReadingEntry>> captor = ArgumentCaptor.captor();
    verify(snapshots).replace(captor.capture(), any());
    return captor.getValue();
  }
}

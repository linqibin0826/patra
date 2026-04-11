package com.patra.catalog.app.usecase.venue.letpub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.venue.VenueEnrichRunStats;
import com.patra.catalog.app.usecase.venue.letpub.LetPubEnrichmentWorker.Outcome;
import com.patra.catalog.domain.port.enrichment.VenueSnapshot;
import com.patra.catalog.domain.port.read.VenueEnrichmentReadPort;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("LetPubEnrichmentRunner 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@ExtendWith(MockitoExtension.class)
class LetPubEnrichmentRunnerTest {

  @Mock VenueEnrichmentReadPort readPort;
  @Mock LetPubEnrichmentWorker worker;
  LetPubEnrichmentRunner runner;

  @BeforeEach
  void setUp() {
    runner = new LetPubEnrichmentRunner(readPort, worker);
  }

  @Test
  @DisplayName("空结果立即退出 - 返回全 0 统计")
  void run_emptyResultsFromStart_returnsZeroStats() {
    when(readPort.findNeedingLetPubEnrichment(anyShort(), anyInt(), anyLong(), anyInt()))
        .thenReturn(List.of());

    VenueEnrichRunStats stats = runner.run((short) 2025, 0);

    assertThat(stats.totalRead()).isZero();
    assertThat(stats.processed()).isZero();
    assertThat(stats.skipped()).isZero();
    assertThat(stats.failed()).isZero();
  }

  @Test
  @DisplayName("单批全部 PROCESSED - 一页耗尽立即终止")
  void run_singleBatch_processesAll() {
    List<VenueSnapshot> batch =
        List.of(
            VenueSnapshot.of(1L, "A", null),
            VenueSnapshot.of(2L, "B", null),
            VenueSnapshot.of(3L, "C", null));
    when(readPort.findNeedingLetPubEnrichment((short) 2025, 0, 0L, 50)).thenReturn(batch);
    when(readPort.findNeedingLetPubEnrichment((short) 2025, 0, 3L, 50)).thenReturn(List.of());
    when(worker.processVenue(any())).thenReturn(Outcome.PROCESSED);

    VenueEnrichRunStats stats = runner.run((short) 2025, 0);

    assertThat(stats.totalRead()).isEqualTo(3);
    assertThat(stats.processed()).isEqualTo(3);
    assertThat(stats.failed()).isZero();
    verify(worker, times(3)).processVenue(any());
  }

  @Test
  @DisplayName("单条失败不中断 - 其余 venue 继续处理")
  void run_workerThrowsOnOneVenue_othersContinue() {
    List<VenueSnapshot> batch =
        List.of(
            VenueSnapshot.of(1L, "A", null),
            VenueSnapshot.of(2L, "B", null),
            VenueSnapshot.of(3L, "C", null));
    when(readPort.findNeedingLetPubEnrichment((short) 2025, 0, 0L, 50)).thenReturn(batch);
    when(readPort.findNeedingLetPubEnrichment((short) 2025, 0, 3L, 50)).thenReturn(List.of());
    when(worker.processVenue(VenueSnapshot.of(1L, "A", null))).thenReturn(Outcome.PROCESSED);
    when(worker.processVenue(VenueSnapshot.of(2L, "B", null)))
        .thenThrow(new RuntimeException("crash"));
    when(worker.processVenue(VenueSnapshot.of(3L, "C", null))).thenReturn(Outcome.PROCESSED);

    VenueEnrichRunStats stats = runner.run((short) 2025, 0);

    assertThat(stats.totalRead()).isEqualTo(3);
    assertThat(stats.processed()).isEqualTo(2);
    assertThat(stats.failed()).isEqualTo(1);
    assertThat(stats.skipped()).isZero();
  }

  @Test
  @DisplayName("Outcome 区分 - MISSING_ISSN / NOT_FOUND_IN_SOURCE 均计入 skipped")
  void run_skipsCountedByOutcome() {
    when(readPort.findNeedingLetPubEnrichment((short) 2025, 0, 0L, 50))
        .thenReturn(
            List.of(
                VenueSnapshot.of(1L, null, null),
                VenueSnapshot.of(2L, "B", null),
                VenueSnapshot.of(3L, "C", null)));
    when(readPort.findNeedingLetPubEnrichment((short) 2025, 0, 3L, 50)).thenReturn(List.of());
    when(worker.processVenue(VenueSnapshot.of(1L, null, null))).thenReturn(Outcome.MISSING_ISSN);
    when(worker.processVenue(VenueSnapshot.of(2L, "B", null)))
        .thenReturn(Outcome.NOT_FOUND_IN_SOURCE);
    when(worker.processVenue(VenueSnapshot.of(3L, "C", null))).thenReturn(Outcome.PROCESSED);

    VenueEnrichRunStats stats = runner.run((short) 2025, 0);

    assertThat(stats.skipped()).isEqualTo(2);
    assertThat(stats.processed()).isEqualTo(1);
    assertThat(stats.failed()).isZero();
  }

  @Test
  @DisplayName("Keyset 游标前进 - 跨页读取使用上页末尾 id")
  void run_keysetAdvancesAcrossPages() {
    when(readPort.findNeedingLetPubEnrichment((short) 2025, 0, 0L, 50))
        .thenReturn(List.of(VenueSnapshot.of(10L, "A", null), VenueSnapshot.of(20L, "B", null)));
    when(readPort.findNeedingLetPubEnrichment((short) 2025, 0, 20L, 50))
        .thenReturn(List.of(VenueSnapshot.of(30L, "C", null)));
    when(readPort.findNeedingLetPubEnrichment((short) 2025, 0, 30L, 50)).thenReturn(List.of());
    when(worker.processVenue(any())).thenReturn(Outcome.PROCESSED);

    VenueEnrichRunStats stats = runner.run((short) 2025, 0);

    assertThat(stats.totalRead()).isEqualTo(3);
    verify(readPort).findNeedingLetPubEnrichment((short) 2025, 0, 0L, 50);
    verify(readPort).findNeedingLetPubEnrichment((short) 2025, 0, 20L, 50);
    verify(readPort).findNeedingLetPubEnrichment((short) 2025, 0, 30L, 50);
  }
}

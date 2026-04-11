package com.patra.catalog.app.usecase.venue.scopus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.venue.VenueEnrichRunStats;
import com.patra.catalog.app.usecase.venue.scopus.ScopusEnrichmentWorker.Outcome;
import com.patra.catalog.domain.port.enrichment.VenueSnapshot;
import com.patra.catalog.domain.port.read.VenueEnrichmentReadPort;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("ScopusEnrichmentRunner 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@ExtendWith(MockitoExtension.class)
class ScopusEnrichmentRunnerTest {

  @Mock VenueEnrichmentReadPort readPort;
  @Mock ScopusEnrichmentWorker worker;

  ScopusEnrichmentRunner runner;
  AtomicInteger sleepCallCount;

  @BeforeEach
  void setUp() {
    sleepCallCount = new AtomicInteger();
    // 匿名子类重写 doRateLimitSleep：跳过真实 sleep 只计数。
    // 这是 template method 测试 override 的标准 Java 模式，与其他 Runner 测试
    // 手动 new Runner(readPort, worker) 的约定一致。
    runner =
        new ScopusEnrichmentRunner(readPort, worker) {
          @Override
          protected void doRateLimitSleep() {
            sleepCallCount.incrementAndGet();
          }
        };
  }

  @Test
  @DisplayName("空结果立即退出 - 返回全 0 统计 + 不 sleep")
  void run_emptyResultsFromStart_returnsZeroStats() {
    when(readPort.findNeedingScopusEnrichment(anyShort(), anyInt(), anyLong(), anyInt()))
        .thenReturn(List.of());

    VenueEnrichRunStats stats = runner.run((short) 2025, 0);

    assertThat(stats.totalRead()).isZero();
    assertThat(stats.processed()).isZero();
    assertThat(stats.skipped()).isZero();
    assertThat(stats.failed()).isZero();
    assertThat(sleepCallCount.get()).isZero();
  }

  @Test
  @DisplayName("单批全部 PROCESSED - 一页耗尽立即终止")
  void run_singleBatch_processesAll() {
    List<VenueSnapshot> batch =
        List.of(VenueSnapshot.of(1L, "A"), VenueSnapshot.of(2L, "B"), VenueSnapshot.of(3L, "C"));
    when(readPort.findNeedingScopusEnrichment((short) 2025, 0, 0L, 50)).thenReturn(batch);
    when(readPort.findNeedingScopusEnrichment((short) 2025, 0, 3L, 50)).thenReturn(List.of());
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
        List.of(VenueSnapshot.of(1L, "A"), VenueSnapshot.of(2L, "B"), VenueSnapshot.of(3L, "C"));
    when(readPort.findNeedingScopusEnrichment((short) 2025, 0, 0L, 50)).thenReturn(batch);
    when(readPort.findNeedingScopusEnrichment((short) 2025, 0, 3L, 50)).thenReturn(List.of());
    when(worker.processVenue(VenueSnapshot.of(1L, "A"))).thenReturn(Outcome.PROCESSED);
    when(worker.processVenue(VenueSnapshot.of(2L, "B"))).thenThrow(new RuntimeException("crash"));
    when(worker.processVenue(VenueSnapshot.of(3L, "C"))).thenReturn(Outcome.PROCESSED);

    VenueEnrichRunStats stats = runner.run((short) 2025, 0);

    assertThat(stats.totalRead()).isEqualTo(3);
    assertThat(stats.processed()).isEqualTo(2);
    assertThat(stats.failed()).isEqualTo(1);
    assertThat(stats.skipped()).isZero();
  }

  @Test
  @DisplayName("Outcome 区分 - MISSING_ISSN / NOT_FOUND_IN_SOURCE 均计入 skipped")
  void run_skipsCountedByOutcome() {
    when(readPort.findNeedingScopusEnrichment((short) 2025, 0, 0L, 50))
        .thenReturn(
            List.of(
                VenueSnapshot.of(1L, null), VenueSnapshot.of(2L, "B"), VenueSnapshot.of(3L, "C")));
    when(readPort.findNeedingScopusEnrichment((short) 2025, 0, 3L, 50)).thenReturn(List.of());
    when(worker.processVenue(VenueSnapshot.of(1L, null))).thenReturn(Outcome.MISSING_ISSN);
    when(worker.processVenue(VenueSnapshot.of(2L, "B"))).thenReturn(Outcome.NOT_FOUND_IN_SOURCE);
    when(worker.processVenue(VenueSnapshot.of(3L, "C"))).thenReturn(Outcome.PROCESSED);

    VenueEnrichRunStats stats = runner.run((short) 2025, 0);

    assertThat(stats.skipped()).isEqualTo(2);
    assertThat(stats.processed()).isEqualTo(1);
    assertThat(stats.failed()).isZero();
  }

  @Test
  @DisplayName("Keyset 游标前进 - 跨页读取使用上页末尾 id")
  void run_keysetAdvancesAcrossPages() {
    when(readPort.findNeedingScopusEnrichment((short) 2025, 0, 0L, 50))
        .thenReturn(List.of(VenueSnapshot.of(10L, "A"), VenueSnapshot.of(20L, "B")));
    when(readPort.findNeedingScopusEnrichment((short) 2025, 0, 20L, 50))
        .thenReturn(List.of(VenueSnapshot.of(30L, "C")));
    when(readPort.findNeedingScopusEnrichment((short) 2025, 0, 30L, 50)).thenReturn(List.of());
    when(worker.processVenue(any())).thenReturn(Outcome.PROCESSED);

    VenueEnrichRunStats stats = runner.run((short) 2025, 0);

    assertThat(stats.totalRead()).isEqualTo(3);
    verify(readPort).findNeedingScopusEnrichment((short) 2025, 0, 0L, 50);
    verify(readPort).findNeedingScopusEnrichment((short) 2025, 0, 20L, 50);
    verify(readPort).findNeedingScopusEnrichment((short) 2025, 0, 30L, 50);
  }

  @Test
  @DisplayName("限速 - 3 个 venue 之间 sleep 被调用 2 次（首条不等）")
  void run_rateLimit_sleepsBetweenVenues() {
    List<VenueSnapshot> batch =
        List.of(VenueSnapshot.of(1L, "A"), VenueSnapshot.of(2L, "B"), VenueSnapshot.of(3L, "C"));
    when(readPort.findNeedingScopusEnrichment((short) 2025, 0, 0L, 50)).thenReturn(batch);
    when(readPort.findNeedingScopusEnrichment((short) 2025, 0, 3L, 50)).thenReturn(List.of());
    when(worker.processVenue(any())).thenReturn(Outcome.PROCESSED);

    runner.run((short) 2025, 0);

    // 3 个 venue，首条不等 → 第 2 条前 + 第 3 条前 = 2 次
    assertThat(sleepCallCount.get()).isEqualTo(2);
  }
}

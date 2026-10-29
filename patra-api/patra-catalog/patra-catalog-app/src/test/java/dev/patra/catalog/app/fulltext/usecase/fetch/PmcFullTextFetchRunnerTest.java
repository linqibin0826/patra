package dev.patra.catalog.app.fulltext.usecase.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.app.fulltext.config.FullTextJobProperties;
import dev.patra.catalog.domain.fulltext.model.read.FetchCandidate;
import dev.patra.catalog.domain.fulltext.port.read.FullTextCandidateReadPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/// `PmcFullTextFetchRunner` 单元测试。
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PmcFullTextFetchRunner")
class PmcFullTextFetchRunnerTest {

  private static final Instant NOW = Instant.parse("2026-10-20T05:00:00Z");

  @Mock private FullTextCandidateReadPort candidatePort;
  @Mock private PmcFullTextFetchWorker worker;

  private PmcFullTextFetchRunner runner;

  @BeforeEach
  void setUp() {
    FullTextJobProperties properties =
        new FullTextJobProperties(
            new FullTextJobProperties.Fetch(2, 2, 8), new FullTextJobProperties.Parse(500));
    runner =
        new PmcFullTextFetchRunner(
            candidatePort, worker, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    when(candidatePort.findDueFetchCandidates(any(), anyInt(), anyInt())).thenReturn(List.of());
    when(candidatePort.findPmcidChangedCandidates(any(), anyLong(), anyInt()))
        .thenReturn(List.of());
    when(candidatePort.findNewFetchCandidates(anyLong(), anyInt())).thenReturn(List.of());
    when(worker.process(any())).thenReturn(FetchOutcome.FETCHED);
  }

  @Test
  @DisplayName("新文献按最后一个 publication_id 翻页，直到取不到候选")
  void should_page_new_candidates_by_last_publication_id() {
    when(candidatePort.findNewFetchCandidates(0L, 2))
        .thenReturn(List.of(candidate(1), candidate(2)));
    when(candidatePort.findNewFetchCandidates(2L, 2)).thenReturn(List.of(candidate(3)));

    assertThat(runner.run(List.of())).isEqualTo(FullTextFetchRunStats.of(3, 3, 0, 0));
  }

  @Test
  @DisplayName("到期候选反复取到处理完为止；同一篇在一次运行里只处理一次，防止持久化出错时死循环")
  void should_drain_due_candidates_and_stop_on_repeats() {
    when(candidatePort.findDueFetchCandidates(NOW, 8, 2))
        .thenReturn(List.of(candidate(5), candidate(6)))
        .thenReturn(List.of(candidate(6), candidate(7)))
        .thenReturn(List.of(candidate(7)));

    FullTextFetchRunStats stats = runner.run(List.of());

    assertThat(stats.processed()).isEqualTo(3);
    verify(worker, times(1)).process(candidate(6));
  }

  @Test
  @DisplayName("PMCID 变了的按 keyset 处理，与到期候选重复的跳过")
  void should_process_pmcid_changed_once() {
    when(candidatePort.findDueFetchCandidates(NOW, 8, 2))
        .thenReturn(List.of(candidate(9)))
        .thenReturn(List.of());
    when(candidatePort.findPmcidChangedCandidates(NOW, 0L, 2))
        .thenReturn(List.of(candidate(9), candidate(10)));

    assertThat(runner.run(List.of()).processed()).isEqualTo(2);
  }

  @Test
  @DisplayName("单篇异常计失败，不中断同批其他篇")
  void should_count_failed_and_continue() {
    when(candidatePort.findNewFetchCandidates(0L, 2))
        .thenReturn(List.of(candidate(1), candidate(2)));
    when(worker.process(candidate(1))).thenThrow(new IllegalStateException("boom"));
    when(worker.process(candidate(2))).thenReturn(FetchOutcome.UNAVAILABLE);

    assertThat(runner.run(List.of())).isEqualTo(FullTextFetchRunStats.of(2, 0, 1, 1));
  }

  @Test
  @DisplayName("给了 publicationIds 时只处理这些，忽略状态")
  void should_use_given_ids_for_manual_refetch() {
    when(candidatePort.findByPublicationIds(List.of(5L))).thenReturn(List.of(candidate(5)));

    assertThat(runner.run(List.of(5L))).isEqualTo(FullTextFetchRunStats.of(1, 1, 0, 0));
    verify(candidatePort, never()).findNewFetchCandidates(anyLong(), eq(2));
  }

  private static FetchCandidate candidate(long id) {
    return FetchCandidate.builder().publicationId(id).pmcid("PMC" + id).build();
  }
}

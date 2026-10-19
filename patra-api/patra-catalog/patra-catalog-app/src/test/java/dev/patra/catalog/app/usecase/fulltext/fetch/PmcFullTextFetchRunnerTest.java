package dev.patra.catalog.app.usecase.fulltext.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.app.usecase.fulltext.FullTextJobProperties;
import dev.patra.catalog.domain.model.read.fulltext.FetchCandidate;
import dev.patra.catalog.domain.port.read.FullTextCandidateReadPort;
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

/// `PmcFullTextFetchRunner` 单元测试。
@ExtendWith(MockitoExtension.class)
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
  }

  @Test
  @DisplayName("按最后一个 publication_id 翻页，直到取不到候选")
  void should_page_by_last_publication_id() {
    FetchCandidate c1 = candidate(1);
    FetchCandidate c2 = candidate(2);
    FetchCandidate c3 = candidate(3);
    when(candidatePort.findFetchCandidates(NOW, 8, 0L, 2)).thenReturn(List.of(c1, c2));
    when(candidatePort.findFetchCandidates(NOW, 8, 2L, 2)).thenReturn(List.of(c3));
    when(candidatePort.findFetchCandidates(NOW, 8, 3L, 2)).thenReturn(List.of());
    when(worker.process(any())).thenReturn(FetchOutcome.FETCHED);

    FullTextFetchRunStats stats = runner.run(List.of());

    assertThat(stats).isEqualTo(FullTextFetchRunStats.of(3, 3, 0, 0));
  }

  @Test
  @DisplayName("单篇异常计失败，不中断同批其他篇")
  void should_count_failed_and_continue() {
    FetchCandidate c1 = candidate(1);
    FetchCandidate c2 = candidate(2);
    when(candidatePort.findFetchCandidates(NOW, 8, 0L, 2)).thenReturn(List.of(c1, c2));
    when(candidatePort.findFetchCandidates(NOW, 8, 2L, 2)).thenReturn(List.of());
    when(worker.process(c1)).thenThrow(new IllegalStateException("boom"));
    when(worker.process(c2)).thenReturn(FetchOutcome.UNAVAILABLE);

    FullTextFetchRunStats stats = runner.run(List.of());

    assertThat(stats).isEqualTo(FullTextFetchRunStats.of(2, 0, 1, 1));
  }

  @Test
  @DisplayName("给了 publicationIds 时只处理这些，忽略状态")
  void should_use_given_ids_for_manual_refetch() {
    FetchCandidate c5 = candidate(5);
    when(candidatePort.findByPublicationIds(List.of(5L))).thenReturn(List.of(c5));
    when(worker.process(c5)).thenReturn(FetchOutcome.FETCHED);

    FullTextFetchRunStats stats = runner.run(List.of(5L));

    assertThat(stats).isEqualTo(FullTextFetchRunStats.of(1, 1, 0, 0));
    verify(candidatePort, never()).findFetchCandidates(any(), eq(8), eq(0L), eq(2));
  }

  private static FetchCandidate candidate(long id) {
    return FetchCandidate.builder().publicationId(id).pmcid("PMC" + id).build();
  }
}

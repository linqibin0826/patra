package dev.patra.analytics.infra.insight.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.domain.insight.model.vo.HotReadingEntry;
import dev.patra.analytics.domain.insight.model.vo.HotReadingSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class HotReadingSnapshotIT extends InsightPersistenceITSupport {

  private static final Instant FIRST = Instant.parse("2026-11-02T07:05:00Z");
  private static final Instant SECOND = Instant.parse("2026-11-02T08:05:00Z");

  @Autowired private HotReadingSnapshotRepositoryAdapter snapshots;
  @Autowired private HotReadingReadAdapter reads;

  @Test
  void summary_is_empty_before_first_run() {
    assertThat(reads.summary(14)).isEqualTo(HotReadingSummary.empty());
  }

  @Test
  void replace_swaps_whole_table() {
    replace(List.of(ranked(1, 11L), ranked(2, 12L), ranked(3, 13L)), FIRST);
    replace(List.of(ranked(1, 21L), backfill(2, 22L)), SECOND);

    HotReadingSummary summary = reads.summary(14);

    assertThat(summary.computedAt()).isEqualTo(SECOND);
    assertThat(summary.rankedTotal()).isEqualTo(1);
    assertThat(summary.items()).containsExactly(ranked(1, 21L), backfill(2, 22L));
  }

  @Test
  void summary_limits_items_but_counts_all_ranked() {
    List<HotReadingEntry> board = new ArrayList<>();
    for (int i = 1; i <= 100; i++) {
      board.add(ranked(i, 1000L + i));
    }
    replace(board, FIRST);

    HotReadingSummary summary = reads.summary(14);

    assertThat(summary.rankedTotal()).isEqualTo(100);
    assertThat(summary.items()).hasSize(14);
    assertThat(summary.items().getLast().articleId()).isEqualTo(1014L);
  }

  @Test
  void ranked_among_ignores_backfill() {
    replace(List.of(ranked(1, 1L), ranked(2, 2L), backfill(3, 3L)), FIRST);

    assertThat(reads.rankedAmong(List.of(1L, 3L, 4L))).containsExactly(1L);
    assertThat(reads.rankedAmong(List.of())).isEmpty();
  }

  @Test
  void reader_sees_old_snapshot_until_replace_commits() throws Exception {
    replace(List.of(ranked(1, 11L)), FIRST);
    CountDownLatch replaced = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    CompletableFuture<Void> writer =
        CompletableFuture.runAsync(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      snapshots.replace(List.of(ranked(1, 21L)), SECOND);
                      replaced.countDown();
                      await(release);
                    }));
    assertThat(replaced.await(10, TimeUnit.SECONDS)).isTrue();

    HotReadingSummary during = reads.summary(14);
    release.countDown();
    writer.get(10, TimeUnit.SECONDS);

    assertThat(during.items()).extracting(HotReadingEntry::articleId).containsExactly(11L);
    assertThat(during.computedAt()).isEqualTo(FIRST);
    assertThat(reads.summary(14).items())
        .extracting(HotReadingEntry::articleId)
        .containsExactly(21L);
  }

  private void replace(List<HotReadingEntry> entries, Instant at) {
    transactions.executeWithoutResult(status -> snapshots.replace(entries, at));
  }

  private static HotReadingEntry ranked(int position, long articleId) {
    return HotReadingEntry.builder()
        .position(position)
        .articleId(articleId)
        .ranked(true)
        .score(10.0 - position)
        .readers7d(3)
        .build();
  }

  private static HotReadingEntry backfill(int position, long articleId) {
    return HotReadingEntry.builder().position(position).articleId(articleId).ranked(false).build();
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

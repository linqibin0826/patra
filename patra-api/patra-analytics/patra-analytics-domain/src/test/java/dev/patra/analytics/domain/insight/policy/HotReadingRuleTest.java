package dev.patra.analytics.domain.insight.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.domain.insight.model.vo.HotReadingCandidate;
import dev.patra.analytics.domain.insight.model.vo.HotReadingEntry;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class HotReadingRuleTest {

  @Test
  void board_without_backfill_when_ranked_fills_page() {
    List<HotReadingEntry> board = HotReadingRule.board(candidates(1, 20), List.of(900L, 901L));

    assertThat(board).hasSize(20).allMatch(HotReadingEntry::ranked);
    assertThat(board).extracting(HotReadingEntry::position).containsExactlyElementsOf(range(1, 20));
    assertThat(board)
        .extracting(HotReadingEntry::articleId)
        .containsExactlyElementsOf(LongStream.rangeClosed(1, 20).boxed().toList());
    assertThat(board.getFirst().score()).isEqualTo(1000.0);
    assertThat(board.getFirst().readers7d()).isEqualTo(3);
  }

  @Test
  void board_caps_at_100_ranked() {
    assertThat(HotReadingRule.board(candidates(1, 150), List.of())).hasSize(100);
  }

  @Test
  void board_backfills_to_page_size_skipping_ranked() {
    List<Long> latest = List.of(9L, 6L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L);

    List<HotReadingEntry> board = HotReadingRule.board(candidates(5, 8), latest);

    assertThat(board).hasSize(HotReadingRule.PAGE_SIZE);
    assertThat(board.subList(0, 4)).allMatch(HotReadingEntry::ranked);
    List<HotReadingEntry> backfill = board.subList(4, 14);
    assertThat(backfill).noneMatch(HotReadingEntry::ranked);
    assertThat(backfill).allSatisfy(e -> assertThat(e.score()).isNull());
    assertThat(backfill).allSatisfy(e -> assertThat(e.readers7d()).isNull());
    assertThat(backfill)
        .extracting(HotReadingEntry::articleId)
        .containsExactly(9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L);
    assertThat(board).extracting(HotReadingEntry::position).containsExactlyElementsOf(range(1, 14));
  }

  @Test
  void board_backfill_takes_what_is_available() {
    List<HotReadingEntry> board = HotReadingRule.board(candidates(1, 2), List.of(7L, 8L, 9L));

    assertThat(board).extracting(HotReadingEntry::articleId).containsExactly(1L, 2L, 7L, 8L, 9L);
  }

  /// 文献 ID 从 from 到 to 的候选，分数递减。
  private static List<HotReadingCandidate> candidates(long from, long to) {
    return LongStream.rangeClosed(from, to)
        .mapToObj(id -> HotReadingCandidate.of(id, 1000.0 - (id - from), 3))
        .toList();
  }

  private static List<Integer> range(int from, int to) {
    return LongStream.rangeClosed(from, to).mapToObj(i -> (int) i).toList();
  }
}

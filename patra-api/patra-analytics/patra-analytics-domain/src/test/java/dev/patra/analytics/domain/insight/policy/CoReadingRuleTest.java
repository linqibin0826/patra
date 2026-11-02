package dev.patra.analytics.domain.insight.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import dev.patra.analytics.domain.insight.model.vo.CoReadingEntry;
import dev.patra.analytics.domain.insight.model.vo.CoReadingPair;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class CoReadingRuleTest {

  @Test
  void keep_drops_ineligible_and_renumbers() {
    List<CoReadingPair> candidates =
        List.of(
            CoReadingPair.of(1L, 10L, 0.9, 5),
            CoReadingPair.of(1L, 20L, 0.8, 4),
            CoReadingPair.of(1L, 30L, 0.7, 3),
            CoReadingPair.of(2L, 10L, 0.6, 3));

    List<CoReadingEntry> kept = CoReadingRule.keep(candidates, Set.of(1L, 10L, 30L));

    assertThat(kept)
        .extracting(
            CoReadingEntry::articleId,
            CoReadingEntry::position,
            CoReadingEntry::relatedArticleId,
            CoReadingEntry::similarity,
            CoReadingEntry::coReaders)
        .containsExactly(tuple(1L, 1, 10L, 0.9, 5), tuple(1L, 2, 30L, 0.7, 3));
  }

  @Test
  void keep_caps_at_20() {
    List<CoReadingPair> candidates =
        LongStream.rangeClosed(1, 25)
            .mapToObj(y -> CoReadingPair.of(1L, 100 + y, 1.0 / y, 3))
            .toList();
    Set<Long> eligible = new HashSet<>(Set.of(1L));
    LongStream.rangeClosed(101, 125).forEach(eligible::add);

    List<CoReadingEntry> kept = CoReadingRule.keep(candidates, eligible);

    assertThat(kept).hasSize(CoReadingRule.KEEP);
    assertThat(kept.getLast().position()).isEqualTo(20);
    assertThat(kept.getLast().relatedArticleId()).isEqualTo(120L);
  }
}

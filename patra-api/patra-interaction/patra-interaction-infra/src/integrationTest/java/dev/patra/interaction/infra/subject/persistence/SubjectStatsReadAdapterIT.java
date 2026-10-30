package dev.patra.interaction.infra.subject.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.subject.model.read.SubjectStats;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.read.SubjectStatsReadPort;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/// 对象统计读端：只经 SQL 读收藏、讨论两张统计表并按对象合并；两边都没有统计的对象不在结果里。
class SubjectStatsReadAdapterIT extends SubjectRegistryITSupport {

  private static final SubjectId S1 = SubjectId.of(11L);
  private static final SubjectId S2 = SubjectId.of(12L);
  private static final SubjectId S4 = SubjectId.of(14L);
  private static final SubjectId S9 = SubjectId.of(19L);

  @Autowired private SubjectStatsReadPort stats;

  /// 每例从空的统计表开始。
  @BeforeEach
  void truncateStats() {
    jdbcTemplate.update("TRUNCATE collection.subject_stats, discussion.subject_stats");
  }

  /// 有统计行的（含 0）返回，没有的省略。
  @Test
  void should_read_collector_counts_and_omit_unknown() {
    jdbcTemplate.update(
        "INSERT INTO collection.subject_stats (subject_id, collector_count) VALUES (11, 3), (12, 0)");

    Map<SubjectId, SubjectStats> result = stats.findStats(List.of(S1, S2, S9));

    assertThat(result)
        .containsOnly(Map.entry(S1, SubjectStats.of(3, 0)), Map.entry(S2, SubjectStats.ZERO));
  }

  /// 讨论数来自讨论的统计表，与收藏人数按对象合并；只有一边有行的另一项为 0。
  @Test
  void should_merge_discussion_counts() {
    jdbcTemplate.update(
        "INSERT INTO collection.subject_stats (subject_id, collector_count) VALUES (11, 3)");
    jdbcTemplate.update(
        "INSERT INTO discussion.subject_stats (subject_id, comment_count) VALUES (11, 5), (14, 2)");

    Map<SubjectId, SubjectStats> result = stats.findStats(List.of(S1, S4, S9));

    assertThat(result)
        .containsOnly(Map.entry(S1, SubjectStats.of(3, 5)), Map.entry(S4, SubjectStats.of(0, 2)));
  }

  /// 空列表不查库，返回空。
  @Test
  void should_return_empty_for_empty_ids() {
    assertThat(stats.findStats(List.of())).isEmpty();
  }

  /// 超过上限是程序错误。
  @Test
  void should_reject_more_than_max_ids() {
    List<SubjectId> ids =
        LongStream.rangeClosed(1, SubjectStatsReadPort.MAX_IDS + 1)
            .mapToObj(SubjectId::of)
            .toList();

    assertThatThrownBy(() -> stats.findStats(ids)).isInstanceOf(IllegalArgumentException.class);
  }
}

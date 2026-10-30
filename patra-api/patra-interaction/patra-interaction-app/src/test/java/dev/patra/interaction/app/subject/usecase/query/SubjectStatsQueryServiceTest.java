package dev.patra.interaction.app.subject.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.subject.exception.InvalidSubjectRequestException;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectStats;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.interaction.domain.subject.port.read.SubjectStatsReadPort;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/// 批量对象统计：校验类型与个数，去重保序，没登记、没统计的 key 补 0。
class SubjectStatsQueryServiceTest {

  private static final SubjectId S1 = SubjectId.of(11L);

  private final SubjectReadPort subjects = mock(SubjectReadPort.class);
  private final SubjectStatsReadPort stats = mock(SubjectStatsReadPort.class);
  private final SubjectStatsQueryService service = new SubjectStatsQueryService(subjects, stats);

  /// 重复的 key 只回一行，按首次出现的顺序；没登记的两项都是 0；只拿已登记的去查统计。
  @Test
  void should_return_one_row_per_unique_key_in_order() {
    when(subjects.findIdsByKeys(SubjectType.PUBLICATION, List.of("2", "1")))
        .thenReturn(Map.of("1", S1));
    when(stats.findStats(List.of(S1))).thenReturn(Map.of(S1, SubjectStats.of(3, 4)));

    List<SubjectStatsView> result = service.stats("publication", List.of("2", "1", "2"));

    assertThat(result)
        .containsExactly(SubjectStatsView.of("2", 0, 0), SubjectStatsView.of("1", 3, 4));
  }

  /// 已登记但两张统计表都没有行：两项是 0。
  @Test
  void should_fill_zero_for_registered_key_without_stats() {
    when(subjects.findIdsByKeys(SubjectType.PUBLICATION, List.of("1"))).thenReturn(Map.of("1", S1));
    when(stats.findStats(List.of(S1))).thenReturn(Map.of());

    assertThat(service.stats("publication", List.of("1")))
        .containsExactly(SubjectStatsView.of("1", 0, 0));
  }

  /// 一个都没登记就不查统计。
  @Test
  void should_not_query_stats_when_no_key_registered() {
    when(subjects.findIdsByKeys(any(), any())).thenReturn(Map.of());

    assertThat(service.stats("publication", List.of("abc")))
        .containsExactly(SubjectStatsView.of("abc", 0, 0));
    verify(stats, never()).findStats(any());
  }

  /// 投稿经验只能讨论，照常返回。
  @Test
  void should_accept_post_type() {
    when(subjects.findIdsByKeys(SubjectType.POST, List.of("5"))).thenReturn(Map.of());

    assertThat(service.stats("post", List.of("5"))).containsExactly(SubjectStatsView.of("5", 0, 0));
  }

  /// 未知类型 422。
  @Test
  void should_reject_unknown_type() {
    assertThatThrownBy(() -> service.stats("book", List.of("1")))
        .isInstanceOf(InvalidSubjectRequestException.class);
  }

  /// 期刊既不能收藏也不能讨论，422。
  @Test
  void should_reject_type_without_collect_or_discuss() {
    assertThatThrownBy(() -> service.stats("journal", List.of("1")))
        .isInstanceOf(InvalidSubjectRequestException.class);
  }

  /// 空、`null`、超过 100 个都 422。
  @Test
  void should_reject_empty_null_or_too_many_keys() {
    List<String> tooMany = IntStream.rangeClosed(1, 101).mapToObj(Integer::toString).toList();

    assertThatThrownBy(() -> service.stats("publication", List.of()))
        .isInstanceOf(InvalidSubjectRequestException.class);
    assertThatThrownBy(() -> service.stats("publication", null))
        .isInstanceOf(InvalidSubjectRequestException.class);
    assertThatThrownBy(() -> service.stats("publication", tooMany))
        .isInstanceOf(InvalidSubjectRequestException.class);
  }
}

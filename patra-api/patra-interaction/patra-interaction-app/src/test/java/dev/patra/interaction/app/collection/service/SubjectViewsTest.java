package dev.patra.interaction.app.collection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.collection.Fixtures;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/// 按读端口的上限分块取快照。
class SubjectViewsTest {

  /// 12,001 个分三块：5000、5000、2001，结果合并。
  @Test
  void should_chunk_by_5000() {
    SubjectReadPort port = mock(SubjectReadPort.class);
    List<Integer> sizes = new ArrayList<>();
    when(port.findByIds(anyCollection()))
        .thenAnswer(
            inv -> {
              Collection<SubjectId> ids = inv.getArgument(0);
              sizes.add(ids.size());
              return ids.stream()
                  .collect(
                      Collectors.toMap(
                          Function.identity(),
                          id -> Fixtures.view(id.value(), SubjectState.ACTIVE, "k", "J", null)));
            });
    List<SubjectId> ids = LongStream.rangeClosed(1, 12_001).mapToObj(SubjectId::of).toList();

    Map<SubjectId, SubjectView> views = new SubjectViews(port).fetch(ids);

    assertThat(sizes).containsExactly(5000, 5000, 2001);
    assertThat(views).hasSize(12_001);
  }

  /// 空列表不查。
  @Test
  void should_skip_empty() {
    SubjectReadPort port = mock(SubjectReadPort.class);

    assertThat(new SubjectViews(port).fetch(List.of())).isEmpty();
  }
}

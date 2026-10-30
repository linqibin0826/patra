package dev.patra.interaction.app.subject.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.subject.exception.InvalidSubjectRequestException;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/// 内部接口「按 ID 取对象视图」的查询：去重保序、查不到的省略、批量上限。
class SubjectViewQueryServiceTest {

  private final SubjectReadPort subjects = mock(SubjectReadPort.class);
  private final SubjectViewQueryService query = new SubjectViewQueryService(subjects);

  /// 按去重后的请求顺序返回，查不到的省略。
  @Test
  void should_keep_request_order_and_omit_unknown() {
    when(subjects.findByIds(List.of(SubjectId.of(41L), SubjectId.of(40L), SubjectId.of(42L))))
        .thenReturn(Map.of(SubjectId.of(40L), view(40L), SubjectId.of(41L), view(41L)));

    assertThat(query.views(List.of(41L, 40L, 41L, 42L)))
        .extracting(v -> v.subjectId().value())
        .containsExactly(41L, 40L);
  }

  /// 空列表：直接返回空，不查库。
  @Test
  void should_return_empty_for_empty_request() {
    assertThat(query.views(List.of())).isEmpty();
    verifyNoInteractions(subjects);
  }

  /// 缺列表、含 null、超过 100 个：422。
  @Test
  void should_reject_null_or_over_100() {
    assertThatThrownBy(() -> query.views(null)).isInstanceOf(InvalidSubjectRequestException.class);
    assertThatThrownBy(() -> query.views(Arrays.asList(1L, null)))
        .isInstanceOf(InvalidSubjectRequestException.class);
    assertThatThrownBy(() -> query.views(LongStream.rangeClosed(1, 101).boxed().toList()))
        .isInstanceOf(InvalidSubjectRequestException.class);
    verifyNoInteractions(subjects);
  }

  /// 一个文献视图。
  ///
  /// @param id 登记表 ID
  /// @return 视图
  private static SubjectView view(long id) {
    return SubjectView.builder()
        .subjectId(SubjectId.of(id))
        .type(SubjectType.PUBLICATION)
        .key("18")
        .state(SubjectState.ACTIVE)
        .build();
  }
}

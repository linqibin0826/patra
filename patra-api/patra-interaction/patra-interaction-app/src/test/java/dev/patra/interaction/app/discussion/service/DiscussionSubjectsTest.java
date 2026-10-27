package dev.patra.interaction.app.discussion.service;

import static dev.patra.interaction.app.discussion.DiscussionFixture.PUB_ID;
import static dev.patra.interaction.app.discussion.DiscussionFixture.publication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.discussion.exception.InvalidDiscussionRequestException;
import dev.patra.interaction.domain.subject.exception.InvalidSubjectKeyException;
import dev.patra.interaction.domain.subject.exception.SubjectCapabilityNotSupportedException;
import dev.patra.interaction.domain.subject.exception.SubjectErrorTrait;
import dev.patra.interaction.domain.subject.exception.SubjectNotFoundException;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/// 讨论的对象：本地校验类型、能力与 key；读只查本地登记，失效的 404。
class DiscussionSubjectsTest {

  private final SubjectReadPort reads = mock(SubjectReadPort.class);
  private final DiscussionSubjects subjects = new DiscussionSubjects(reads);

  /// 未知类型、不能讨论的类型、格式不对的 key 都是 422。
  @Test
  void should_reject_unknown_type_capability_and_bad_key() {
    assertThatThrownBy(() -> subjects.requireDiscussable("book", "1"))
        .isInstanceOf(InvalidDiscussionRequestException.class);
    assertThatThrownBy(() -> subjects.requireDiscussable("journal", "1"))
        .isInstanceOf(SubjectCapabilityNotSupportedException.class);
    assertThatThrownBy(() -> subjects.requireDiscussable("publication", "0"))
        .isInstanceOf(InvalidSubjectKeyException.class);
    assertThat(subjects.requireDiscussable("post", "500")).isEqualTo(SubjectType.POST);
  }

  /// 读：没登记过为空；失效抛 404（带「已失效」原因码）；正常返回视图。
  @Test
  void should_find_registered_subjects_for_reading() {
    when(reads.findIdsByKeys(SubjectType.PUBLICATION, List.of("100"))).thenReturn(Map.of());
    assertThat(subjects.findForRead(SubjectType.PUBLICATION, "100")).isEmpty();

    when(reads.findIdsByKeys(SubjectType.PUBLICATION, List.of("100")))
        .thenReturn(Map.of("100", PUB_ID));
    when(reads.findByIds(List.of(PUB_ID))).thenReturn(Map.of(PUB_ID, publication()));
    assertThat(subjects.findForRead(SubjectType.PUBLICATION, "100")).contains(publication());

    SubjectView gone = unavailable();
    when(reads.findByIds(List.of(PUB_ID))).thenReturn(Map.of(PUB_ID, gone));
    assertThatThrownBy(() -> subjects.findForRead(SubjectType.PUBLICATION, "100"))
        .isInstanceOfSatisfying(
            SubjectNotFoundException.class,
            ex -> assertThat(ex.getErrorTraits()).contains(SubjectErrorTrait.SUBJECT_UNAVAILABLE));
  }

  /// 按 ID：正常返回；失效 404；登记表里没有是程序错误。
  @Test
  void should_require_active_subject_by_id() {
    when(reads.findByIds(List.of(PUB_ID))).thenReturn(Map.of(PUB_ID, publication()));
    assertThat(subjects.requireActive(PUB_ID)).isEqualTo(publication());

    SubjectView gone = unavailable();
    when(reads.findByIds(List.of(PUB_ID))).thenReturn(Map.of(PUB_ID, gone));
    assertThatThrownBy(() -> subjects.requireActive(PUB_ID))
        .isInstanceOf(SubjectNotFoundException.class);

    when(reads.findByIds(List.of(PUB_ID))).thenReturn(Map.of());
    assertThatThrownBy(() -> subjects.requireActive(PUB_ID))
        .isInstanceOf(IllegalStateException.class);
  }

  /// 失效的文献。
  ///
  /// @return 视图
  private static SubjectView unavailable() {
    SubjectView active = publication();
    return SubjectView.builder()
        .subjectId(active.subjectId())
        .type(active.type())
        .key(active.key())
        .state(SubjectState.UNAVAILABLE)
        .snapshot(active.snapshot())
        .build();
  }
}

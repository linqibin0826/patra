package dev.patra.interaction.domain.reaction.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/// 有帮助的领域事件都要带事件 ID（PAP-141）：外送时原样带进集成事件，补投靠它不变才去得了重，所以缺了就拒绝。
class ReactionEventsTest {

  private static final Instant AT = Instant.parse("2026-10-31T01:00:00Z");

  /// 有帮助已点缺事件 ID。
  @Test
  void should_reject_marked_without_event_id() {
    assertThatThrownBy(
            () ->
                HelpfulMarkedEvent.builder()
                    .targetType(HelpfulTargetType.COMMENT)
                    .targetId(1L)
                    .targetAuthorId(2L)
                    .userId(3L)
                    .markedAt(AT)
                    .subjectId(SubjectId.of(4L))
                    .subjectType(SubjectType.PUBLICATION)
                    .build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("eventId");
  }

  /// 有帮助已点缺外送映射要用的字段：目标种类、对象与对象种类。
  @Test
  void should_reject_marked_missing_fields_the_mapping_needs() {
    assertThatThrownBy(() -> marked().targetType(null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("targetType");
    assertThatThrownBy(() -> marked().subjectId(null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("subjectId");
    assertThatThrownBy(() -> marked().subjectType(null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("subjectType");
  }

  /// 一个字段齐全的「有帮助已点」。
  ///
  /// @return 构造器
  private static HelpfulMarkedEvent.HelpfulMarkedEventBuilder marked() {
    return HelpfulMarkedEvent.builder()
        .eventId(UUID.randomUUID())
        .targetType(HelpfulTargetType.COMMENT)
        .targetId(1L)
        .targetAuthorId(2L)
        .userId(3L)
        .markedAt(AT)
        .subjectId(SubjectId.of(4L))
        .subjectType(SubjectType.PUBLICATION);
  }

  /// 有帮助已取消缺事件 ID。
  @Test
  void should_reject_unmarked_without_event_id() {
    assertThatThrownBy(
            () ->
                HelpfulUnmarkedEvent.builder()
                    .targetType(HelpfulTargetType.POST)
                    .targetId(1L)
                    .userId(3L)
                    .unmarkedAt(AT)
                    .build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("eventId");
  }
}

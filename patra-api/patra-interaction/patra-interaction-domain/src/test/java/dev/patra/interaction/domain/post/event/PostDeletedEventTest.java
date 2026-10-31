package dev.patra.interaction.domain.post.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/// 投稿经验已删除要带事件 ID（PAP-141），缺了就拒绝。
class PostDeletedEventTest {

  /// 缺登记对象（外送映射要用）。
  @Test
  void should_reject_missing_subject_id() {
    assertThatThrownBy(
            () ->
                PostDeletedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .postId(1L)
                    .authorId(3L)
                    .deletedAt(Instant.parse("2026-10-31T01:00:00Z"))
                    .build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("subjectId");
  }

  /// 缺事件 ID。
  @Test
  void should_reject_missing_event_id() {
    assertThatThrownBy(
            () ->
                PostDeletedEvent.builder()
                    .postId(1L)
                    .subjectId(SubjectId.of(2L))
                    .authorId(3L)
                    .deletedAt(Instant.parse("2026-10-31T01:00:00Z"))
                    .build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("eventId");
  }
}

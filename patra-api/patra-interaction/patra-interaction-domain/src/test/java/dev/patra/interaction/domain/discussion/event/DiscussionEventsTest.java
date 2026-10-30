package dev.patra.interaction.domain.discussion.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/// 事件是不可变的契约：列表字段在构造时拷贝，`null` 视为空。
class DiscussionEventsTest {

  private static final Instant T0 = Instant.parse("2026-10-27T08:00:00Z");

  /// 发表事件拷贝提及列表。
  @Test
  void should_copy_lists_of_published_event() {
    List<Long> mentions = new ArrayList<>(List.of(7L));
    CommentPublishedEvent event = published().mentionedUserIds(mentions).build();
    mentions.add(8L);

    assertThat(event.mentionedUserIds()).containsExactly(7L);
    assertThatThrownBy(() -> event.mentionedUserIds().add(9L))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(published().mentionedUserIds(null).build().mentionedUserIds()).isEmpty();
  }

  /// 编辑事件拷贝两个列表。
  @Test
  void should_copy_lists_of_edited_event() {
    List<Long> added = new ArrayList<>(List.of(8L));
    CommentEditedEvent event = edited().addedMentionedUserIds(added).build();
    added.add(9L);

    assertThat(event.addedMentionedUserIds()).containsExactly(8L);
    assertThat(event.mentionedUserIds()).isEmpty();
    assertThatThrownBy(() -> event.addedMentionedUserIds().add(10L))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  /// 评论已删除缺事件 ID 就拒绝（PAP-141）：外送时原样带进集成事件，补投靠它不变才去得了重，所以不兜底生成。
  @Test
  void should_reject_comment_deleted_without_event_id() {
    assertThatThrownBy(
            () ->
                CommentDeletedEvent.builder()
                    .commentId(1L)
                    .version(1)
                    .subjectId(SubjectId.of(2L))
                    .authorId(3L)
                    .deletedAt(T0)
                    .build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("eventId");
  }

  /// 一个发表事件（列表另给）。
  ///
  /// @return 构造器
  private static CommentPublishedEvent.CommentPublishedEventBuilder published() {
    return CommentPublishedEvent.builder()
        .commentId(100L)
        .subjectId(SubjectId.of(11L))
        .subjectType(SubjectType.POST)
        .subjectOwner(SubjectOwner.of(9L, true))
        .rootCommentId(100L)
        .authorId(1L)
        .authorAnonymous(false)
        .body("正文")
        .publishedAt(T0);
  }

  /// 一个编辑事件（列表另给）。
  ///
  /// @return 构造器
  private static CommentEditedEvent.CommentEditedEventBuilder edited() {
    return CommentEditedEvent.builder()
        .commentId(100L)
        .subjectId(SubjectId.of(11L))
        .subjectType(SubjectType.PUBLICATION)
        .rootCommentId(100L)
        .authorId(1L)
        .authorAnonymous(false)
        .body("正文")
        .editedAt(T0);
  }
}

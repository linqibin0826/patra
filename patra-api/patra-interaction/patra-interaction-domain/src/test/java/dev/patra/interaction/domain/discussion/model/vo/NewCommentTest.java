package dev.patra.interaction.domain.discussion.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.discussion.event.CommentPublishedEvent;
import dev.patra.interaction.domain.discussion.model.aggregate.CommentAggregate;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/// 新评论产生「评论已发表」事件：评论 ID 要等插入后才有，由调用方传入；根评论的根是它自己。
class NewCommentTest {

  private static final Instant T0 = Instant.parse("2026-10-27T08:00:00Z");
  private static final SubjectId SUBJECT = SubjectId.of(11L);
  private static final long AUTHOR = 1L;

  /// 回复：根与父来自新评论，父评论作者由调用方给（锁内读到的）。
  @Test
  void should_produce_published_event_for_reply() {
    SubjectOwner owner = SubjectOwner.of(AUTHOR, true);

    CommentPublishedEvent event = comment(40L, 50L).publishedEvent(60L, subject(owner), 7L, "纯文本");

    assertThat(event)
        .usingRecursiveComparison()
        .ignoringFields("eventId")
        .isEqualTo(
            CommentPublishedEvent.builder()
                .eventId(UUID.randomUUID())
                .commentId(60L)
                .version(CommentAggregate.INITIAL_VERSION)
                .subjectId(SUBJECT)
                .subjectType(SubjectType.POST)
                .subjectOwner(owner)
                .rootCommentId(40L)
                .parentCommentId(50L)
                .parentAuthorId(7L)
                .authorId(AUTHOR)
                .authorAnonymous(true)
                .mentionedUserIds(List.of(8L))
                .plainText("纯文本")
                .publishedAt(T0)
                .build());
  }

  /// 根评论：根是刚生成的 ID，没有父评论。
  @Test
  void should_use_own_id_as_root_for_root_comment() {
    CommentPublishedEvent event =
        comment(null, null).publishedEvent(60L, subject(null), null, "纯文本");

    assertThat(event.rootCommentId()).isEqualTo(60L);
    assertThat(event.parentCommentId()).isNull();
    assertThat(event.authorAnonymous()).isFalse();
  }

  /// 一条新评论。
  ///
  /// @param rootId 根评论，根评论自己为 `null`
  /// @param parentId 父评论，根评论为 `null`
  /// @return 新评论
  private static NewComment comment(Long rootId, Long parentId) {
    return NewComment.builder()
        .subjectId(SUBJECT)
        .rootId(rootId)
        .parentId(parentId)
        .authorId(AUTHOR)
        .body("正文")
        .mentionedUserIds(List.of(8L))
        .createdAt(T0)
        .build();
  }

  /// 评论所在的对象。
  ///
  /// @param owner 所有者，可空
  /// @return 对象
  private static SubjectView subject(SubjectOwner owner) {
    return SubjectView.builder().subjectId(SUBJECT).type(SubjectType.POST).owner(owner).build();
  }
}

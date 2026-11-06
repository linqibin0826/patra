package dev.patra.interaction.domain.discussion.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.discussion.event.CommentDeletedEvent;
import dev.patra.interaction.domain.discussion.event.CommentEditedEvent;
import dev.patra.interaction.domain.discussion.exception.CommentNotFoundException;
import dev.patra.interaction.domain.discussion.exception.CommentNotOwnedException;
import dev.patra.interaction.domain.discussion.exception.ReplyTargetGoneException;
import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.discussion.model.read.RevisionRecord;
import dev.patra.interaction.domain.discussion.model.vo.CommentEdit;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/// 评论的规则：只有作者能改能删；发布 5 分钟内的修改不记版本，之后每次存一版；只对第一次 @ 到的人算「新增」；
/// 正常评论与本楼的根评论（即使已成占位）可以被回复；有回复的删除留占位；确实发生的编辑和删除产生事件。
class CommentAggregateTest {

  private static final Instant T0 = Instant.parse("2026-10-27T08:00:00Z");
  private static final long AUTHOR = 1L;
  private static final long OTHER = 2L;
  private static final SubjectId SUBJECT = SubjectId.of(11L);

  /// 别人不能编辑。
  @Test
  void should_reject_edit_by_someone_else() {
    assertThatThrownBy(() -> root().build().edit(OTHER, "新", List.of(), T0))
        .isInstanceOf(CommentNotOwnedException.class);
  }

  /// 占位不能编辑。
  @Test
  void should_reject_edit_of_placeholder() {
    CommentAggregate placeholder = root().state(CommentState.DELETED).body(null).build();

    assertThatThrownBy(() -> placeholder.edit(AUTHOR, "新", List.of(), T0))
        .isInstanceOf(CommentNotFoundException.class);
  }

  /// 正文没变：什么都不做。
  @Test
  void should_return_empty_when_body_unchanged() {
    assertThat(root().build().edit(AUTHOR, "旧", List.of(7L), T0.plusSeconds(600))).isEmpty();
  }

  /// 发布后整 5 分钟之内：原地替换，不记版本。
  @Test
  void should_replace_in_place_up_to_five_minutes() {
    CommentEdit edit =
        root().build().edit(AUTHOR, "新", List.of(), T0.plus(5, ChronoUnit.MINUTES)).orElseThrow();

    assertThat(edit.archived()).isNull();
    assertThat(edit.updated().getBody()).isEqualTo("新");
    assertThat(edit.updated().getRevisedAt()).isNull();
  }

  /// 超过 5 分钟：旧正文存成一版，版本时间是发表时刻。
  @Test
  void should_archive_previous_version_after_five_minutes() {
    Instant now = T0.plus(5, ChronoUnit.MINUTES).plus(1, ChronoUnit.MICROS);

    CommentEdit edit = root().build().edit(AUTHOR, "新", List.of(), now).orElseThrow();

    assertThat(edit.archived()).isEqualTo(RevisionRecord.of("旧", T0));
    assertThat(edit.updated().getBody()).isEqualTo("新");
    assertThat(edit.updated().getRevisedAt()).isEqualTo(now);
  }

  /// 再改一次：存下的版本时间是上一次记版本的编辑时刻。
  @Test
  void should_archive_with_previous_revision_time_later_on() {
    Instant revised = T0.plus(1, ChronoUnit.HOURS);
    CommentAggregate comment = root().revisedAt(revised).build();

    CommentEdit edit =
        comment.edit(AUTHOR, "新", List.of(), revised.plus(1, ChronoUnit.HOURS)).orElseThrow();

    assertThat(edit.archived().at()).isEqualTo(revised);
  }

  /// 只有这条评论第一次 @ 到的人算「新增」；曾经 @ 过的人并进去。
  @Test
  void should_report_only_first_time_mentions() {
    CommentEdit edit = root().build().edit(AUTHOR, "新", List.of(8L, 7L, 9L), T0).orElseThrow();

    assertThat(edit.addedMentionedUserIds()).containsExactly(8L, 9L);
    assertThat(edit.updated().getMentionedUserIds()).containsExactlyInAnyOrder(7L, 8L, 9L);
  }

  /// 发布 5 分钟内的原地修改也加版本：摘录变了，事件的消费方要能分出新旧。
  @Test
  void should_increment_version_when_body_changes_within_grace() {
    CommentEdit edit =
        root().build().edit(AUTHOR, "新", List.of(), T0.plusSeconds(60)).orElseThrow();

    assertThat(edit.updated().getVersion()).isEqualTo(2);
  }

  /// 超过 5 分钟、记了版本的修改同样加一。
  @Test
  void should_increment_version_when_body_changes_after_grace() {
    CommentAggregate comment = root().version(2).build();

    CommentEdit edit =
        comment.edit(AUTHOR, "新", List.of(), T0.plus(1, ChronoUnit.HOURS)).orElseThrow();

    assertThat(edit.updated().getVersion()).isEqualTo(3);
  }

  /// 版本从 1 起。
  @Test
  void should_reject_non_positive_version() {
    assertThatThrownBy(() -> root().version(0).build()).isInstanceOf(IllegalStateException.class);
  }

  /// 正常评论、已成占位的根评论都能被回复；已成占位的回复不能。
  @Test
  void should_allow_replies_to_active_or_root_placeholder_only() {
    reply().build().requireRepliable();
    root().state(CommentState.DELETED).body(null).build().requireRepliable();

    CommentAggregate deletedReply = reply().state(CommentState.DELETED).body(null).build();
    assertThatThrownBy(deletedReply::requireRepliable).isInstanceOf(ReplyTargetGoneException.class);
  }

  /// 别人不能删。
  @Test
  void should_reject_delete_by_someone_else() {
    assertThatThrownBy(() -> root().build().delete(OTHER, false, T0))
        .isInstanceOf(CommentNotOwnedException.class);
  }

  /// 已是占位再删：什么都没发生，不产生事件。
  @Test
  void should_not_produce_event_when_deleting_placeholder() {
    CommentAggregate placeholder = root().state(CommentState.DELETED).body(null).build();

    assertThat(placeholder.delete(AUTHOR, true, T0)).isEmpty();
  }

  /// 有回复：产生「留下占位」的事件，带删除时的正文版本与删除时刻。
  @Test
  void should_produce_placeholder_event_when_comment_has_replies() {
    CommentDeletedEvent event = reply().version(3).build().delete(AUTHOR, true, T0).orElseThrow();

    assertThat(event)
        .usingRecursiveComparison()
        .ignoringFields("eventId")
        .isEqualTo(
            CommentDeletedEvent.builder()
                .eventId(UUID.randomUUID())
                .commentId(101L)
                .version(3)
                .subjectId(SUBJECT)
                .authorId(AUTHOR)
                .placeholder(true)
                .deletedAt(T0)
                .build());
  }

  /// 没有回复：产生「整条去掉」的事件。
  @Test
  void should_produce_removal_event_when_comment_has_no_replies() {
    assertThat(root().build().delete(AUTHOR, false, T0).orElseThrow().placeholder()).isFalse();
  }

  /// 每次产生的事件 ID 都是新的：消费方靠它去重，两次删除不能被当成同一次。
  @Test
  void should_generate_new_event_id_for_each_delete() {
    CommentAggregate comment = root().build();

    assertThat(comment.delete(AUTHOR, true, T0).orElseThrow().eventId())
        .isNotEqualTo(comment.delete(AUTHOR, true, T0).orElseThrow().eventId());
  }

  /// 只有已是占位、且没有回复的评论，才跟着被删的回复一起去掉。
  @Test
  void should_identify_orphaned_placeholder() {
    CommentAggregate placeholder = root().state(CommentState.DELETED).body(null).build();

    assertThat(placeholder.isOrphanedPlaceholder(false)).isTrue();
    assertThat(placeholder.isOrphanedPlaceholder(true)).isFalse();
    assertThat(root().build().isOrphanedPlaceholder(false)).isFalse();
  }

  /// 回复已成占位的根评论时没有可提醒的作者：只有正常评论才给出作者。
  @Test
  void should_give_author_only_when_active() {
    assertThat(root().build().authorIdIfActive()).isEqualTo(AUTHOR);
    assertThat(root().state(CommentState.DELETED).body(null).build().authorIdIfActive()).isNull();
  }

  /// 编辑结果产生事件：新版本、第一次 @ 到的人来自编辑本身，其余是聚合外的数据。
  @Test
  void should_produce_edited_event_from_edit() {
    CommentEdit edit = reply().build().edit(AUTHOR, "新", List.of(7L, 8L), T0).orElseThrow();
    SubjectOwner owner = SubjectOwner.of(AUTHOR, true);

    CommentEditedEvent event =
        edit.editedEvent(postSubject(owner), 9L, List.of(7L, 8L), "纯文本", T0.plusSeconds(1));

    assertThat(event)
        .usingRecursiveComparison()
        .ignoringFields("eventId")
        .isEqualTo(
            CommentEditedEvent.builder()
                .eventId(UUID.randomUUID())
                .commentId(101L)
                .version(2)
                .subjectId(SUBJECT)
                .subjectType(SubjectType.POST)
                .subjectOwner(owner)
                .rootCommentId(100L)
                .parentCommentId(100L)
                .parentAuthorId(9L)
                .authorId(AUTHOR)
                .authorAnonymous(true)
                .mentionedUserIds(List.of(7L, 8L))
                .addedMentionedUserIds(List.of(8L))
                .plainText("纯文本")
                .editedAt(T0.plusSeconds(1))
                .build());
  }

  /// 作者只有是对象的匿名所有者时才算匿名：所有者不是他、所有者没匿名、对象没有所有者（文献）都不算。
  @Test
  void should_mark_edited_event_anonymous_only_for_anonymous_owner() {
    CommentEdit edit = root().build().edit(AUTHOR, "新", List.of(), T0).orElseThrow();

    assertThat(anonymousIn(edit, SubjectOwner.of(AUTHOR, true))).isTrue();
    assertThat(anonymousIn(edit, SubjectOwner.of(OTHER, true))).isFalse();
    assertThat(anonymousIn(edit, SubjectOwner.of(AUTHOR, false))).isFalse();
    assertThat(anonymousIn(edit, null)).isFalse();
  }

  /// 这次编辑的事件里，作者算不算匿名。
  ///
  /// @param edit 编辑结果
  /// @param owner 对象所有者，可空
  /// @return 匿名为 `true`
  private static boolean anonymousIn(CommentEdit edit, SubjectOwner owner) {
    return edit.editedEvent(postSubject(owner), null, List.of(), "纯文本", T0).authorAnonymous();
  }

  /// 评论所在的对象。
  ///
  /// @param owner 所有者，可空
  /// @return 对象
  private static SubjectView postSubject(SubjectOwner owner) {
    return SubjectView.builder().subjectId(SUBJECT).type(SubjectType.POST).owner(owner).build();
  }

  /// 不变量：正常评论有正文、占位没有；根评论没有父评论且根是自己，回复反之。
  @Test
  void should_enforce_invariants() {
    assertThatThrownBy(() -> root().body(null).build()).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> root().state(CommentState.DELETED).build())
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> root().parentId(99L).build())
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> reply().rootId(101L).build())
        .isInstanceOf(IllegalStateException.class);
  }

  /// 一条根评论：作者 1，正文「旧」，@ 过 7。
  ///
  /// @return 构造器
  private static CommentAggregate.CommentAggregateBuilder root() {
    return CommentAggregate.builder()
        .commentId(100L)
        .subjectId(SUBJECT)
        .rootId(100L)
        .authorId(AUTHOR)
        .state(CommentState.ACTIVE)
        .body("旧")
        .mentionedUserIds(Set.of(7L))
        .createdAt(T0)
        .version(CommentAggregate.INITIAL_VERSION);
  }

  /// 根评论下的一条回复。
  ///
  /// @return 构造器
  private static CommentAggregate.CommentAggregateBuilder reply() {
    return root().commentId(101L).parentId(100L);
  }
}

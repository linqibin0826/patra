package dev.patra.interaction.domain.discussion.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.discussion.exception.CommentNotFoundException;
import dev.patra.interaction.domain.discussion.exception.CommentNotOwnedException;
import dev.patra.interaction.domain.discussion.exception.ReplyTargetGoneException;
import dev.patra.interaction.domain.discussion.model.enums.CommentRemoval;
import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.discussion.model.read.RevisionRecord;
import dev.patra.interaction.domain.discussion.model.vo.CommentEdit;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/// 评论的规则：只有作者能改能删；发布 5 分钟内的修改不记版本，之后每次存一版；只对第一次 @ 到的人算「新增」；
/// 正常评论与本楼的根评论（即使已成占位）可以被回复；有回复的删除留占位。
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

  /// 删除：别人不能删；已是占位不再动；有回复留占位；没有回复整条去掉。
  @Test
  void should_decide_removal() {
    CommentAggregate comment = root().build();

    assertThatThrownBy(() -> comment.removalFor(OTHER, false))
        .isInstanceOf(CommentNotOwnedException.class);
    assertThat(root().state(CommentState.DELETED).body(null).build().removalFor(AUTHOR, true))
        .isEqualTo(CommentRemoval.NONE);
    assertThat(comment.removalFor(AUTHOR, true)).isEqualTo(CommentRemoval.PLACEHOLDER);
    assertThat(comment.removalFor(AUTHOR, false)).isEqualTo(CommentRemoval.REMOVE);
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

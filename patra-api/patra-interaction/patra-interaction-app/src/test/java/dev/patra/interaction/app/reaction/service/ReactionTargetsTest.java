package dev.patra.interaction.app.reaction.service;

import static dev.patra.interaction.app.reaction.ReactionFixtures.comment;
import static dev.patra.interaction.app.reaction.ReactionFixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.discussion.port.read.DiscussionReadPort;
import dev.patra.interaction.domain.reaction.exception.HelpfulTargetNotFoundException;
import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import dev.patra.interaction.domain.reaction.model.vo.HelpfulTarget;
import dev.patra.interaction.domain.reaction.model.vo.TargetProfile;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/// 目标解析：评论经讨论读端口 + 登记表，经验只经登记表；匿名、可点按 spec §3.2 判。
class ReactionTargetsTest {

  private static final long AUTHOR = 7L;
  private static final long OTHER = 8L;
  private static final long COMMENT_ID = 42L;
  private static final long POST_ID = 500L;
  private static final SubjectId PAPER = SubjectId.of(11L);
  private static final SubjectId POST_SUBJECT = SubjectId.of(12L);
  private static final HelpfulTarget COMMENT =
      HelpfulTarget.of(HelpfulTargetType.COMMENT, COMMENT_ID);
  private static final HelpfulTarget POST = HelpfulTarget.of(HelpfulTargetType.POST, POST_ID);

  private final DiscussionReadPort discussions = mock(DiscussionReadPort.class);
  private final SubjectReadPort subjects = mock(SubjectReadPort.class);
  private final ReactionTargets targets = new ReactionTargets(discussions, subjects);

  @Test
  void should_profile_comment_under_publication() {
    givenComment(AUTHOR, CommentState.ACTIVE, PAPER);
    givenSubject(PAPER, SubjectType.PUBLICATION, SubjectState.ACTIVE, null);

    assertThat(targets.require(COMMENT)).isEqualTo(TargetProfile.of(COMMENT, AUTHOR, true, false));
  }

  /// 只有「所有者匿名、且评论作者就是所有者」才算匿名。
  @Test
  void should_mark_owner_comment_anonymous_under_anonymous_post() {
    givenSubject(
        POST_SUBJECT, SubjectType.POST, SubjectState.ACTIVE, SubjectOwner.of(AUTHOR, true));

    givenComment(AUTHOR, CommentState.ACTIVE, POST_SUBJECT);
    assertThat(targets.require(COMMENT).anonymous()).isTrue();

    givenComment(OTHER, CommentState.ACTIVE, POST_SUBJECT);
    assertThat(targets.require(COMMENT).anonymous()).isFalse();

    givenSubject(
        POST_SUBJECT, SubjectType.POST, SubjectState.ACTIVE, SubjectOwner.of(AUTHOR, false));
    givenComment(AUTHOR, CommentState.ACTIVE, POST_SUBJECT);
    assertThat(targets.require(COMMENT).anonymous()).isFalse();
  }

  @Test
  void should_mark_placeholder_unavailable() {
    givenComment(AUTHOR, CommentState.DELETED, PAPER);
    givenSubject(PAPER, SubjectType.PUBLICATION, SubjectState.ACTIVE, null);

    assertThat(targets.require(COMMENT).available()).isFalse();
  }

  /// 评论所在的经验已删、文献已下线：评论不能点。
  @Test
  void should_mark_comment_unavailable_when_subject_unavailable() {
    givenComment(AUTHOR, CommentState.ACTIVE, POST_SUBJECT);
    givenSubject(
        POST_SUBJECT, SubjectType.POST, SubjectState.UNAVAILABLE, SubjectOwner.of(OTHER, false));

    assertThat(targets.require(COMMENT).available()).isFalse();
  }

  /// 评论所在对象在登记表里查不到（数据异常）：当作不能点，不让有帮助 500。
  @Test
  void should_mark_comment_unavailable_when_subject_missing() {
    givenComment(AUTHOR, CommentState.ACTIVE, PAPER);
    when(subjects.findByIds(List.of(PAPER))).thenReturn(Map.of());

    assertThat(targets.require(COMMENT).available()).isFalse();
  }

  @Test
  void should_profile_post_from_registry() {
    when(subjects.findIdsByKeys(SubjectType.POST, List.of("500")))
        .thenReturn(Map.of("500", POST_SUBJECT));
    givenSubject(
        POST_SUBJECT, SubjectType.POST, SubjectState.ACTIVE, SubjectOwner.of(AUTHOR, true));

    assertThat(targets.require(POST)).isEqualTo(TargetProfile.of(POST, AUTHOR, true, true));

    givenSubject(
        POST_SUBJECT, SubjectType.POST, SubjectState.UNAVAILABLE, SubjectOwner.of(AUTHOR, false));
    assertThat(targets.require(POST)).isEqualTo(TargetProfile.of(POST, AUTHOR, false, false));
  }

  @Test
  void should_throw_when_target_missing() {
    when(discussions.findById(COMMENT_ID)).thenReturn(Optional.empty());
    when(subjects.findIdsByKeys(SubjectType.POST, List.of("500"))).thenReturn(Map.of());

    assertThatThrownBy(() -> targets.require(COMMENT))
        .isInstanceOf(HelpfulTargetNotFoundException.class);
    assertThatThrownBy(() -> targets.require(POST))
        .isInstanceOf(HelpfulTargetNotFoundException.class);
  }

  /// 桩一条评论。
  ///
  /// @param author 作者
  /// @param state 状态
  /// @param subjectId 所在对象
  private void givenComment(long author, CommentState state, SubjectId subjectId) {
    when(discussions.findById(COMMENT_ID))
        .thenReturn(Optional.of(comment(COMMENT_ID, subjectId, author, state)));
  }

  /// 桩一个登记对象。
  ///
  /// @param id 本地 ID
  /// @param type 种类
  /// @param state 状态
  /// @param owner 所有者
  private void givenSubject(
      SubjectId id, SubjectType type, SubjectState state, SubjectOwner owner) {
    when(subjects.findByIds(List.of(id))).thenReturn(Map.of(id, subject(id, type, state, owner)));
  }
}

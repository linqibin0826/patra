package dev.patra.interaction.app.reaction.service;

import static dev.patra.interaction.app.reaction.ReactionFixtures.NOW;
import static dev.patra.interaction.app.reaction.ReactionFixtures.comment;
import static dev.patra.interaction.app.reaction.ReactionFixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.discussion.model.read.CommentRecord;
import dev.patra.interaction.domain.discussion.port.read.DiscussionReadPort;
import dev.patra.interaction.domain.post.model.enums.FinalOutcome;
import dev.patra.interaction.domain.post.model.enums.FirstDecision;
import dev.patra.interaction.domain.post.model.read.ExperienceDigest;
import dev.patra.interaction.domain.post.model.read.ExperienceOutline;
import dev.patra.interaction.domain.post.port.read.PostReadPort;
import dev.patra.interaction.domain.reaction.exception.HelpfulTargetNotFoundException;
import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import dev.patra.interaction.domain.reaction.model.vo.HelpfulTarget;
import dev.patra.interaction.domain.reaction.model.vo.TargetProfile;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.PlainText;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.port.markdown.PlainTextPort;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
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
  private static final ExperienceOutline OUTLINE =
      ExperienceOutline.of(FirstDecision.MAJOR_REVISION, FinalOutcome.IN_PROGRESS, 10, null);

  private final DiscussionReadPort discussions = mock(DiscussionReadPort.class);
  private final SubjectReadPort subjects = mock(SubjectReadPort.class);
  private final PostReadPort posts = mock(PostReadPort.class);

  /// 假的纯文本转换：把收到的正文与提及名单拼出来，测试据此核对两个参数。
  private final PlainTextPort plainText =
      (markdown, mentioned) -> PlainText.of(markdown + "|" + new TreeSet<>(mentioned));

  private final ReactionTargets targets =
      new ReactionTargets(discussions, subjects, posts, plainText);

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

  /// 描述评论（PAP-141）：所在对象、评论的纯文本（提及名单取自正文）；不读经验。
  @Test
  void should_describe_comment_with_subject_and_plain_text() {
    when(discussions.findById(COMMENT_ID))
        .thenReturn(
            Optional.of(
                CommentRecord.builder()
                    .commentId(COMMENT_ID)
                    .subjectId(POST_SUBJECT)
                    .rootId(COMMENT_ID)
                    .authorId(AUTHOR)
                    .state(CommentState.ACTIVE)
                    .body("看 <@9>")
                    .createdAt(NOW)
                    .build()));
    givenSubject(
        POST_SUBJECT, SubjectType.POST, SubjectState.ACTIVE, SubjectOwner.of(OTHER, false));

    assertThat(targets.describe(COMMENT))
        .isEqualTo(
            TargetFacts.of(
                subject(
                    POST_SUBJECT,
                    SubjectType.POST,
                    SubjectState.ACTIVE,
                    SubjectOwner.of(OTHER, false)),
                null,
                "看 <@9>|[9]"));
    verifyNoInteractions(posts);
  }

  /// 描述经验：经验自己的登记对象、结果、正文的纯文本（经验不解析 @，名单为空）。
  @Test
  void should_describe_post_with_outline_and_body_text() {
    givenPost();
    when(posts.findDigest(POST_ID)).thenReturn(Optional.of(ExperienceDigest.of(OUTLINE, "经验正文")));

    assertThat(targets.describe(POST)).isEqualTo(TargetFacts.of(postView(), OUTLINE, "经验正文|[]"));
  }

  /// 经验没写正文：纯文本为空，结果照带。
  @Test
  void should_describe_post_without_body() {
    givenPost();
    when(posts.findDigest(POST_ID)).thenReturn(Optional.of(ExperienceDigest.of(OUTLINE, null)));

    assertThat(targets.describe(POST)).isEqualTo(TargetFacts.of(postView(), OUTLINE, null));
  }

  /// 经验在 post 里查不到（数据异常）：结果与纯文本都为空，不让点有帮助失败。
  @Test
  void should_describe_post_without_digest() {
    givenPost();
    when(posts.findDigest(POST_ID)).thenReturn(Optional.empty());

    assertThat(targets.describe(POST)).isEqualTo(TargetFacts.of(postView(), null, null));
  }

  /// 正文只有 Markdown 结构、转出来没有文字（如单独一个 `-`）：纯文本记为空，消费方才会退回写结果行。
  @Test
  void should_describe_blank_text_as_null() {
    ReactionTargets blank =
        new ReactionTargets(
            discussions, subjects, posts, (markdown, mentioned) -> PlainText.of(""));
    givenPost();
    when(posts.findDigest(POST_ID)).thenReturn(Optional.of(ExperienceDigest.of(OUTLINE, "-")));

    assertThat(blank.describe(POST)).isEqualTo(TargetFacts.of(postView(), OUTLINE, null));
  }

  /// 描述时目标查不到：同解析画像。
  @Test
  void should_throw_when_describing_missing_target() {
    when(discussions.findById(COMMENT_ID)).thenReturn(Optional.empty());
    when(subjects.findIdsByKeys(SubjectType.POST, List.of("500"))).thenReturn(Map.of());

    assertThatThrownBy(() -> targets.describe(COMMENT))
        .isInstanceOf(HelpfulTargetNotFoundException.class);
    assertThatThrownBy(() -> targets.describe(POST))
        .isInstanceOf(HelpfulTargetNotFoundException.class);
  }

  /// 桩经验 500 的登记行（作者匿名）。
  private void givenPost() {
    when(subjects.findIdsByKeys(SubjectType.POST, List.of("500")))
        .thenReturn(Map.of("500", POST_SUBJECT));
    givenSubject(
        POST_SUBJECT, SubjectType.POST, SubjectState.ACTIVE, SubjectOwner.of(AUTHOR, true));
  }

  /// 经验 500 的登记视图。
  ///
  /// @return 视图
  private static SubjectView postView() {
    return subject(
        POST_SUBJECT, SubjectType.POST, SubjectState.ACTIVE, SubjectOwner.of(AUTHOR, true));
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

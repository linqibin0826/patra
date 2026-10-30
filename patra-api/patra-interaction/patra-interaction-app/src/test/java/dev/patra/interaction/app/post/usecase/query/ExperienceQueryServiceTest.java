package dev.patra.interaction.app.post.usecase.query;

import static dev.patra.interaction.app.post.PostFixtures.JOURNAL;
import static dev.patra.interaction.app.post.PostFixtures.JOURNAL_KEY;
import static dev.patra.interaction.app.post.PostFixtures.NOW;
import static dev.patra.interaction.app.post.PostFixtures.POST_ID;
import static dev.patra.interaction.app.post.PostFixtures.card;
import static dev.patra.interaction.app.post.PostFixtures.journalView;
import static dev.patra.interaction.app.post.PostFixtures.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.post.TestUsers;
import dev.patra.interaction.app.post.service.ExperienceViews;
import dev.patra.interaction.domain.post.exception.InvalidPostRequestException;
import dev.patra.interaction.domain.post.exception.PostNotFoundException;
import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.enums.PostStatus;
import dev.patra.interaction.domain.post.model.read.ExperienceStats;
import dev.patra.interaction.domain.post.model.read.HelpfulRankedPost;
import dev.patra.interaction.domain.post.model.read.PostStats;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.post.model.vo.PostRevision;
import dev.patra.interaction.domain.post.port.read.PostReadPort;
import dev.patra.interaction.domain.post.port.repository.PostRepository;
import dev.patra.interaction.domain.subject.exception.InvalidSubjectKeyException;
import dev.patra.interaction.domain.subject.exception.SubjectErrorTrait;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.RankCursor;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 经验的查询（spec 6.4）：期刊列表与汇总（期刊未登记时不查库、不调 catalog）、详情、修改历史、用户的经验列表。
class ExperienceQueryServiceTest {

  private static final long AUTHOR = 7L;
  private static final long OTHER = 8L;

  private final SubjectReadPort subjects = mock(SubjectReadPort.class);
  private final PostReadPort reads = mock(PostReadPort.class);
  private final PostRepository posts = mock(PostRepository.class);
  private final UserDirectoryPort users = mock(UserDirectoryPort.class);

  @BeforeEach
  void setUp() {
    when(subjects.findIdsByKeys(SubjectType.JOURNAL, List.of(JOURNAL_KEY)))
        .thenReturn(Map.of(JOURNAL_KEY, JOURNAL));
    when(subjects.findByIds(List.of(JOURNAL))).thenReturn(Map.of(JOURNAL, journalView()));
    when(users.findCards(any(), any())).thenReturn(Map.of(AUTHOR, card(AUTHOR, true)));
  }

  /// 没人挂过经验的期刊没有登记：空页，不查库。
  @Test
  void should_return_empty_page_for_unregistered_journal() {
    when(subjects.findIdsByKeys(SubjectType.JOURNAL, List.of("999"))).thenReturn(Map.of());

    JournalExperiencePage page = service(null).journalExperiences("999", null, null, null);

    assertThat(page.items()).isEmpty();
    assertThat(page.nextCursor()).isNull();
    assertThat(page.total()).isZero();
    assertThat(service(null).summary("999", null).count()).isZero();
    verifyNoInteractions(reads);
  }

  /// 期刊 key、每页条数不合法：422。
  @Test
  void should_reject_malformed_journal_key_and_limit() {
    assertThatThrownBy(() -> service(null).journalExperiences("01", null, null, null))
        .isInstanceOf(InvalidSubjectKeyException.class);
    assertThatThrownBy(() -> service(null).journalExperiences(JOURNAL_KEY, null, null, 11))
        .isInstanceOf(InvalidPostRequestException.class);
    assertThatThrownBy(() -> service(null).userExperiences(AUTHOR, null, 0))
        .isInstanceOf(InvalidPostRequestException.class);
  }

  /// 多取一条判断有没有下一页；有才给游标（本页最后一条的 ID）。
  @Test
  void should_set_next_cursor_only_when_more() {
    PostAggregate first = withId(post(AUTHOR, false, NOW), 1L);
    PostAggregate second = withId(post(AUTHOR, false, NOW), 2L);
    PostAggregate third = withId(post(AUTHOR, false, NOW), 3L);
    when(reads.pageByJournal(JOURNAL, PostId.of(9L), 3)).thenReturn(List.of(first, second, third));
    when(reads.pageByJournal(JOURNAL, null, 11)).thenReturn(List.of(first));

    JournalExperiencePage more = service(null).journalExperiences(JOURNAL_KEY, null, "9", 2);
    JournalExperiencePage last = service(null).journalExperiences(JOURNAL_KEY, null, null, null);

    assertThat(more.items())
        .extracting(ExperienceItemView::postId)
        .containsExactly(first.postId(), second.postId());
    assertThat(more.nextCursor()).isEqualTo("2");
    assertThat(last.items()).hasSize(1);
    assertThat(last.nextCursor()).isNull();
  }

  /// 「最有帮助」：按三元组游标取，下一页游标是本页最后一条排序时读出的（有帮助数、发布时刻、ID），不取后一条
  /// 统计查询里可能已经变了的数，否则两条查询之间有人取消有帮助会跳过同数的经验。
  @Test
  void should_page_by_helpful_with_rank_cursor() {
    PostAggregate first = withId(post(AUTHOR, false, NOW), 1L);
    PostAggregate second = withId(post(AUTHOR, false, NOW.plusSeconds(1)), 2L);
    RankCursor after = RankCursor.of(9, NOW.plusSeconds(5), 8L);
    when(reads.pageByJournalHelpful(JOURNAL, after, 2))
        .thenReturn(List.of(HelpfulRankedPost.of(first, 4), HelpfulRankedPost.of(second, 2)));
    when(reads.findStats(List.of(first.postId()), null))
        .thenReturn(Map.of(first.postId(), PostStats.of(3, 0, null)));

    JournalExperiencePage page =
        service(null).journalExperiences(JOURNAL_KEY, "helpful", after.format(), 1);

    assertThat(page.items()).extracting(ExperienceItemView::postId).containsExactly(first.postId());
    assertThat(page.nextCursor()).isEqualTo(RankCursor.of(4, NOW, 1L).format());
    verify(reads, never()).pageByJournal(any(), any(), anyInt());
  }

  /// 两种排序都带期刊下的总条数。
  @Test
  void should_return_total_for_both_sorts() {
    when(reads.countByJournal(JOURNAL)).thenReturn(18L);

    assertThat(service(null).journalExperiences(JOURNAL_KEY, null, null, null).total())
        .isEqualTo(18L);
    assertThat(service(null).journalExperiences(JOURNAL_KEY, "helpful", null, null).total())
        .isEqualTo(18L);
  }

  /// 游标格式与排序对不上：422。
  @Test
  void should_reject_cursor_of_other_sort() {
    String rank = RankCursor.of(1, NOW, 1L).format();

    assertThatThrownBy(() -> service(null).journalExperiences(JOURNAL_KEY, "helpful", "9", null))
        .isInstanceOf(InvalidPostRequestException.class);
    assertThatThrownBy(() -> service(null).journalExperiences(JOURNAL_KEY, "latest", rank, null))
        .isInstanceOf(InvalidPostRequestException.class);
  }

  /// 近 3 年的起点是北京时间今天减 3 年；`all` 从最早能填的日期起。
  @Test
  void should_summarize_with_window_since() {
    when(reads.summarize(any(), any()))
        .thenReturn(ExperienceStats.builder().allCount(3).windowCount(2).build());

    assertThat(service(null).summary(JOURNAL_KEY, null).count()).isEqualTo(2);
    verify(reads).summarize(JOURNAL, LocalDate.parse("2023-10-27"));
    service(null).summary(JOURNAL_KEY, "all");
    verify(reads).summarize(JOURNAL, LocalDate.parse("1990-01-01"));
    assertThatThrownBy(() -> service(null).summary(JOURNAL_KEY, "1y"))
        .isInstanceOf(InvalidPostRequestException.class);
  }

  /// 详情：不存在 404；已删除只回删除标记与期刊。
  @Test
  void should_return_detail_or_deleted_marker() {
    when(posts.find(POST_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service(null).detail(POST_ID.value()))
        .isInstanceOf(PostNotFoundException.class);

    when(posts.find(POST_ID)).thenReturn(Optional.of(deleted(post(AUTHOR, false, NOW))));
    ExperienceDetailView detail = service(null).detail(POST_ID.value());
    assertThat(detail.deleted()).isTrue();
    assertThat(detail.experience()).isNull();
    assertThat(detail.journal().key()).isEqualTo(JOURNAL_KEY);
  }

  /// 修改历史：已删除 404 + SUBJECT_UNAVAILABLE；当前版本在前（成文时刻是最近一次记版本），旧版本随后。
  @Test
  void should_list_current_version_first() {
    PostAggregate post =
        post(AUTHOR, false, NOW).toBuilder().revisedAt(NOW.plus(Duration.ofHours(1))).build();
    PostRevision old = PostRevision.of(post.fields(), null, NOW, NOW.plus(Duration.ofHours(1)));
    when(posts.find(POST_ID)).thenReturn(Optional.of(post));
    when(reads.revisions(POST_ID)).thenReturn(List.of(old));

    List<RevisionVersionView> versions = service(null).revisions(POST_ID.value());

    assertThat(versions).extracting(RevisionVersionView::current).containsExactly(true, false);
    assertThat(versions.getFirst().writtenAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    assertThat(versions.getFirst().body()).isEqualTo("正文");
    assertThat(versions.get(1).writtenAt()).isEqualTo(NOW);

    when(posts.find(POST_ID)).thenReturn(Optional.of(deleted(post)));
    assertThatThrownBy(() -> service(null).revisions(POST_ID.value()))
        .isInstanceOfSatisfying(
            PostNotFoundException.class,
            ex -> assertThat(ex.getErrorTraits()).contains(SubjectErrorTrait.SUBJECT_UNAVAILABLE));
  }

  /// 本人看自己的：含匿名的，不问 identity 主页可见性。
  @Test
  void should_list_all_own_posts_including_anonymous() {
    when(reads.pageByAuthor(AUTHOR, true, null, 21)).thenReturn(List.of(post(AUTHOR, true, NOW)));

    CursorPage<UserExperienceView> page = service(AUTHOR).userExperiences(AUTHOR, null, null);

    assertThat(page.items()).hasSize(1);
    assertThat(page.items().getFirst().experience().anonymous()).isTrue();
    assertThat(page.items().getFirst().experience().mine()).isTrue();
    assertThat(page.items().getFirst().journal().title()).isEqualTo("Journal of X");
    verify(users, never()).findCards(Set.of(AUTHOR), AUTHOR);
  }

  /// 别人看：主页可见时只列署名的。
  @Test
  void should_list_only_named_posts_for_others() {
    when(users.findCards(List.of(AUTHOR), OTHER)).thenReturn(Map.of(AUTHOR, card(AUTHOR, true)));
    when(reads.pageByAuthor(AUTHOR, false, null, 21)).thenReturn(List.of(post(AUTHOR, false, NOW)));

    CursorPage<UserExperienceView> page = service(OTHER).userExperiences(AUTHOR, null, null);

    assertThat(page.items()).hasSize(1);
    assertThat(page.items().getFirst().experience().mine()).isFalse();
  }

  /// 别人看：主页对他不可见、或 identity 不认识这个人，空页，不查库。
  @Test
  void should_return_empty_when_profile_hidden_or_omitted() {
    when(users.findCards(List.of(AUTHOR), OTHER)).thenReturn(Map.of(AUTHOR, card(AUTHOR, false)));
    assertThat(service(OTHER).userExperiences(AUTHOR, null, null).items()).isEmpty();

    when(users.findCards(List.of(AUTHOR), null)).thenReturn(Map.of());
    assertThat(service(null).userExperiences(AUTHOR, null, null).items()).isEmpty();

    verify(reads, never()).pageByAuthor(anyLong(), anyBoolean(), any(), anyInt());
  }

  /// 以某个访客建服务。
  ///
  /// @param viewer 访客，匿名为 `null`
  /// @return 服务
  private ExperienceQueryService service(Long viewer) {
    CurrentUserPort currentUser =
        viewer == null ? TestUsers.anonymous() : TestUsers.signedIn(viewer);
    return new ExperienceQueryService(
        subjects,
        reads,
        posts,
        users,
        new ExperienceViews(users, subjects, reads),
        currentUser,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /// 换 ID。
  ///
  /// @param post 经验
  /// @param id ID
  /// @return 经验
  private static PostAggregate withId(PostAggregate post, long id) {
    return post.toBuilder().postId(PostId.of(id)).build();
  }

  /// 已删除的同一条。
  ///
  /// @param post 经验
  /// @return 已删除的经验
  private static PostAggregate deleted(PostAggregate post) {
    return post.toBuilder().status(PostStatus.DELETED).build();
  }
}

package dev.patra.interaction.app.post.service;

import static dev.patra.interaction.app.post.PostFixtures.JOURNAL;
import static dev.patra.interaction.app.post.PostFixtures.NOW;
import static dev.patra.interaction.app.post.PostFixtures.POST_ID;
import static dev.patra.interaction.app.post.PostFixtures.card;
import static dev.patra.interaction.app.post.PostFixtures.journalView;
import static dev.patra.interaction.app.post.PostFixtures.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.post.usecase.query.ExperienceDetailView;
import dev.patra.interaction.app.post.usecase.query.ExperienceItemView;
import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.enums.PostStatus;
import dev.patra.interaction.domain.post.model.read.PostStats;
import dev.patra.interaction.domain.post.port.read.PostReadPort;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 经验的视图与匿名遮蔽（spec 6.5）：匿名经验对别人不给作者；本人看自己的匿名经验有名片；主页不可见时去掉用户名与头像；
/// 每条带有帮助数、回复数与「我点过」（PAP-132）。
class ExperienceViewsTest {

  private static final long AUTHOR = 7L;
  private static final long OTHER = 8L;

  private final UserDirectoryPort users = mock(UserDirectoryPort.class);
  private final SubjectReadPort subjects = mock(SubjectReadPort.class);
  private final PostReadPort reads = mock(PostReadPort.class);
  private final ExperienceViews views = new ExperienceViews(users, subjects, reads);

  @BeforeEach
  void setUp() {
    when(subjects.findByIds(List.of(JOURNAL))).thenReturn(Map.of(JOURNAL, journalView()));
  }

  /// 别人看署名经验：有名片，不是本人写的。
  @Test
  void should_show_named_author_to_others() {
    when(users.findCards(Set.of(AUTHOR), OTHER)).thenReturn(Map.of(AUTHOR, card(AUTHOR, true)));

    ExperienceItemView item = views.items(List.of(post(AUTHOR, false, NOW)), OTHER).getFirst();

    assertThat(item.author().userId()).isEqualTo(AUTHOR);
    assertThat(item.author().username()).isEqualTo("user-7");
    assertThat(item.anonymous()).isFalse();
    assertThat(item.mine()).isFalse();
  }

  /// 别人（含匿名访客）看匿名经验：没有作者，也不为它调 identity。
  @Test
  void should_hide_anonymous_author_from_others() {
    List<PostAggregate> page = List.of(post(AUTHOR, true, NOW));

    ExperienceItemView forOther = views.items(page, OTHER).getFirst();
    ExperienceItemView forGuest = views.items(page, null).getFirst();

    assertThat(forOther.author()).isNull();
    assertThat(forOther.anonymous()).isTrue();
    assertThat(forOther.mine()).isFalse();
    assertThat(forGuest.author()).isNull();
    verifyNoInteractions(users);
  }

  /// 本人看自己的匿名经验：有自己的名片，标注匿名与本人。
  @Test
  void should_show_own_anonymous_post_to_author() {
    when(users.findCards(Set.of(AUTHOR), AUTHOR)).thenReturn(Map.of(AUTHOR, card(AUTHOR, true)));

    ExperienceItemView item = views.items(List.of(post(AUTHOR, true, NOW)), AUTHOR).getFirst();

    assertThat(item.author().userId()).isEqualTo(AUTHOR);
    assertThat(item.anonymous()).isTrue();
    assertThat(item.mine()).isTrue();
  }

  /// identity 省略了作者：没有作者、不算匿名（门户按「已注销用户」显示）。
  @Test
  void should_leave_author_empty_when_identity_omits_it() {
    when(users.findCards(anyCollection(), any())).thenReturn(Map.of());

    ExperienceItemView item = views.items(List.of(post(AUTHOR, false, NOW)), OTHER).getFirst();

    assertThat(item.author()).isNull();
    assertThat(item.anonymous()).isFalse();
  }

  /// 主页对访客不可见：不给用户名与头像，昵称、色号照给。
  @Test
  void should_strip_username_and_avatar_when_profile_hidden() {
    when(users.findCards(Set.of(AUTHOR), OTHER)).thenReturn(Map.of(AUTHOR, card(AUTHOR, false)));

    ExperienceItemView item = views.items(List.of(post(AUTHOR, false, NOW)), OTHER).getFirst();

    assertThat(item.author().username()).isNull();
    assertThat(item.author().avatarUrl()).isNull();
    assertThat(item.author().displayName()).isEqualTo("用户7");
    assertThat(item.author().avatarPalette()).isEqualTo(3);
    assertThat(item.author().profileVisible()).isFalse();
  }

  /// 一页里多条一次取名片。
  @Test
  void should_fetch_cards_once_per_page() {
    PostAggregate first = post(AUTHOR, false, NOW);
    PostAggregate second = post(OTHER, false, NOW);
    when(users.findCards(Set.of(AUTHOR, OTHER), null))
        .thenReturn(Map.of(AUTHOR, card(AUTHOR, true), OTHER, card(OTHER, true)));

    assertThat(views.items(List.of(first, second), null))
        .extracting(i -> i.author().userId())
        .containsExactly(AUTHOR, OTHER);
    verify(users).findCards(Set.of(AUTHOR, OTHER), null);
  }

  /// 已删除的详情只有删除标记与期刊。
  @Test
  void should_return_deleted_detail_with_journal_only() {
    PostAggregate deleted = post(AUTHOR, false, NOW).toBuilder().status(PostStatus.DELETED).build();

    ExperienceDetailView detail = views.detail(deleted, OTHER);

    assertThat(detail.deleted()).isTrue();
    assertThat(detail.experience()).isNull();
    assertThat(detail.journal().title()).isEqualTo("Journal of X");
    assertThat(detail.journal().abbreviation()).isEqualTo("J X");
    verifyNoInteractions(users);
  }

  /// 列表条目带一次批量取来的三项统计；匿名访客的「我点过」为 `null`。
  @Test
  void should_fill_stats_for_items() {
    when(users.findCards(anyCollection(), any())).thenReturn(Map.of(AUTHOR, card(AUTHOR, true)));
    when(reads.findStats(List.of(POST_ID), OTHER))
        .thenReturn(Map.of(POST_ID, PostStats.of(3, 2, true)));
    when(reads.findStats(List.of(POST_ID), null))
        .thenReturn(Map.of(POST_ID, PostStats.of(3, 2, null)));

    ExperienceItemView item = views.items(List.of(post(AUTHOR, false, NOW)), OTHER).getFirst();
    ExperienceItemView forGuest = views.items(List.of(post(AUTHOR, false, NOW)), null).getFirst();

    assertThat(item.helpfulCount()).isEqualTo(3);
    assertThat(item.replyCount()).isEqualTo(2);
    assertThat(item.helpful()).isTrue();
    assertThat(forGuest.helpful()).isNull();
  }

  /// 详情、作者刚写完的详情与主页条目都带统计。
  @Test
  void should_fill_stats_for_detail_and_authored() {
    PostAggregate post = post(AUTHOR, false, NOW);
    when(users.findCards(anyCollection(), any())).thenReturn(Map.of(AUTHOR, card(AUTHOR, true)));
    when(reads.findStats(List.of(POST_ID), OTHER))
        .thenReturn(Map.of(POST_ID, PostStats.of(5, 1, false)));
    when(reads.findStats(List.of(POST_ID), AUTHOR))
        .thenReturn(Map.of(POST_ID, PostStats.of(5, 1, false)));

    assertThat(views.detail(post, OTHER).experience().helpfulCount()).isEqualTo(5);
    assertThat(views.authored(post, card(AUTHOR, true)).experience().replyCount()).isEqualTo(1);
    assertThat(views.withoutAuthor(List.of(post), OTHER).getFirst().helpful()).isFalse();
  }
}

package dev.patra.interaction.app.discussion.usecase.query;

import static dev.patra.interaction.app.discussion.DiscussionFixture.POST_ID;
import static dev.patra.interaction.app.discussion.DiscussionFixture.PUB_ID;
import static dev.patra.interaction.app.discussion.DiscussionFixture.anonymous;
import static dev.patra.interaction.app.discussion.DiscussionFixture.card;
import static dev.patra.interaction.app.discussion.DiscussionFixture.post;
import static dev.patra.interaction.app.discussion.DiscussionFixture.publication;
import static dev.patra.interaction.app.discussion.DiscussionFixture.signedIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.discussion.service.DiscussionSubjects;
import dev.patra.interaction.domain.discussion.port.mention.MentionDirectoryPort;
import dev.patra.interaction.domain.discussion.port.read.DiscussionReadPort;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.CurrentUserPort;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// @ 联想（spec 6.3）：要登录；参与者在前（排除自己与匿名所有者，非匿名所有者排第一，只留正常、主页可见的），最多 6；
/// 有查询词时再并上 identity 的联想，合计最多 8。
class MentionSuggestionQueryServiceTest {

  private static final long VIEWER = 5L;

  private final SubjectReadPort subjectReads = mock(SubjectReadPort.class);
  private final DiscussionReadPort reads = mock(DiscussionReadPort.class);
  private final UserDirectoryPort users = mock(UserDirectoryPort.class);
  private final MentionDirectoryPort directory = mock(MentionDirectoryPort.class);
  private final Map<Long, UserCard> cards = new HashMap<>();

  @BeforeEach
  void setUp() {
    when(subjectReads.findIdsByKeys(SubjectType.PUBLICATION, List.of("100")))
        .thenReturn(Map.of("100", PUB_ID));
    when(subjectReads.findByIds(List.of(PUB_ID))).thenReturn(Map.of(PUB_ID, publication()));
    when(subjectReads.findIdsByKeys(SubjectType.POST, List.of("500")))
        .thenReturn(Map.of("500", POST_ID));
    when(users.findCards(anyCollection(), eq(VIEWER)))
        .thenAnswer(
            invocation -> {
              Collection<Long> ids = invocation.getArgument(0);
              return ids.stream()
                  .filter(cards::containsKey)
                  .collect(Collectors.toMap(id -> id, cards::get));
            });
  }

  /// 匿名访客不能用。
  @Test
  void should_require_login() {
    MentionSuggestionQueryService service =
        new MentionSuggestionQueryService(
            new DiscussionSubjects(subjectReads), reads, users, directory, anonymous());

    assertThatThrownBy(() -> service.suggest("publication", "100", "a"))
        .isInstanceOf(AuthenticationRequiredException.class);
  }

  /// 没有查询词：最近的参与者，去掉不可见与非正常的，最多 6 位，不调 identity 联想。
  @Test
  void should_list_recent_participants_for_empty_query() {
    List<Long> ids = List.of(11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L);
    ids.forEach(id -> cards.put(id, card(id)));
    cards.put(12L, card(12L, AccountState.ACTIVE, false));
    cards.put(13L, card(13L, AccountState.BANNED, false));
    when(reads.findRecentParticipants(PUB_ID, List.of(VIEWER), 50)).thenReturn(ids);

    List<SuggestionView> result = service().suggest("publication", "100", null);

    assertThat(result)
        .extracting(s -> s.user().userId())
        .containsExactly(11L, 14L, 15L, 16L, 17L, 18L);
    assertThat(result).allMatch(SuggestionView::participant);
    verify(directory, never()).suggest(anyString(), anyLong(), anyInt());
  }

  /// 匿名所有者被排除；非匿名所有者排第一（即使没评论过）。
  @Test
  void should_exclude_anonymous_owner_and_put_visible_owner_first() {
    cards.put(9L, card(9L));
    cards.put(11L, card(11L));
    when(subjectReads.findByIds(List.of(POST_ID))).thenReturn(Map.of(POST_ID, post(9L, true)));
    when(reads.findRecentParticipants(POST_ID, List.of(VIEWER, 9L), 50)).thenReturn(List.of(11L));
    assertThat(service().suggest("post", "500", ""))
        .extracting(s -> s.user().userId())
        .containsExactly(11L);

    when(subjectReads.findByIds(List.of(POST_ID))).thenReturn(Map.of(POST_ID, post(9L, false)));
    when(reads.findRecentParticipants(POST_ID, List.of(VIEWER, 9L), 50)).thenReturn(List.of(11L));
    assertThat(service().suggest("post", "500", ""))
        .extracting(s -> s.user().userId())
        .containsExactly(9L, 11L);
  }

  /// 有查询词：参与者里用户名或昵称（不分大小写）以它开头的在前，再补 identity 的结果，去重、合计不超过 8。
  @Test
  void should_match_participants_then_fill_from_identity() {
    cards.put(11L, card(11L));
    cards.put(12L, nicknamed(12L, "zs-12", "Zhang San"));
    cards.put(13L, card(13L));
    when(reads.findRecentParticipants(PUB_ID, List.of(VIEWER), 50))
        .thenReturn(List.of(11L, 12L, 13L));
    when(directory.suggest("u1", VIEWER, 10))
        .thenReturn(
            List.of(
                card(11L),
                card(101L),
                card(102L),
                card(103L),
                card(104L),
                card(105L),
                card(106L),
                card(107L)));

    List<SuggestionView> byName = service().suggest("publication", "100", "u1");
    assertThat(byName)
        .extracting(s -> s.user().userId())
        .containsExactly(11L, 13L, 101L, 102L, 103L, 104L, 105L, 106L);
    assertThat(byName)
        .extracting(SuggestionView::participant)
        .containsExactly(true, true, false, false, false, false, false, false);

    when(directory.suggest("zhang", VIEWER, 9)).thenReturn(List.of());
    assertThat(service().suggest("publication", "100", "zhang"))
        .extracting(s -> s.user().userId())
        .containsExactly(12L);
  }

  /// 对象还没登记：只有 identity 的结果。
  @Test
  void should_skip_participants_for_unregistered_subject() {
    when(subjectReads.findIdsByKeys(SubjectType.PUBLICATION, List.of("100"))).thenReturn(Map.of());
    when(directory.suggest("bo", VIEWER, 8)).thenReturn(List.of(card(101L)));

    assertThat(service().suggest("publication", "100", "bo"))
        .extracting(s -> s.user().userId())
        .containsExactly(101L);
    verify(reads, never()).findRecentParticipants(PUB_ID, List.of(VIEWER), 50);
  }

  /// 已登录的服务。
  ///
  /// @return 服务
  private MentionSuggestionQueryService service() {
    CurrentUserPort currentUser = signedIn(VIEWER);
    return new MentionSuggestionQueryService(
        new DiscussionSubjects(subjectReads), reads, users, directory, currentUser);
  }

  /// 设了昵称的名片。
  ///
  /// @param id 用户 ID
  /// @param username 用户名
  /// @param displayName 昵称
  /// @return 名片
  private static UserCard nicknamed(long id, String username, String displayName) {
    UserCard base = card(id);
    return UserCard.builder()
        .userId(id)
        .username(username)
        .displayName(displayName)
        .avatarUrl(base.avatarUrl())
        .avatarPalette(base.avatarPalette())
        .state(AccountState.ACTIVE)
        .emailVerified(true)
        .profileVisible(true)
        .build();
  }
}

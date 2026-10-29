package dev.patra.interaction.app.notification.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.notification.exception.InvalidNotificationRequestException;
import dev.patra.interaction.domain.notification.model.enums.NotificationTab;
import dev.patra.interaction.domain.notification.model.enums.NotificationType;
import dev.patra.interaction.domain.notification.model.read.NotificationEntry;
import dev.patra.interaction.domain.notification.model.vo.ExcerptText;
import dev.patra.interaction.domain.notification.port.read.NotificationReadPort;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// 提醒的查询（spec 7.2）：匿名遮蔽、对象上下文、摘录渲染与隐藏、名片分批、游标。
class NotificationQueryServiceTest {

  private static final long R = 1L;
  private static final SubjectId PAPER = SubjectId.of(100L);
  private static final SubjectId POST = SubjectId.of(200L);

  private final NotificationReadPort reads = mock(NotificationReadPort.class);
  private final SubjectReadPort subjects = mock(SubjectReadPort.class);
  private final UserDirectoryPort users = mock(UserDirectoryPort.class);
  private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
  private final NotificationQueryService service =
      new NotificationQueryService(reads, subjects, users, currentUser);

  private final Map<SubjectId, SubjectView> subjectViews = new HashMap<>();

  @BeforeEach
  void world() {
    CurrentUser user = CurrentUser.of(R, 1L, AccountType.USER, ClientType.WEB);
    when(currentUser.current()).thenReturn(Optional.of(user));
    when(currentUser.require()).thenReturn(user);
    subjectViews.put(PAPER, paper(SubjectState.ACTIVE));
    when(subjects.findByIds(anyCollection())).thenReturn(subjectViews);
    when(users.findCards(anyCollection(), eq(R)))
        .thenAnswer(
            inv -> {
              Map<Long, UserCard> cards = new HashMap<>();
              for (Long id : inv.<Collection<Long>>getArgument(0)) {
                cards.put(id, card(id));
              }
              return cards;
            });
  }

  /// 匿名所有者触发的：没有触发人，也不拿他的 ID 去取名片（这一页没有别的人，名片一张都不取）。
  @Test
  void should_mask_anonymous_actor() {
    page(entry(10L, 77L, true, PAPER, ExcerptText.builder().literal("hi").build()));

    NotificationView view = service.page(null, null).items().getFirst();

    assertThat(view.actor()).isNull();
    assertThat(view.actorAnonymous()).isTrue();
    verifyNoInteractions(users);
  }

  /// 对象已不可用：不给摘录，触发人照常。
  @Test
  void should_hide_excerpt_when_subject_unavailable() {
    subjectViews.put(PAPER, paper(SubjectState.UNAVAILABLE));
    page(entry(10L, 5L, false, PAPER, ExcerptText.builder().literal("秘密").build()));

    NotificationView view = service.page(null, null).items().getFirst();

    assertThat(view.excerpt()).isNull();
    assertThat(view.subject().available()).isFalse();
    assertThat(view.actor().userId()).isEqualTo(5L);
  }

  /// 收件人自己的经验：期刊缩写、投稿月份、「你的」。
  @Test
  void should_build_post_context_for_owner() {
    subjectViews.put(
        POST,
        new SubjectView(
            POST,
            SubjectType.POST,
            "5001",
            SubjectState.ACTIVE,
            SubjectSnapshot.of(null, "Journal of Tests", "2026-03", "J Tests"),
            SubjectOwner.of(R, true)));
    page(entry(10L, 5L, false, POST, ExcerptText.builder().literal("问").build()));

    SubjectRefView subject = service.page(null, null).items().getFirst().subject();

    assertThat(subject.type()).isEqualTo(SubjectType.POST);
    assertThat(subject.key()).isEqualTo("5001");
    assertThat(subject.mine()).isTrue();
    assertThat(subject.journal()).isEqualTo("Journal of Tests");
    assertThat(subject.abbreviation()).isEqualTo("J Tests");
    assertThat(subject.submittedMonth()).isEqualTo("2026-03");
    assertThat(subject.title()).isNull();
  }

  /// 摘录里的提及换成用户名。
  @Test
  void should_render_mentions_in_excerpt() {
    page(entry(10L, 5L, false, PAPER, ExcerptText.builder().literal("问 ").mention(6).build()));

    NotificationView view = service.page(null, null).items().getFirst();

    assertThat(view.excerpt()).isEqualTo("问 @user-6");
    assertThat(view.excerptTruncated()).isFalse();
    assertThat(view.subject().title()).isEqualTo("论文");
  }

  /// 一页的触发人与提及超过 100 人：分批取名片。
  @Test
  void should_fetch_cards_in_batches_of_100() {
    List<NotificationEntry> entries = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      ExcerptText.Builder excerpt = ExcerptText.builder();
      for (int m = 0; m < 6; m++) {
        excerpt.mention(10_000L + i * 10L + m);
      }
      entries.add(entry(100L - i, 1_000L + i, false, PAPER, excerpt.build()));
    }
    when(reads.page(eq(R), any(), any(), anyInt())).thenReturn(entries);

    service.page(null, null);

    ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.captor();
    verify(users, times(2)).findCards(ids.capture(), eq(R));
    assertThat(ids.getAllValues())
        .allSatisfy(batch -> assertThat(batch).hasSizeLessThanOrEqualTo(100));
    assertThat(ids.getAllValues().stream().mapToInt(Collection::size).sum()).isEqualTo(140);
  }

  /// 多取到一条就有下一页，游标是本页最后一条的 ID。
  @Test
  void should_return_next_cursor_when_more() {
    List<NotificationEntry> entries = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      entries.add(entry(100L - i, 5L, false, PAPER, ExcerptText.builder().literal("x").build()));
    }
    when(reads.page(R, NotificationTab.MENTIONS, 500L, 21)).thenReturn(entries);

    NotificationPage page = service.page("mentions", "500");

    assertThat(page.items()).hasSize(20);
    assertThat(page.nextCursor()).isEqualTo("81");

    when(reads.page(R, NotificationTab.REPLIES, null, 21)).thenReturn(entries.subList(0, 20));
    assertThat(service.page(null, null).nextCursor()).isNull();
  }

  /// 游标写法不对：422。
  @Test
  void should_reject_bad_cursor() {
    assertThatThrownBy(() -> service.page(null, "abc"))
        .isInstanceOf(InvalidNotificationRequestException.class);
  }

  /// 读端口返回这一页。
  ///
  /// @param entries 提醒
  private void page(NotificationEntry... entries) {
    when(reads.page(eq(R), any(), any(), anyInt())).thenReturn(List.of(entries));
  }

  /// 一条提醒。
  ///
  /// @param id 提醒 ID
  /// @param actor 触发人
  /// @param anonymous 是否匿名所有者触发
  /// @param subject 对象
  /// @param excerpt 摘录
  /// @return 提醒
  private static NotificationEntry entry(
      long id, long actor, boolean anonymous, SubjectId subject, ExcerptText excerpt) {
    return NotificationEntry.builder()
        .notificationId(id)
        .type(NotificationType.COMMENT_REPLY)
        .actorId(actor)
        .actorAnonymous(anonymous)
        .subjectId(subject)
        .commentId(900L + id)
        .excerpt(excerpt)
        .createdAt(Instant.parse("2026-10-27T01:00:00Z"))
        .build();
  }

  /// 文献对象。
  ///
  /// @param state 状态
  /// @return 视图
  private static SubjectView paper(SubjectState state) {
    return new SubjectView(
        PAPER,
        SubjectType.PUBLICATION,
        "123",
        state,
        SubjectSnapshot.of("论文", "J", "2019", null),
        null);
  }

  /// 主页公开的名片，用户名 `user-{id}`。
  ///
  /// @param id 用户
  /// @return 名片
  private static UserCard card(long id) {
    return UserCard.builder()
        .userId(id)
        .username("user-" + id)
        .displayName("用户" + id)
        .state(AccountState.ACTIVE)
        .emailVerified(true)
        .profileVisible(true)
        .build();
  }
}

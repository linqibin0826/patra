package dev.patra.notification.app.inbox.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.inbox.exception.InvalidInboxRequestException;
import dev.patra.notification.domain.inbox.model.read.InboxEntry;
import dev.patra.notification.domain.inbox.model.read.NotificationType;
import dev.patra.notification.domain.inbox.model.vo.InboxCursor;
import dev.patra.notification.domain.inbox.port.read.InboxReadPort;
import dev.patra.notification.domain.kernel.exception.IdentityUnavailableException;
import dev.patra.notification.domain.kernel.exception.InteractionUnavailableException;
import dev.patra.notification.domain.kernel.model.enums.AccountState;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.kernel.model.enums.SubjectType;
import dev.patra.notification.domain.kernel.model.read.ExperienceOutline;
import dev.patra.notification.domain.kernel.model.read.SubjectView;
import dev.patra.notification.domain.kernel.model.read.UserCard;
import dev.patra.notification.domain.kernel.port.identity.UserCardPort;
import dev.patra.notification.domain.kernel.port.interaction.SubjectViewPort;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 提醒列表：读一页，读时拼名片、对象视图与摘录。
class InboxQueryServiceTest {

  private static final long ME = 2L;
  private static final long ACTOR = 9L;
  private static final long MENTIONED = 4L;
  private static final Instant AT = Instant.parse("2026-10-31T02:15:30Z");

  private final InboxReadPort reads = mock(InboxReadPort.class);
  private final SubjectViewPort subjects = mock(SubjectViewPort.class);
  private final UserCardPort users = mock(UserCardPort.class);
  private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
  private final InboxQueryService query =
      new InboxQueryService(reads, subjects, users, currentUser);

  /// 当前用户是 2。
  @BeforeEach
  void signIn() {
    when(currentUser.require())
        .thenReturn(CurrentUser.of(ME, 1L, AccountType.USER, ClientType.WEB));
  }

  /// 一行回复：触发人名片、经验对象（作者是我 → mine）、换了名字的摘录。
  @Test
  void should_render_page_with_cards_and_subjects() {
    when(reads.page(ME, NotificationCategory.REPLY, null, 21))
        .thenReturn(List.of(entry(11L, NotificationType.COMMENT_REPLY, false, "谢谢 <@4>")));
    when(subjects.findViews(List.of(40L))).thenReturn(Map.of(40L, post(40L, true)));
    when(users.findCards(List.of(ACTOR, MENTIONED), ME))
        .thenReturn(
            Map.of(ACTOR, card(ACTOR, "wang", true), MENTIONED, card(MENTIONED, "zhou", false)));

    NotificationPage page = query.page(null, null);

    assertThat(page.nextCursor()).isNull();
    NotificationView view = page.items().getFirst();
    assertThat(view.notificationId()).isEqualTo(11L);
    assertThat(view.type()).isEqualTo(NotificationType.COMMENT_REPLY);
    assertThat(view.read()).isFalse();
    assertThat(view.occurredAt()).isEqualTo(AT);
    assertThat(view.actor().username()).isEqualTo("wang");
    assertThat(view.commentId()).isEqualTo(500L);
    assertThat(view.excerpt()).isEqualTo("谢谢 @周敏");
    assertThat(view.excerptTruncated()).isFalse();
    assertThat(view.subject())
        .isEqualTo(
            SubjectRefView.builder()
                .type(SubjectType.POST)
                .key("55")
                .available(true)
                .journal("The Lancet")
                .abbreviation("Lancet")
                .submittedMonth("2026-08")
                .mine(true)
                .experience(ExperienceOutline.of("MAJOR_REVISION", "ACCEPTED", 41, 96))
                .build());
  }

  /// 文献对象：标题给出，经验字段为空；摘录 120 字素、单行。
  @Test
  void should_render_publication_and_cut_excerpt() {
    when(reads.page(ME, NotificationCategory.MENTION, null, 21))
        .thenReturn(List.of(entry(11L, NotificationType.MENTION, false, "字".repeat(130))));
    when(subjects.findViews(any())).thenReturn(Map.of(40L, publication(40L)));
    when(users.findCards(any(), eq(ME))).thenReturn(Map.of(ACTOR, card(ACTOR, "wang", true)));

    NotificationView view = query.page("mentions", null).items().getFirst();

    assertThat(view.subject().title()).isEqualTo("标题");
    assertThat(view.subject().journal()).isNull();
    assertThat(view.subject().submittedMonth()).isNull();
    assertThat(view.subject().mine()).isFalse();
    assertThat(view.excerpt()).hasSize(120);
    assertThat(view.excerptTruncated()).isTrue();
  }

  /// 匿名作者触发的：不查他的名片、不给 actor。
  @Test
  void should_hide_actor_when_anonymous() {
    when(reads.page(ME, NotificationCategory.REPLY, null, 21))
        .thenReturn(List.of(entry(11L, NotificationType.POST_COMMENT, true, "正文")));
    when(subjects.findViews(any())).thenReturn(Map.of(40L, post(40L, true)));

    NotificationView view = query.page("replies", null).items().getFirst();

    assertThat(view.actor()).isNull();
    assertThat(view.actorAnonymous()).isTrue();
    verifyNoInteractions(users);
  }

  /// interaction 挂了：对象与摘录都为空，不报错。
  @Test
  void should_degrade_subject_when_interaction_unavailable() {
    when(reads.page(ME, NotificationCategory.REPLY, null, 21))
        .thenReturn(List.of(entry(11L, NotificationType.COMMENT_REPLY, false, "正文")));
    when(subjects.findViews(any()))
        .thenThrow(new InteractionUnavailableException(new IllegalStateException("down")));
    when(users.findCards(any(), eq(ME))).thenReturn(Map.of());

    NotificationView view = query.page(null, null).items().getFirst();

    assertThat(view.subject()).isNull();
    assertThat(view.excerpt()).isNull();
  }

  /// 文献已下线：对象照给（available 为假），摘录不给。
  @Test
  void should_hide_excerpt_when_subject_unavailable() {
    when(reads.page(ME, NotificationCategory.REPLY, null, 21))
        .thenReturn(List.of(entry(11L, NotificationType.COMMENT_REPLY, false, "正文")));
    SubjectView offline =
        SubjectView.builder().subjectId(40L).type(SubjectType.PUBLICATION).available(false).build();
    when(subjects.findViews(any())).thenReturn(Map.of(40L, offline));
    when(users.findCards(any(), eq(ME))).thenReturn(Map.of());

    NotificationView view = query.page(null, null).items().getFirst();

    assertThat(view.subject().available()).isFalse();
    assertThat(view.excerpt()).isNull();
  }

  /// 正文只有 Markdown 结构时纯文本是空串（PAP-141 契约修订 4）：不给摘录，不出空字符串。
  @Test
  void should_hide_blank_excerpt() {
    when(reads.page(ME, NotificationCategory.REPLY, null, 21))
        .thenReturn(List.of(entry(11L, NotificationType.COMMENT_REPLY, true, " ")));
    when(subjects.findViews(any())).thenReturn(Map.of(40L, publication(40L)));

    NotificationView view = query.page(null, null).items().getFirst();

    assertThat(view.excerpt()).isNull();
    assertThat(view.excerptTruncated()).isFalse();
  }

  /// identity 挂了：没有名字的提醒看不懂，整页 503。
  @Test
  void should_propagate_identity_failure() {
    when(reads.page(ME, NotificationCategory.REPLY, null, 21))
        .thenReturn(List.of(entry(11L, NotificationType.COMMENT_REPLY, false, "正文")));
    when(subjects.findViews(any())).thenReturn(Map.of());
    when(users.findCards(any(), eq(ME)))
        .thenThrow(new IdentityUnavailableException(new IllegalStateException("down")));

    assertThatThrownBy(() -> query.page(null, null))
        .isInstanceOf(IdentityUnavailableException.class);
  }

  /// 同一对象、同一触发人的多行：对象视图与名片各查一次，参数去重。
  @Test
  void should_query_each_subject_and_user_once() {
    when(reads.page(ME, NotificationCategory.REPLY, null, 21))
        .thenReturn(
            List.of(
                entry(12L, NotificationType.COMMENT_REPLY, false, "a"),
                entry(11L, NotificationType.COMMENT_REPLY, false, "b")));
    when(subjects.findViews(any())).thenReturn(Map.of());
    when(users.findCards(any(), eq(ME))).thenReturn(Map.of());

    query.page(null, null);

    verify(subjects).findViews(List.of(40L));
    verify(users).findCards(List.of(ACTOR), ME);
  }

  /// 名片超过 100 个按批取。
  @Test
  void should_batch_cards_by_100() {
    String mentions =
        String.join(" ", LongStream.rangeClosed(100, 220).mapToObj(id -> "<@" + id + ">").toList());
    when(reads.page(ME, NotificationCategory.REPLY, null, 21))
        .thenReturn(List.of(entry(11L, NotificationType.COMMENT_REPLY, false, mentions)));
    when(subjects.findViews(any())).thenReturn(Map.of(40L, publication(40L)));
    when(users.findCards(any(), eq(ME))).thenReturn(Map.of());

    query.page(null, null);

    verify(users, times(2)).findCards(any(), eq(ME));
  }

  /// 读到 21 行：给 20 条，游标指向第 20 条；不满 21 行没有游标。
  @Test
  void should_return_next_cursor_only_when_more() {
    List<InboxEntry> rows = new ArrayList<>();
    for (long id = 40; id > 19; id--) {
      rows.add(entry(id, NotificationType.COMMENT_REPLY, true, null));
    }
    when(reads.page(eq(ME), eq(NotificationCategory.REPLY), any(), anyInt())).thenReturn(rows);
    when(subjects.findViews(any())).thenReturn(Map.of());

    NotificationPage page = query.page(null, null);

    assertThat(page.items()).hasSize(20);
    assertThat(page.nextCursor()).isEqualTo(InboxCursor.of(AT, 21L).encode());
  }

  /// 游标原样解析后交给读端口。
  @Test
  void should_pass_cursor_to_read_port() {
    when(reads.page(anyLong(), any(), any(), anyInt())).thenReturn(List.of());

    query.page("replies", InboxCursor.of(AT, 7L).encode());

    verify(reads).page(ME, NotificationCategory.REPLY, InboxCursor.of(AT, 7L), 21);
    verifyNoInteractions(subjects, users);
  }

  /// 标签、游标不合法：422。
  @Test
  void should_reject_bad_tab_and_cursor() {
    assertThatThrownBy(() -> query.page("likes", null))
        .isInstanceOf(InvalidInboxRequestException.class);
    assertThatThrownBy(() -> query.page(null, "!!"))
        .isInstanceOf(InvalidInboxRequestException.class);
  }

  /// 一行：对象 40，评论 500，触发人 9。
  ///
  /// @param id 提醒 ID
  /// @param type 类型
  /// @param anonymous 触发人匿名
  /// @param plainText 评论正文
  /// @return 行
  private static InboxEntry entry(
      long id, NotificationType type, boolean anonymous, String plainText) {
    return InboxEntry.builder()
        .notificationId(id)
        .type(type)
        .actorId(ACTOR)
        .actorAnonymous(anonymous)
        .subjectId(40L)
        .commentId(500L)
        .plainText(plainText)
        .occurredAt(AT)
        .build();
  }

  /// 一条经验（作者是我）。
  ///
  /// @param subjectId 对象
  /// @param available 是否还在
  /// @return 视图
  private static SubjectView post(long subjectId, boolean available) {
    return SubjectView.builder()
        .subjectId(subjectId)
        .type(SubjectType.POST)
        .key("55")
        .available(available)
        .journal("The Lancet")
        .abbreviation("Lancet")
        .publishedDate("2026-08")
        .ownerId(ME)
        .experience(ExperienceOutline.of("MAJOR_REVISION", "ACCEPTED", 41, 96))
        .build();
  }

  /// 一篇文献。
  ///
  /// @param subjectId 对象
  /// @return 视图
  private static SubjectView publication(long subjectId) {
    return SubjectView.builder()
        .subjectId(subjectId)
        .type(SubjectType.PUBLICATION)
        .key("18")
        .available(true)
        .title("标题")
        .journal("Nature")
        .publishedDate("2019-03")
        .build();
  }

  /// 名片。
  ///
  /// @param userId 用户
  /// @param username 用户名
  /// @param visible 主页可见
  /// @return 名片
  private static UserCard card(long userId, String username, boolean visible) {
    return UserCard.builder()
        .userId(userId)
        .username(username)
        .displayName(userId == MENTIONED ? "周敏" : "王")
        .state(AccountState.ACTIVE)
        .profileVisible(visible)
        .build();
  }
}

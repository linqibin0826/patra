package dev.patra.notification.app.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.notification.app.mail.service.AssembledReminder.Ready;
import dev.patra.notification.app.mail.service.AssembledReminder.Skip;
import dev.patra.notification.domain.inbox.model.read.InboxEntry;
import dev.patra.notification.domain.inbox.model.read.NotificationType;
import dev.patra.notification.domain.inbox.model.read.UnreadWindow;
import dev.patra.notification.domain.inbox.port.read.InboxReadPort;
import dev.patra.notification.domain.kernel.model.enums.AccountState;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.kernel.model.enums.SubjectType;
import dev.patra.notification.domain.kernel.model.read.Recipient;
import dev.patra.notification.domain.kernel.model.read.SegmentedExcerpt.Segment;
import dev.patra.notification.domain.kernel.model.read.SubjectView;
import dev.patra.notification.domain.kernel.model.read.UserCard;
import dev.patra.notification.domain.kernel.port.gateway.PresenceGateway;
import dev.patra.notification.domain.kernel.port.identity.RecipientPort;
import dev.patra.notification.domain.kernel.port.identity.UserCardPort;
import dev.patra.notification.domain.kernel.port.interaction.SubjectViewPort;
import dev.patra.notification.domain.mail.model.aggregate.ReminderDelivery;
import dev.patra.notification.domain.mail.model.enums.SkipReason;
import dev.patra.notification.domain.mail.model.vo.content.ReminderMailContent;
import dev.patra.notification.domain.mail.model.vo.content.ReminderMailContent.Layout;
import dev.patra.notification.domain.mail.policy.ReminderLinks;
import dev.patra.notification.domain.mail.policy.ReminderMailComposer;
import dev.patra.notification.domain.mail.policy.ReminderTimeText;
import dev.patra.notification.domain.preference.model.read.EmailMode;
import dev.patra.notification.domain.preference.model.read.EmailModes;
import dev.patra.notification.domain.preference.model.read.EmailSubscription;
import dev.patra.notification.domain.preference.port.read.EmailSubscriptionReadPort;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// 装配一封提醒邮件：检查顺序与跳过原因（spec 5.3、7.3）、名字（8.1）、落点。
class ReminderDeliveryAssemblerTest {

  private static final long ME = 7L;
  private static final Instant CREATED = Instant.parse("2026-10-30T07:02:00Z");
  private static final Instant NOW = CREATED.plus(Duration.ofMinutes(10));
  private static final String TOKEN = "T".repeat(43);

  private final InboxReadPort inbox = mock(InboxReadPort.class);
  private final PresenceGateway presence = mock(PresenceGateway.class);
  private final EmailSubscriptionReadPort subscriptions = mock(EmailSubscriptionReadPort.class);
  private final RecipientPort recipients = mock(RecipientPort.class);
  private final SubjectViewPort subjects = mock(SubjectViewPort.class);
  private final UserCardPort cards = mock(UserCardPort.class);
  private final ReminderDeliveryAssembler assembler =
      new ReminderDeliveryAssembler(
          inbox,
          presence,
          subscriptions,
          recipients,
          subjects,
          cards,
          new ReminderMailComposer(
              ReminderLinks.of("https://patra.linqibin.dev"),
              ReminderTimeText.of(ZoneId.of("Asia/Shanghai"))));

  /// 默认一切正常：两类都是逐条、收件人正常且已验证、对象与名片都查得到。
  @BeforeEach
  void happyPath() {
    when(subscriptions.findSubscription(ME)).thenReturn(EmailModes.DEFAULT);
    when(subscriptions.subscriptionOf(ME))
        .thenReturn(EmailSubscription.of(ME, EmailMode.INSTANT, EmailMode.INSTANT, TOKEN));
    when(recipients.find(ME)).thenReturn(Optional.of(recipient(AccountState.ACTIVE, true)));
    when(subjects.findViews(anyCollection())).thenReturn(Map.of(40L, paper(40L), 41L, myPost(41L)));
    when(cards.findCards(anyCollection(), eq(ME)))
        .thenReturn(
            Map.of(
                9L, card(9L, "王立", AccountState.ACTIVE), ME, card(ME, "陈宇", AccountState.ACTIVE)));
  }

  /// 全都已读：跳过，不调 identity。
  @Test
  void instant_all_read_skips_without_calling_identity() {
    when(inbox.findUnread(eq(ME), anyCollection())).thenReturn(List.of());

    assertThat(assembler.assemble(instant(), NOW)).isEqualTo(Skip.of(SkipReason.NOTHING_UNREAD));
    verifyNoInteractions(recipients, subjects, cards);
  }

  /// 出现之后上过站的条目去掉；全去掉就跳过。
  @Test
  void instant_seen_after_created_drops_those_entries() {
    when(inbox.findUnread(eq(ME), anyCollection()))
        .thenReturn(List.of(entry(1L, NotificationType.COMMENT_REPLY, 9L, false, 40L, "好")));
    when(presence.lastSeen(List.of(ME))).thenReturn(Map.of(ME, CREATED.plusSeconds(1)));

    assertThat(assembler.assemble(instant(), NOW)).isEqualTo(Skip.of(SkipReason.NOTHING_UNREAD));
    verifyNoInteractions(recipients);
  }

  /// 档位已经不是逐条：跳过。
  @Test
  void mode_not_instant_skips() {
    unread(entry(1L, NotificationType.COMMENT_REPLY, 9L, false, 40L, "好"));
    when(subscriptions.findSubscription(ME))
        .thenReturn(EmailModes.of(EmailMode.DIGEST, EmailMode.INSTANT));

    assertThat(assembler.assemble(instant(), NOW)).isEqualTo(Skip.of(SkipReason.MODE));
    verifyNoInteractions(recipients);
  }

  /// 收件人不该收：identity 不认识、不是正常状态、邮箱未验证或为空。只有 ACTIVE 且已验证才发；跳过时不取令牌。
  @ParameterizedTest
  @EnumSource(
      value = AccountState.class,
      names = {"BANNED", "PENDING_DELETION", "DELETED"})
  void recipient_not_active_skips_without_token(AccountState state) {
    unread(entry(1L, NotificationType.COMMENT_REPLY, 9L, false, 40L, "好"));
    when(recipients.find(ME)).thenReturn(Optional.of(recipient(state, true)));

    assertThat(assembler.assemble(instant(), NOW)).isEqualTo(Skip.of(SkipReason.RECIPIENT));
    verify(subscriptions, never()).subscriptionOf(anyLong());
  }

  /// 邮箱未验证、identity 不认识：跳过。
  @Test
  void unverified_or_unknown_recipient_skips() {
    unread(entry(1L, NotificationType.COMMENT_REPLY, 9L, false, 40L, "好"));
    when(recipients.find(ME)).thenReturn(Optional.of(recipient(AccountState.ACTIVE, false)));
    assertThat(assembler.assemble(instant(), NOW)).isEqualTo(Skip.of(SkipReason.RECIPIENT));

    when(recipients.find(ME)).thenReturn(Optional.empty());
    assertThat(assembler.assemble(instant(), NOW)).isEqualTo(Skip.of(SkipReason.RECIPIENT));
  }

  /// 对象查不到或没有 key 的条目去掉；都去掉就跳过。
  @Test
  void items_without_subject_or_key_are_dropped_and_all_dropped_skips() {
    unread(entry(1L, NotificationType.COMMENT_REPLY, 9L, false, 99L, "好"));

    assertThat(assembler.assemble(instant(), NOW)).isEqualTo(Skip.of(SkipReason.NOTHING_UNREAD));
  }

  /// 一切正常：收件地址与单条版式的内容；名字是显示名，`<@ID>` 按 MentionNames。
  @Test
  void instant_ready_with_names() {
    unread(entry(1L, NotificationType.COMMENT_REPLY, 9L, false, 40L, "<@7> 你看"));

    AssembledReminder result = assembler.assemble(instant(), NOW);

    assertThat(result).isInstanceOf(Ready.class);
    Ready ready = (Ready) result;
    assertThat(ready.to()).isEqualTo("chen@example.com");
    ReminderMailContent content = ready.content();
    assertThat(content.layout()).isEqualTo(Layout.SINGLE);
    assertThat(content.subject()).isEqualTo("王立 回复了你的评论");
    assertThat(content.single().excerpt()).first().isEqualTo(Segment.of("@chenyu", true));
    assertThat(content.oneClickUrl()).contains("token=" + TOKEN);
  }

  /// 匿名作者、已注销的触发人。
  @Test
  void actor_names_anonymous_and_deleted() {
    unread(
        entry(1L, NotificationType.COMMENT_REPLY, 9L, true, 40L, "甲"),
        entry(2L, NotificationType.COMMENT_REPLY, 8L, false, 40L, "乙"));

    Ready ready = (Ready) assembler.assemble(instant(), NOW);

    assertThat(ready.content().subject()).isEqualTo("2 条新回复：匿名作者、已注销用户");
  }

  /// 「你的投稿经验」按对象作者是不是收件人判断。
  @Test
  void mine_is_owner_equals_recipient() {
    unread(entry(1L, NotificationType.POST_COMMENT, 9L, false, 41L, "好"));

    Ready ready = (Ready) assembler.assemble(instant(), NOW);

    assertThat(ready.content().single().place().label()).isEqualTo("你的投稿经验");
  }

  /// 汇总：只装配设为每日汇总的类别，按窗口取。
  @Test
  void digest_uses_window_and_digest_categories() {
    when(subscriptions.findSubscription(ME))
        .thenReturn(EmailModes.of(EmailMode.INSTANT, EmailMode.DIGEST));
    Instant windowEnd = Instant.parse("2026-10-31T00:00:00Z");
    when(inbox.unreadBetween(
            ME, NotificationCategory.MENTION, windowEnd.minus(Duration.ofHours(24)), windowEnd, 10))
        .thenReturn(
            UnreadWindow.of(
                1, List.of(entry(1L, NotificationType.MENTION, 9L, false, 40L, "<@7> 看"))));

    Ready ready =
        (Ready)
            assembler.assemble(ReminderDelivery.digest(UUID.randomUUID(), ME, windowEnd, NOW), NOW);

    assertThat(ready.content().layout()).isEqualTo(Layout.DIGEST);
    assertThat(ready.content().subject()).isEqualTo("每日汇总：1 条未读的 @（10 月 31 日）");
    verify(inbox, never())
        .unreadBetween(anyLong(), eq(NotificationCategory.REPLY), any(), any(), any(Integer.class));
  }

  /// 汇总：没有设为每日汇总的类别了，跳过；窗口里没有未读，跳过。
  @Test
  void digest_without_categories_or_unread_skips() {
    ReminderDelivery digest = ReminderDelivery.digest(UUID.randomUUID(), ME, NOW, NOW);
    assertThat(assembler.assemble(digest, NOW)).isEqualTo(Skip.of(SkipReason.MODE));

    when(subscriptions.findSubscription(ME))
        .thenReturn(EmailModes.of(EmailMode.DIGEST, EmailMode.DIGEST));
    when(inbox.unreadBetween(anyLong(), any(), any(), any(), any(Integer.class)))
        .thenReturn(UnreadWindow.of(0, List.of()));
    assertThat(assembler.assemble(digest, NOW)).isEqualTo(Skip.of(SkipReason.NOTHING_UNREAD));
    verifyNoInteractions(recipients);
  }

  /// 收件人这些提醒仍未读。
  ///
  /// @param entries entries
  private void unread(InboxEntry... entries) {
    when(inbox.findUnread(eq(ME), anyCollection())).thenReturn(List.of(entries));
  }

  /// 一条两条提醒的逐条投递。
  ///
  /// @return 结果
  private static ReminderDelivery instant() {
    return ReminderDelivery.instant(
        UUID.randomUUID(), ME, NotificationCategory.REPLY, List.of(1L, 2L), NOW);
  }

  /// 信箱里的一条提醒。
  ///
  /// @param id id
  /// @param type type
  /// @param actorId actorId
  /// @param anonymous anonymous
  /// @param subjectId subjectId
  /// @param text text
  /// @return 结果
  private static InboxEntry entry(
      long id,
      NotificationType type,
      long actorId,
      boolean anonymous,
      long subjectId,
      String text) {
    return InboxEntry.builder()
        .notificationId(id)
        .type(type)
        .actorId(actorId)
        .actorAnonymous(anonymous)
        .subjectId(subjectId)
        .commentId(500L + id)
        .plainText(text)
        .occurredAt(CREATED.plusSeconds(id))
        .createdAt(CREATED.plusSeconds(id))
        .build();
  }

  /// 收件人的收件信息。
  ///
  /// @param state state
  /// @param verified verified
  /// @return 结果
  private static Recipient recipient(AccountState state, boolean verified) {
    return Recipient.builder()
        .userId(ME)
        .email("chen@example.com")
        .emailVerified(verified)
        .state(state)
        .username("chenyu")
        .displayName("陈宇")
        .build();
  }

  /// 替收件人取的名片。
  ///
  /// @param id id
  /// @param name name
  /// @param state state
  /// @return 结果
  private static UserCard card(long id, String name, AccountState state) {
    return UserCard.builder()
        .userId(id)
        .username(id == ME ? "chenyu" : "wangli")
        .displayName(name)
        .state(state)
        .profileVisible(true)
        .build();
  }

  /// 一篇文献的对象视图。
  ///
  /// @param id id
  /// @return 结果
  private static SubjectView paper(long id) {
    return SubjectView.builder()
        .subjectId(id)
        .type(SubjectType.PUBLICATION)
        .key("18")
        .available(true)
        .title("标题")
        .build();
  }

  /// 收件人自己的一条投稿经验。
  ///
  /// @param id id
  /// @return 结果
  private static SubjectView myPost(long id) {
    return SubjectView.builder()
        .subjectId(id)
        .type(SubjectType.POST)
        .key("32")
        .available(true)
        .abbreviation("J Am Heart Assoc")
        .publishedDate("2026-03")
        .ownerId(ME)
        .build();
  }
}

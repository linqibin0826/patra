package dev.patra.notification.app.mail.service;

import dev.patra.notification.app.mail.service.AssembledReminder.Ready;
import dev.patra.notification.app.mail.service.AssembledReminder.Skip;
import dev.patra.notification.domain.inbox.model.read.InboxEntry;
import dev.patra.notification.domain.inbox.model.read.UnreadWindow;
import dev.patra.notification.domain.inbox.port.read.InboxReadPort;
import dev.patra.notification.domain.kernel.model.enums.AccountState;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.kernel.model.read.Recipient;
import dev.patra.notification.domain.kernel.model.read.SubjectView;
import dev.patra.notification.domain.kernel.model.read.UserCard;
import dev.patra.notification.domain.kernel.policy.ExcerptRenderer;
import dev.patra.notification.domain.kernel.policy.MentionNames;
import dev.patra.notification.domain.kernel.port.gateway.PresenceGateway;
import dev.patra.notification.domain.kernel.port.identity.RecipientPort;
import dev.patra.notification.domain.kernel.port.identity.UserCardPort;
import dev.patra.notification.domain.kernel.port.interaction.SubjectViewPort;
import dev.patra.notification.domain.mail.model.aggregate.ReminderDelivery;
import dev.patra.notification.domain.mail.model.enums.DeliveryKind;
import dev.patra.notification.domain.mail.model.enums.SkipReason;
import dev.patra.notification.domain.mail.model.vo.DigestWindow;
import dev.patra.notification.domain.mail.model.vo.content.DigestSectionFacts;
import dev.patra.notification.domain.mail.model.vo.content.ReminderFacts;
import dev.patra.notification.domain.mail.policy.DigestSlotPolicy;
import dev.patra.notification.domain.mail.policy.ReminderMailComposer;
import dev.patra.notification.domain.preference.model.read.EmailMode;
import dev.patra.notification.domain.preference.model.read.EmailModes;
import dev.patra.notification.domain.preference.port.read.EmailSubscriptionReadPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 装配一封提醒邮件（spec 5.3、7.3、8.1）：查该不该发，取收件地址、令牌、对象视图与名字，交给拼装规则。
///
/// 检查从上到下：只查库的在前（未读、在站、档位），调外部服务的在后（收件人、对象视图、名片）；收件人通过检查之后才调
/// `subscriptionOf` 取令牌（它在没有行时会建行，不能给已注销的人建出偏好）。每次重试都从头装配。
/// identity、interaction 不可用时直接抛出，由调用方推迟。
@Component
@RequiredArgsConstructor
public class ReminderDeliveryAssembler {

  /// 匿名经验作者的写法。
  static final String ANONYMOUS_AUTHOR = "匿名作者";

  private final InboxReadPort inbox;
  private final PresenceGateway presence;
  private final EmailSubscriptionReadPort subscriptions;
  private final RecipientPort recipients;
  private final SubjectViewPort subjects;
  private final UserCardPort cards;
  private final ReminderMailComposer composer;

  /// 装配。
  ///
  /// @param delivery 待发的投递
  /// @param now 此刻
  /// @return 可以发了，或者不该发的原因
  public AssembledReminder assemble(ReminderDelivery delivery, Instant now) {
    return delivery.getKind() == DeliveryKind.INSTANT
        ? assembleInstant(delivery, now)
        : assembleDigest(delivery);
  }

  /// 装配一封离站逐条邮件。
  ///
  /// @param delivery 投递
  /// @param now 此刻
  /// @return 结果
  private AssembledReminder assembleInstant(ReminderDelivery delivery, Instant now) {
    long recipientId = delivery.getRecipientId();
    List<InboxEntry> unread =
        inbox.findUnread(recipientId, delivery.getNotificationIds()).stream()
            .filter(ReminderDeliveryAssembler::hasText)
            .toList();
    if (unread.isEmpty()) {
      return Skip.of(SkipReason.NOTHING_UNREAD);
    }
    Instant lastSeen = presence.lastSeen(List.of(recipientId)).get(recipientId);
    List<InboxEntry> entries =
        unread.stream()
            .filter(entry -> lastSeen == null || entry.createdAt().isAfter(lastSeen))
            .toList();
    if (entries.isEmpty()) {
      return Skip.of(SkipReason.NOTHING_UNREAD);
    }
    if (subscriptions.findSubscription(recipientId).modeOf(delivery.getCategory())
        != EmailMode.INSTANT) {
      return Skip.of(SkipReason.MODE);
    }
    Optional<String> address = mailbox(recipientId);
    if (address.isEmpty()) {
      return Skip.of(SkipReason.RECIPIENT);
    }
    String token = subscriptions.subscriptionOf(recipientId).unsubscribeToken();
    Context context = context(recipientId, entries);
    List<ReminderFacts> facts = context.facts(entries);
    if (facts.isEmpty()) {
      return Skip.of(SkipReason.NOTHING_UNREAD);
    }
    return Ready.of(
        address.get(),
        composer.instant(delivery.getCategory(), facts, context.mentionNames(), token, now));
  }

  /// 装配一封每日汇总。
  ///
  /// @param delivery 投递
  /// @return 结果
  private AssembledReminder assembleDigest(ReminderDelivery delivery) {
    long recipientId = delivery.getRecipientId();
    EmailModes modes = subscriptions.findSubscription(recipientId);
    Set<NotificationCategory> categories = EnumSet.noneOf(NotificationCategory.class);
    for (NotificationCategory category :
        List.of(NotificationCategory.REPLY, NotificationCategory.MENTION)) {
      if (modes.modeOf(category) == EmailMode.DIGEST) {
        categories.add(category);
      }
    }
    if (categories.isEmpty()) {
      return Skip.of(SkipReason.MODE);
    }
    DigestWindow window = DigestWindow.endingAt(delivery.getWindowEnd());
    Map<NotificationCategory, UnreadWindow> unread = new EnumMap<>(NotificationCategory.class);
    for (NotificationCategory category : categories) {
      unread.put(
          category,
          inbox.unreadBetween(
              recipientId, category, window.from(), window.to(), DigestSlotPolicy.MAX_ITEMS));
    }
    if (unread.values().stream().mapToInt(UnreadWindow::total).sum() == 0) {
      return Skip.of(SkipReason.NOTHING_UNREAD);
    }
    Optional<String> address = mailbox(recipientId);
    if (address.isEmpty()) {
      return Skip.of(SkipReason.RECIPIENT);
    }
    String token = subscriptions.subscriptionOf(recipientId).unsubscribeToken();
    List<InboxEntry> all =
        unread.values().stream()
            .flatMap(w -> w.newest().stream())
            .filter(ReminderDeliveryAssembler::hasText)
            .toList();
    Context context = context(recipientId, all);
    Function<UnreadWindow, DigestSectionFacts> section =
        w ->
            w == null
                ? DigestSectionFacts.of(0, List.of())
                : DigestSectionFacts.of(
                    w.total(),
                    context.facts(
                        w.newest().stream().filter(ReminderDeliveryAssembler::hasText).toList()));
    DigestSectionFacts replies = section.apply(unread.get(NotificationCategory.REPLY));
    DigestSectionFacts mentions = section.apply(unread.get(NotificationCategory.MENTION));
    if (replies.listable().isEmpty() && mentions.listable().isEmpty()) {
      return Skip.of(SkipReason.NOTHING_UNREAD);
    }
    return Ready.of(
        address.get(),
        composer.digest(window, categories, replies, mentions, context.mentionNames(), token));
  }

  /// 收件地址：identity 认识、账号正常、邮箱已验证且不为空时才有。
  ///
  /// @param recipientId 收件人
  /// @return 地址；不该收时为空
  private Optional<String> mailbox(long recipientId) {
    return recipients
        .find(recipientId)
        .filter(r -> r.state() == AccountState.ACTIVE)
        .filter(Recipient::emailVerified)
        .map(Recipient::email)
        .filter(email -> !email.isBlank());
  }

  /// 查这些条目的对象视图与名片，得到拼装要的事实。
  ///
  /// @param recipientId 收件人（名片按他的视角取）
  /// @param entries 条目
  /// @return 上下文
  private Context context(long recipientId, List<InboxEntry> entries) {
    Map<Long, SubjectView> views =
        inChunks(
            entries.stream().map(InboxEntry::subjectId).distinct().toList(),
            SubjectViewPort.MAX_SUBJECTS,
            subjects::findViews);
    Set<Long> userIds = new LinkedHashSet<>();
    for (InboxEntry entry : entries) {
      if (!entry.actorAnonymous()) {
        userIds.add(entry.actorId());
      }
      userIds.addAll(ExcerptRenderer.mentionedIds(entry.plainText()));
    }
    Map<Long, UserCard> userCards =
        inChunks(
            List.copyOf(userIds), UserCardPort.MAX_USERS, ids -> cards.findCards(ids, recipientId));
    return new Context(recipientId, views, userCards);
  }

  /// 有正文（评论没被删掉）才能拼摘录。
  ///
  /// @param entry 条目
  /// @return 有正文且有评论为真
  private static boolean hasText(InboxEntry entry) {
    return entry.plainText() != null && entry.commentId() != null;
  }

  /// 按上限分批调一个批量接口，把结果并起来。
  ///
  /// @param ids ID
  /// @param chunk 每批上限
  /// @param fetch 批量接口
  /// @param <V> 结果类型
  /// @return ID → 结果
  private static <V> Map<Long, V> inChunks(
      List<Long> ids, int chunk, Function<Collection<Long>, Map<Long, V>> fetch) {
    Map<Long, V> merged = new HashMap<>();
    for (int from = 0; from < ids.size(); from += chunk) {
      merged.putAll(fetch.apply(ids.subList(from, Math.min(ids.size(), from + chunk))));
    }
    return merged;
  }

  /// 一封邮件的对象视图与名片。
  ///
  /// @param recipientId 收件人
  /// @param views 对象视图
  /// @param userCards 名片
  private record Context(
      long recipientId, Map<Long, SubjectView> views, Map<Long, UserCard> userCards) {

    /// `<@ID>` 的名字（不含 `@`），规则同站内列表。
    ///
    /// @return 用户 ID → 名字
    Map<Long, String> mentionNames() {
      Map<Long, String> names = new HashMap<>();
      userCards.forEach((id, card) -> names.put(id, MentionNames.of(card)));
      return names;
    }

    /// 有落点的条目拼成事实：对象查得到、可用、有 key。
    ///
    /// @param entries 条目
    /// @return 事实
    List<ReminderFacts> facts(List<InboxEntry> entries) {
      List<ReminderFacts> facts = new ArrayList<>();
      for (InboxEntry entry : entries) {
        SubjectView view = views.get(entry.subjectId());
        if (view == null || !view.available() || view.key() == null) {
          continue;
        }
        facts.add(
            ReminderFacts.builder()
                .notificationId(entry.notificationId())
                .type(entry.type())
                .actorName(actorName(entry))
                .occurredAt(entry.occurredAt())
                .plainText(entry.plainText())
                .subject(view)
                .commentId(entry.commentId())
                .mine(view.ownerId() != null && view.ownerId() == recipientId)
                .build());
      }
      return facts;
    }

    /// 触发人的名字（spec 8.1）：匿名写「匿名作者」，查不到或已注销写「已注销用户」，否则写显示名。
    ///
    /// @param entry 条目
    /// @return 名字
    private String actorName(InboxEntry entry) {
      if (entry.actorAnonymous()) {
        return ANONYMOUS_AUTHOR;
      }
      UserCard card = userCards.get(entry.actorId());
      if (card == null || card.state() == AccountState.DELETED || card.displayName() == null) {
        return MentionNames.DELETED_USER;
      }
      return card.displayName();
    }
  }
}

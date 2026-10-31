package dev.patra.notification.app.inbox.usecase.query;

import dev.patra.notification.domain.inbox.model.enums.InboxTab;
import dev.patra.notification.domain.inbox.model.read.InboxCursor;
import dev.patra.notification.domain.inbox.model.read.InboxEntry;
import dev.patra.notification.domain.inbox.port.read.InboxReadPort;
import dev.patra.notification.domain.kernel.exception.IdentityUnavailableException;
import dev.patra.notification.domain.kernel.exception.InteractionUnavailableException;
import dev.patra.notification.domain.kernel.model.enums.SubjectType;
import dev.patra.notification.domain.kernel.model.read.RenderedExcerpt;
import dev.patra.notification.domain.kernel.model.read.SubjectView;
import dev.patra.notification.domain.kernel.model.read.UserCard;
import dev.patra.notification.domain.kernel.policy.ExcerptRenderer;
import dev.patra.notification.domain.kernel.policy.MentionNames;
import dev.patra.notification.domain.kernel.port.identity.UserCardPort;
import dev.patra.notification.domain.kernel.port.interaction.SubjectViewPort;
import dev.patra.tool.core.security.CurrentUserPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 提醒列表（spec 8.2、第 9 节），要登录，只读自己的。
///
/// 读一页后拼三样：对象视图（调 interaction，挂了降级为不带对象信息）、触发人与 @ 到的人的名片（调 identity，挂了整页
/// 503）、按 PAP-128 渲染的摘录。对象不可用（文献下线）或查不到时不给摘录：提醒不能成为看到已下线内容的另一个出口。
@Slf4j
@Component
@RequiredArgsConstructor
public class InboxQueryService {

  /// 每页条数。
  public static final int PAGE_SIZE = 20;

  /// 站内摘录的字素上限（单行）。
  public static final int EXCERPT_GRAPHEMES = 120;

  private final InboxReadPort reads;
  private final SubjectViewPort subjects;
  private final UserCardPort users;
  private final CurrentUserPort currentUser;

  /// 当前用户一个标签下的一页。
  ///
  /// @param tab `replies`（默认）/ `mentions` / `helpful`
  /// @param cursor 上一页的 `nextCursor`，可空
  /// @return 一页
  /// @throws IdentityUnavailableException identity 不可用
  public NotificationPage page(String tab, String cursor) {
    InboxTab inboxTab = InboxTab.fromParam(tab);
    InboxCursor after = InboxCursor.parse(cursor);
    long recipient = currentUser.require().userId();
    List<InboxEntry> rows = reads.page(recipient, inboxTab.category(), after, PAGE_SIZE + 1);
    List<InboxEntry> page = rows.subList(0, Math.min(PAGE_SIZE, rows.size()));
    if (page.isEmpty()) {
      return NotificationPage.of(List.of(), null);
    }
    Map<Long, SubjectView> views = viewsOf(page);
    Map<Long, UserCard> cards = cardsFor(page, views, recipient);
    List<NotificationView> items =
        page.stream().map(e -> view(e, views.get(e.subjectId()), cards, recipient)).toList();
    InboxEntry last = page.getLast();
    String next =
        rows.size() > PAGE_SIZE
            ? InboxCursor.of(last.occurredAt(), last.notificationId()).encode()
            : null;
    return NotificationPage.of(items, next);
  }

  /// 这一页的对象视图；interaction 不可用时降级为空表。
  ///
  /// @param page 这一页
  /// @return 登记表 ID → 视图
  private Map<Long, SubjectView> viewsOf(List<InboxEntry> page) {
    List<Long> ids = page.stream().map(InboxEntry::subjectId).distinct().toList();
    try {
      return subjects.findViews(ids);
    } catch (InteractionUnavailableException ex) {
      log.warn("提醒列表取对象视图失败，降级为不带对象信息: subjects={}", ids.size());
      return Map.of();
    }
  }

  /// 这一页要的名片：非匿名的触发人，以及要显示摘录的那些行里 @ 到的人，按 `UserCardPort.MAX_USERS` 分批取。
  ///
  /// @param page 这一页
  /// @param views 对象视图
  /// @param recipient 收件人（访客）
  /// @return 用户 ID → 名片
  private Map<Long, UserCard> cardsFor(
      List<InboxEntry> page, Map<Long, SubjectView> views, long recipient) {
    Set<Long> ids = new LinkedHashSet<>();
    for (InboxEntry entry : page) {
      if (!entry.actorAnonymous()) {
        ids.add(entry.actorId());
      }
      if (showsExcerpt(entry, views.get(entry.subjectId()))) {
        ids.addAll(ExcerptRenderer.mentionedIds(entry.plainText()));
      }
    }
    Map<Long, UserCard> cards = new HashMap<>();
    List<Long> all = new ArrayList<>(ids);
    for (int from = 0; from < all.size(); from += UserCardPort.MAX_USERS) {
      List<Long> batch = all.subList(from, Math.min(all.size(), from + UserCardPort.MAX_USERS));
      cards.putAll(users.findCards(List.copyOf(batch), recipient));
    }
    return cards;
  }

  /// 一行的视图。
  ///
  /// @param entry 库里的一行
  /// @param subject 对象视图，可空
  /// @param cards 名片
  /// @param recipient 收件人
  /// @return 视图
  private static NotificationView view(
      InboxEntry entry, SubjectView subject, Map<Long, UserCard> cards, long recipient) {
    RenderedExcerpt excerpt =
        showsExcerpt(entry, subject)
            ? ExcerptRenderer.render(
                entry.plainText(), namesOf(entry.plainText(), cards), EXCERPT_GRAPHEMES, true)
            : null;
    return NotificationView.builder()
        .notificationId(entry.notificationId())
        .type(entry.type())
        .read(entry.read())
        .occurredAt(entry.occurredAt())
        .actor(entry.actorAnonymous() ? null : cards.get(entry.actorId()))
        .actorAnonymous(entry.actorAnonymous())
        .subject(subject == null ? null : subjectRef(subject, recipient))
        .commentId(entry.commentId())
        .excerpt(excerpt == null ? null : excerpt.text())
        .excerptTruncated(excerpt != null && excerpt.truncated())
        .build();
  }

  /// 要不要显示摘录：有文字（正文只有 Markdown 结构时纯文本是空串），且对象查得到、还在。
  ///
  /// @param entry 一行
  /// @param subject 对象视图，可空
  /// @return 显示为 `true`
  private static boolean showsExcerpt(InboxEntry entry, SubjectView subject) {
    return entry.plainText() != null
        && !entry.plainText().isBlank()
        && subject != null
        && subject.available();
  }

  /// 摘录里 @ 到的人显示的名字。
  ///
  /// @param plainText 正文
  /// @param cards 名片
  /// @return 用户 ID → 名字
  private static Map<Long, String> namesOf(String plainText, Map<Long, UserCard> cards) {
    Map<Long, String> names = new HashMap<>();
    for (Long userId : ExcerptRenderer.mentionedIds(plainText)) {
      names.put(userId, MentionNames.of(cards.get(userId)));
    }
    return names;
  }

  /// 对象上下文：文献给标题，经验给期刊、投稿月份、结果与「是不是我的」。
  ///
  /// @param subject 对象视图
  /// @param recipient 收件人
  /// @return 上下文
  private static SubjectRefView subjectRef(SubjectView subject, long recipient) {
    boolean post = subject.type() == SubjectType.POST;
    return SubjectRefView.builder()
        .type(subject.type())
        .key(subject.key())
        .available(subject.available())
        .title(post ? null : subject.title())
        .journal(post ? subject.journal() : null)
        .abbreviation(post ? subject.abbreviation() : null)
        .submittedMonth(post ? subject.publishedDate() : null)
        .mine(post && Objects.equals(subject.ownerId(), recipient))
        .experience(post ? subject.experience() : null)
        .build();
  }
}

package dev.patra.interaction.app.notification.usecase.query;

import dev.patra.interaction.domain.notification.exception.InvalidNotificationRequestException;
import dev.patra.interaction.domain.notification.model.enums.NotificationTab;
import dev.patra.interaction.domain.notification.model.read.NotificationEntry;
import dev.patra.interaction.domain.notification.model.read.UnreadCounts;
import dev.patra.interaction.domain.notification.model.vo.RenderedExcerpt;
import dev.patra.interaction.domain.notification.policy.ExcerptRules;
import dev.patra.interaction.domain.notification.port.read.NotificationReadPort;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import dev.patra.tool.core.security.CurrentUserPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 提醒的查询（spec 7.1、7.2），要登录，只读自己的。
///
/// 列表拼三样：登记表里的对象上下文、触发人名片、摘录里提及的人的名字。匿名所有者触发的提醒不取他的名片；对象已不可用时
/// 不给摘录（经验删除后下面的回复不再展示，提醒不能成为另一个出口）。
@Component
@RequiredArgsConstructor
public class NotificationQueryService {

  /// 每页条数。
  public static final int PAGE_SIZE = 20;

  /// 游标写法：正整数。
  private static final Pattern CURSOR = Pattern.compile("[1-9][0-9]{0,18}");

  private final NotificationReadPort reads;
  private final SubjectReadPort subjects;
  private final UserDirectoryPort users;
  private final CurrentUserPort currentUser;

  /// 当前用户的未读数。
  ///
  /// @return 两个标签各自的未读数
  public UnreadCounts unreadCounts() {
    return reads.unreadCounts(currentUser.require().userId());
  }

  /// 当前用户一个标签下的一页。
  ///
  /// @param tab `replies`（默认）/ `mentions`
  /// @param cursor 上一页的 `nextCursor`，可空
  /// @return 一页
  public NotificationPage page(String tab, String cursor) {
    NotificationTab notificationTab = NotificationTab.fromParam(tab);
    Long before = parseCursor(cursor);
    long recipient = currentUser.require().userId();
    List<NotificationEntry> rows = reads.page(recipient, notificationTab, before, PAGE_SIZE + 1);
    List<NotificationEntry> page = rows.subList(0, Math.min(PAGE_SIZE, rows.size()));
    if (page.isEmpty()) {
      return NotificationPage.of(List.of(), null);
    }
    Map<SubjectId, SubjectView> subjectViews =
        subjects.findByIds(page.stream().map(NotificationEntry::subjectId).distinct().toList());
    Map<Long, UserCard> cards = cardsFor(page, recipient);
    List<NotificationView> views =
        page.stream().map(e -> view(e, subjectViews.get(e.subjectId()), cards, recipient)).toList();
    String next = rows.size() > PAGE_SIZE ? Long.toString(page.getLast().notificationId()) : null;
    return NotificationPage.of(views, next);
  }

  /// 一行的视图。
  ///
  /// @param entry 库里的一行
  /// @param subject 对象视图，可空
  /// @param cards 名片
  /// @param recipient 收件人
  /// @return 视图
  private static NotificationView view(
      NotificationEntry entry, SubjectView subject, Map<Long, UserCard> cards, long recipient) {
    boolean available = subject != null && subject.state() == SubjectState.ACTIVE;
    RenderedExcerpt excerpt =
        available && entry.excerpt() != null ? ExcerptRules.render(entry.excerpt(), cards) : null;
    return NotificationView.builder()
        .notificationId(entry.notificationId())
        .type(entry.type())
        .read(entry.read())
        .createdAt(entry.createdAt())
        .actor(entry.actorAnonymous() ? null : cards.get(entry.actorId()))
        .actorAnonymous(entry.actorAnonymous())
        .subject(subject == null ? null : subjectRef(subject, recipient))
        .commentId(entry.commentId())
        .excerpt(excerpt == null ? null : excerpt.text())
        .excerptTruncated(excerpt != null && excerpt.truncated())
        .build();
  }

  /// 对象上下文。
  ///
  /// @param subject 对象视图
  /// @param recipient 收件人
  /// @return 上下文
  private static SubjectRefView subjectRef(SubjectView subject, long recipient) {
    boolean post = subject.type() == SubjectType.POST;
    SubjectSnapshot snapshot = subject.snapshot();
    return SubjectRefView.builder()
        .type(subject.type())
        .key(subject.key())
        .available(subject.state() == SubjectState.ACTIVE)
        .title(post || snapshot == null ? null : snapshot.title())
        .journal(post && snapshot != null ? snapshot.journal() : null)
        .abbreviation(post && snapshot != null ? snapshot.abbreviation() : null)
        .submittedMonth(post && snapshot != null ? snapshot.publishedDate() : null)
        .mine(post && subject.owner() != null && subject.owner().userId() == recipient)
        .build();
  }

  /// 这一页要的名片：非匿名的触发人与摘录里提及的人，按 `UserDirectoryPort.MAX_USERS` 分批取。
  ///
  /// @param page 这一页
  /// @param recipient 收件人（访客）
  /// @return 用户 ID → 名片
  private Map<Long, UserCard> cardsFor(List<NotificationEntry> page, long recipient) {
    Set<Long> ids = new LinkedHashSet<>();
    for (NotificationEntry entry : page) {
      if (!entry.actorAnonymous()) {
        ids.add(entry.actorId());
      }
      if (entry.excerpt() != null) {
        ids.addAll(entry.excerpt().mentionIds());
      }
    }
    Map<Long, UserCard> cards = new HashMap<>();
    List<Long> all = new ArrayList<>(ids);
    for (int from = 0; from < all.size(); from += UserDirectoryPort.MAX_USERS) {
      List<Long> batch =
          all.subList(from, Math.min(all.size(), from + UserDirectoryPort.MAX_USERS));
      cards.putAll(users.findCards(batch, recipient));
    }
    return cards;
  }

  /// 解析游标。
  ///
  /// @param cursor 游标，可空
  /// @return 提醒 ID；第一页为 `null`
  private static Long parseCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    if (!CURSOR.matcher(cursor).matches()) {
      throw new InvalidNotificationRequestException("cursor 不合法");
    }
    try {
      return Long.parseLong(cursor);
    } catch (NumberFormatException ex) {
      throw new InvalidNotificationRequestException("cursor 不合法");
    }
  }
}

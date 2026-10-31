package dev.patra.notification.infra.inbox.persistence;

import static dev.patra.notification.domain.inbox.model.read.NotificationType.COMMENT_HELPFUL;
import static dev.patra.notification.domain.inbox.model.read.NotificationType.COMMENT_REPLY;
import static dev.patra.notification.domain.inbox.model.read.NotificationType.MENTION;
import static dev.patra.notification.domain.inbox.model.read.NotificationType.POST_COMMENT;
import static dev.patra.notification.domain.inbox.model.read.NotificationType.POST_HELPFUL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.patra.notification.domain.inbox.model.aggregate.HelpfulNotification;
import dev.patra.notification.domain.inbox.model.enums.HelpfulTargetType;
import dev.patra.notification.domain.inbox.model.read.InboxCursor;
import dev.patra.notification.domain.inbox.model.read.InboxEntry;
import dev.patra.notification.domain.inbox.model.read.UnreadCounts;
import dev.patra.notification.domain.inbox.model.vo.HelpfulMark;
import dev.patra.notification.domain.inbox.model.vo.HelpfulTarget;
import dev.patra.notification.domain.inbox.model.vo.SubjectOwner;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/// 信箱的读端：按排序时间与 ID 翻页、按类别过滤、关联评论正文、三个未读数。
class InboxReadAdapterIT extends InboxPersistenceITSupport {

  @Autowired private InboxReadAdapter reads;
  @Autowired private NotificationRepositoryAdapter notifications;
  @Autowired private CommentContentRepositoryAdapter comments;
  @Autowired private HelpfulNotificationRepositoryAdapter helpful;
  @Autowired private TransactionTemplate transactions;

  /// 排序时间新的在前，同一时刻按 ID 倒序；按游标翻两页不重不漏。
  @Test
  void should_page_by_occurred_at_then_id() {
    Instant t1 = Instant.parse("2026-10-31T01:00:00Z");
    Instant t2 = Instant.parse("2026-10-31T02:00:00Z");
    notifications.insertAll(
        List.of(
            draft(2L, COMMENT_REPLY, 500L, t2),
            draft(2L, COMMENT_REPLY, 501L, t2),
            draft(2L, POST_COMMENT, 502L, t1)));

    List<InboxEntry> first = reads.page(2L, NotificationCategory.REPLY, null, 2);
    InboxEntry last = first.getLast();
    List<InboxEntry> second =
        reads.page(
            2L,
            NotificationCategory.REPLY,
            InboxCursor.of(last.occurredAt(), last.notificationId()),
            2);

    List<InboxEntry> all = new ArrayList<>(first);
    all.addAll(second);
    assertThat(all).extracting(InboxEntry::commentId).containsExactlyInAnyOrder(500L, 501L, 502L);
    assertThat(first).extracting(InboxEntry::occurredAt).containsOnly(t2);
    assertThat(first.get(0).notificationId()).isGreaterThan(first.get(1).notificationId());
    assertThat(second).extracting(InboxEntry::commentId).containsExactly(502L);
  }

  /// 只出这个类别的。
  @Test
  void should_filter_by_category() {
    notifications.insertAll(List.of(draft(2L, COMMENT_REPLY, 500L), draft(2L, MENTION, 501L)));

    assertThat(reads.page(2L, NotificationCategory.MENTION, null, 20))
        .extracting(InboxEntry::type)
        .containsExactly(MENTION);
    assertThat(reads.page(2L, NotificationCategory.HELPFUL, null, 20)).isEmpty();
  }

  /// 带出评论最新正文；评论成了墓碑或没有快照时为空。
  @Test
  void should_join_comment_text_and_hide_tombstone() {
    notifications.insertAll(
        List.of(
            draft(2L, COMMENT_REPLY, 500L),
            draft(2L, COMMENT_REPLY, 501L),
            draft(2L, COMMENT_REPLY, 502L)));
    comments.saveIfNewer(500L, 40L, 2, "最新 <@4>", NOW);
    comments.saveIfNewer(501L, 40L, 1, "会被删", NOW);
    comments.markDeleted(501L, 40L, NOW, NOW);

    List<InboxEntry> page = reads.page(2L, NotificationCategory.REPLY, null, 20);

    assertThat(page)
        .extracting(InboxEntry::commentId, InboxEntry::plainText)
        .containsExactlyInAnyOrder(tuple(500L, "最新 <@4>"), tuple(501L, null), tuple(502L, null));
  }

  /// 三个类别各自数未读；已读的不算；别人的不算。
  @Test
  void should_count_unread_per_category() {
    long read =
        notifications
            .insertAll(
                List.of(
                    draft(2L, COMMENT_REPLY, 500L),
                    draft(2L, POST_COMMENT, 501L),
                    draft(2L, MENTION, 502L),
                    draft(3L, MENTION, 502L)))
            .stream()
            .filter(c -> c.recipientId() == 2L && c.category() == NotificationCategory.MENTION)
            .findFirst()
            .orElseThrow()
            .notificationId();
    notifications.insertAll(List.of(draft(2L, MENTION, 503L)));
    notifications.markRead(2L, read, NOW);

    assertThat(reads.unreadCounts(2L)).isEqualTo(UnreadCounts.of(2, 1, 0));
    assertThat(reads.unreadCounts(99L)).isEqualTo(UnreadCounts.of(0, 0, 0));
  }

  /// 有帮助的两种条目：人数取附表、经验的摘录取条目上的经验正文、评论的取评论快照；点经验时没有评论。
  @Test
  void helpful_rows_carry_actor_count_and_post_text() {
    Instant t1 = Instant.parse("2026-10-31T01:00:00Z");
    HelpfulNotification post =
        HelpfulNotification.open(
            2L,
            HelpfulTarget.of(HelpfulTargetType.POST, 55L, 41L),
            SubjectOwner.of(2L, false),
            HelpfulMark.of(11L, t1),
            "经验正文");
    post.absorb(HelpfulMark.of(12L, t1.plusSeconds(60)), "经验正文");
    post.absorb(HelpfulMark.of(13L, t1.plusSeconds(120)), "经验正文");
    transactions.executeWithoutResult(status -> helpful.insert(post, NOW));
    comments.saveIfNewer(500L, 40L, 1, "评论正文", NOW);
    HelpfulNotification comment =
        HelpfulNotification.open(
            2L,
            HelpfulTarget.of(HelpfulTargetType.COMMENT, 500L, 40L),
            SubjectOwner.of(null, false),
            HelpfulMark.of(11L, t1.plusSeconds(600)),
            "评论正文");
    transactions.executeWithoutResult(status -> helpful.insert(comment, NOW));
    notifications.insertAll(List.of(draft(2L, COMMENT_REPLY, 501L)));

    List<InboxEntry> page = reads.page(2L, NotificationCategory.HELPFUL, null, 20);

    assertThat(page)
        .extracting(
            InboxEntry::type, InboxEntry::actorCount, InboxEntry::plainText, InboxEntry::commentId)
        .containsExactly(
            tuple(COMMENT_HELPFUL, 1, "评论正文", 500L), tuple(POST_HELPFUL, 3, "经验正文", null));
    assertThat(page.get(1).actorId()).isEqualTo(13L);
  }

  /// 回复与 @ 的行人数恒为 1。
  @Test
  void reply_rows_report_one_actor() {
    notifications.insertAll(List.of(draft(2L, COMMENT_REPLY, 500L)));

    assertThat(reads.page(2L, NotificationCategory.REPLY, null, 20))
        .extracting(InboxEntry::actorCount)
        .containsExactly(1);
  }
}

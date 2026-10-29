package dev.patra.interaction.infra.notification.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.notification.model.enums.NotificationTab;
import dev.patra.interaction.domain.notification.model.enums.NotificationType;
import dev.patra.interaction.domain.notification.model.read.NotificationEntry;
import dev.patra.interaction.domain.notification.model.read.UnreadCounts;
import dev.patra.interaction.domain.notification.model.vo.ExcerptText;
import dev.patra.interaction.domain.notification.model.vo.NotificationDraft;
import dev.patra.interaction.domain.notification.port.read.NotificationReadPort;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/// 提醒的读端（spec 7.1、7.2）：按标签倒序分页、只看自己的、未读数、摘录与删除标记。
class NotificationReadAdapterIT extends NotificationPersistenceITSupport {

  @Autowired private NotificationReadPort reads;

  /// 三条回复类、两条 @ 类：每个标签按 ID 倒序，游标往后翻。
  @Test
  void should_page_tab_newest_first_with_cursor() {
    notifications.insertAll(
        List.of(
            draft(A, NotificationType.COMMENT_REPLY, 501L),
            draft(A, NotificationType.MENTION, 502L),
            draft(A, NotificationType.POST_COMMENT, 503L),
            draft(A, NotificationType.MENTION, 504L),
            draft(A, NotificationType.COMMENT_REPLY, 505L)));

    List<NotificationEntry> first = reads.page(A, NotificationTab.REPLIES, null, 2);
    assertThat(first).extracting(NotificationEntry::commentId).containsExactly(505L, 503L);
    assertThat(first.get(0).notificationId()).isGreaterThan(first.get(1).notificationId());

    assertThat(reads.page(A, NotificationTab.REPLIES, first.get(1).notificationId(), 2))
        .extracting(NotificationEntry::commentId)
        .containsExactly(501L);
    assertThat(reads.page(A, NotificationTab.MENTIONS, null, 20))
        .extracting(NotificationEntry::commentId)
        .containsExactly(504L, 502L);
  }

  /// 别人的提醒不出现。
  @Test
  void should_not_see_other_recipients() {
    notifications.insertAll(
        List.of(
            draft(A, NotificationType.COMMENT_REPLY, 501L),
            draft(B, NotificationType.COMMENT_REPLY, 502L)));

    assertThat(reads.page(A, NotificationTab.REPLIES, null, 20))
        .extracting(NotificationEntry::commentId)
        .containsExactly(501L);
  }

  /// 每个标签的未读数，已读的不算。
  @Test
  void should_count_unread_per_tab() {
    notifications.insertAll(
        List.of(
            draft(A, NotificationType.COMMENT_REPLY, 501L),
            draft(A, NotificationType.POST_COMMENT, 502L),
            draft(A, NotificationType.COMMENT_REPLY, 503L),
            draft(A, NotificationType.MENTION, 504L)));
    long read = reads.page(A, NotificationTab.REPLIES, null, 1).getFirst().notificationId();
    notifications.markRead(A, read, T0);

    UnreadCounts counts = reads.unreadCounts(A);

    assertThat(counts).isEqualTo(UnreadCounts.of(2, 1));
    assertThat(counts.total()).isEqualTo(3);
    assertThat(reads.unreadCounts(B)).isEqualTo(UnreadCounts.of(0, 0));
  }

  /// 摘录与截断标记原样读回。
  @Test
  void should_map_excerpt() {
    NotificationDraft truncated =
        NotificationDraft.builder()
            .recipientId(A)
            .type(NotificationType.COMMENT_REPLY)
            .actorId(ACTOR)
            .actorAnonymous(true)
            .subjectId(SubjectId.of(100L))
            .commentId(501L)
            .excerpt(ExcerptText.of("长 <@7>", true))
            .createdAt(T0)
            .build();
    notifications.insertAll(List.of(truncated));

    List<NotificationEntry> page = reads.page(A, NotificationTab.REPLIES, null, 20);

    assertThat(page).hasSize(1);
    NotificationEntry kept = page.get(0);
    assertThat(kept.excerpt()).isEqualTo(ExcerptText.of("长 <@7>", true));
    assertThat(kept.actorAnonymous()).isTrue();
    assertThat(kept.actorId()).isEqualTo(ACTOR);
    assertThat(kept.subjectId().value()).isEqualTo(100L);
    assertThat(kept.createdAt()).isEqualTo(T0);
    assertThat(kept.read()).isFalse();
  }

  /// 信箱里以后不是评论引起的类型 `comment_id` 为空：读回来是 null，不是 0（终审 Minor 1）。
  @Test
  void should_read_null_comment_id_as_null() {
    jdbcTemplate.update(
        "INSERT INTO notification.notification (recipient_id, notification_id, type, actor_id,"
            + " actor_anonymous, subject_id, comment_id, created_at)"
            + " VALUES (?, 1, 'MENTION', ?, false, 100, NULL, now())",
        A,
        ACTOR);

    assertThat(reads.page(A, NotificationTab.MENTIONS, null, 20))
        .singleElement()
        .extracting(NotificationEntry::commentId)
        .isNull();
  }
}

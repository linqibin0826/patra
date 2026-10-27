package dev.patra.interaction.infra.notification.persistence;

import dev.patra.interaction.domain.notification.model.enums.NotificationTab;
import dev.patra.interaction.domain.notification.model.enums.NotificationType;
import dev.patra.interaction.domain.notification.model.read.NotificationEntry;
import dev.patra.interaction.domain.notification.model.read.UnreadCounts;
import dev.patra.interaction.domain.notification.model.vo.ExcerptText;
import dev.patra.interaction.domain.notification.port.read.NotificationReadPort;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `NotificationReadPort` 的原生 SQL 实现。每条语句都带 `recipient_id`，只扫收件人所在的分区。
@Component
@RequiredArgsConstructor
public class NotificationReadAdapter implements NotificationReadPort {

  /// 一行映射成读模型。
  private static final RowMapper<NotificationEntry> ENTRY =
      (rs, rowNum) -> {
        String excerpt = rs.getString("excerpt");
        return NotificationEntry.builder()
            .notificationId(rs.getLong("notification_id"))
            .type(NotificationType.valueOf(rs.getString("type")))
            .actorId(rs.getLong("actor_id"))
            .actorAnonymous(rs.getBoolean("actor_anonymous"))
            .subjectId(SubjectId.of(rs.getLong("subject_id")))
            .commentId(rs.getObject("comment_id", Long.class))
            .excerpt(
                excerpt == null
                    ? null
                    : ExcerptText.of(excerpt, rs.getBoolean("excerpt_truncated")))
            .commentDeleted(rs.getBoolean("comment_deleted"))
            .createdAt(NotificationRows.instant(rs, "created_at"))
            .readAt(NotificationRows.instant(rs, "read_at"))
            .build();
      };

  private final JdbcClient jdbc;

  /// 一个标签下的一页，按 ID 倒序（与生成时刻同序）。
  ///
  /// @param recipientId 收件人
  /// @param tab 标签
  /// @param beforeNotificationId 游标，可空
  /// @param limit 条数
  /// @return 提醒
  @Override
  public List<NotificationEntry> page(
      long recipientId, NotificationTab tab, Long beforeNotificationId, int limit) {
    return jdbc.sql(
            "SELECT notification_id, type, actor_id, actor_anonymous, subject_id, comment_id,"
                + " excerpt, excerpt_truncated, comment_deleted, created_at, read_at"
                + " FROM notification.notification"
                + " WHERE recipient_id = :recipientId AND type IN (:types)"
                + " AND (CAST(:before AS bigint) IS NULL OR notification_id < :before)"
                + " ORDER BY notification_id DESC LIMIT :limit")
        .param("recipientId", recipientId)
        .param("types", NotificationType.of(tab).stream().map(Enum::name).toList())
        .param("before", beforeNotificationId)
        .param("limit", limit)
        .query(ENTRY)
        .list();
  }

  /// 两个标签各自的未读数（走部分索引）。
  ///
  /// @param recipientId 收件人
  /// @return 未读数
  @Override
  public UnreadCounts unreadCounts(long recipientId) {
    int[] counts = new int[2];
    jdbc.sql(
            "SELECT type, count(*) AS n FROM notification.notification"
                + " WHERE recipient_id = :recipientId AND read_at IS NULL GROUP BY type")
        .param("recipientId", recipientId)
        .query(
            rs -> {
              NotificationTab tab = NotificationType.valueOf(rs.getString("type")).tab();
              counts[tab.ordinal()] += rs.getInt("n");
            });
    return UnreadCounts.of(
        counts[NotificationTab.REPLIES.ordinal()], counts[NotificationTab.MENTIONS.ordinal()]);
  }
}

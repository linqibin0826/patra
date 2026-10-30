package dev.patra.notification.infra.inbox.persistence;

import dev.patra.notification.domain.inbox.model.enums.NotificationType;
import dev.patra.notification.domain.inbox.model.read.InboxEntry;
import dev.patra.notification.domain.inbox.model.read.UnreadCounts;
import dev.patra.notification.domain.inbox.model.vo.InboxCursor;
import dev.patra.notification.domain.inbox.port.read.InboxReadPort;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `InboxReadPort` 的原生 SQL 实现。每条语句都带 `recipient_id`，只扫收件人所在的分区。
@Component
@RequiredArgsConstructor
public class InboxReadAdapter implements InboxReadPort {

  /// 列表的查询：评论正文从快照表关联（墓碑的正文为空）。
  private static final String PAGE =
      "SELECT n.notification_id, n.type, n.actor_id, n.actor_anonymous, n.subject_id, n.comment_id,"
          + " c.plain_text, n.occurred_at, n.read_at"
          + " FROM inbox.notification n LEFT JOIN inbox.comment c ON c.comment_id = n.comment_id"
          + " WHERE n.recipient_id = :recipientId AND n.category = :category";

  /// 列表的顺序：排序时间倒序，同一时刻按 ID 倒序（走 `idx_notification_list`）。
  private static final String ORDER =
      " ORDER BY n.occurred_at DESC, n.notification_id DESC LIMIT :limit";

  /// 一行映射成读模型。
  private static final RowMapper<InboxEntry> ENTRY =
      (rs, rowNum) ->
          InboxEntry.builder()
              .notificationId(rs.getLong("notification_id"))
              .type(NotificationType.valueOf(rs.getString("type")))
              .actorId(rs.getLong("actor_id"))
              .actorAnonymous(rs.getBoolean("actor_anonymous"))
              .subjectId(rs.getLong("subject_id"))
              .commentId(rs.getObject("comment_id", Long.class))
              .plainText(rs.getString("plain_text"))
              .occurredAt(InboxRows.instant(rs, "occurred_at"))
              .readAt(InboxRows.instant(rs, "read_at"))
              .build();

  private final JdbcClient jdbc;

  /// 一个类别下的一页。
  ///
  /// @param recipientId 收件人
  /// @param category 类别
  /// @param after 游标，可空
  /// @param limit 条数
  /// @return 提醒
  @Override
  public List<InboxEntry> page(
      long recipientId, NotificationCategory category, InboxCursor after, int limit) {
    if (after == null) {
      return jdbc.sql(PAGE + ORDER)
          .param("recipientId", recipientId)
          .param("category", category.name())
          .param("limit", limit)
          .query(ENTRY)
          .list();
    }
    return jdbc.sql(PAGE + " AND (n.occurred_at, n.notification_id) < (:at, :id)" + ORDER)
        .param("recipientId", recipientId)
        .param("category", category.name())
        .param("at", InboxRows.utc(after.occurredAt()))
        .param("id", after.notificationId())
        .param("limit", limit)
        .query(ENTRY)
        .list();
  }

  /// 三个类别各自的未读数（走部分索引 `idx_notification_unread`）。
  ///
  /// @param recipientId 收件人
  /// @return 未读数
  @Override
  public UnreadCounts unreadCounts(long recipientId) {
    Map<NotificationCategory, Integer> counts = new EnumMap<>(NotificationCategory.class);
    jdbc.sql(
            "SELECT category, count(*) AS n FROM inbox.notification"
                + " WHERE recipient_id = :recipientId AND read_at IS NULL GROUP BY category")
        .param("recipientId", recipientId)
        .query(
            rs -> {
              counts.put(NotificationCategory.valueOf(rs.getString("category")), rs.getInt("n"));
            });
    return UnreadCounts.of(
        counts.getOrDefault(NotificationCategory.REPLY, 0),
        counts.getOrDefault(NotificationCategory.MENTION, 0),
        counts.getOrDefault(NotificationCategory.HELPFUL, 0));
  }
}

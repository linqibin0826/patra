package dev.patra.notification.infra.inbox.persistence;

import dev.patra.notification.domain.inbox.model.read.InboxCursor;
import dev.patra.notification.domain.inbox.model.read.InboxEntry;
import dev.patra.notification.domain.inbox.model.read.NotificationType;
import dev.patra.notification.domain.inbox.model.read.UnreadCounts;
import dev.patra.notification.domain.inbox.model.read.UnreadWindow;
import dev.patra.notification.domain.inbox.port.read.InboxReadPort;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import java.time.Instant;
import java.util.Collection;
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

  /// 一行提醒的列：评论正文从快照表关联（墓碑的正文为空），有帮助点经验时取条目上的经验正文；有帮助的人数数附表，
  /// 其余为 1。列表与提醒邮件（按 ID 复查、汇总窗口）共用这一份列清单。
  private static final String SELECT =
      "SELECT n.notification_id, n.type, n.actor_id, n.actor_anonymous, n.subject_id, n.comment_id,"
          + " COALESCE(c.plain_text, n.post_plain_text) AS plain_text, n.occurred_at, n.created_at,"
          + " n.read_at,"
          + " CASE WHEN n.category = 'HELPFUL' THEN (SELECT count(*) FROM inbox.helpful_actor a"
          + " WHERE a.recipient_id = n.recipient_id AND a.notification_id = n.notification_id)"
          + " ELSE 1 END AS actor_count"
          + " FROM inbox.notification n LEFT JOIN inbox.comment c ON c.comment_id = n.comment_id";

  /// 一个收件人一个类别的提醒。
  private static final String PAGE =
      SELECT + " WHERE n.recipient_id = :recipientId AND n.category = :category";

  /// 一个类别在写入时刻窗口里的未读（走部分索引 `idx_notification_unread`，再按写入时刻过滤）。
  private static final String UNREAD_IN_WINDOW =
      " FROM inbox.notification n WHERE n.recipient_id = :recipientId AND n.category = :category"
          + " AND n.read_at IS NULL AND n.created_at >= :from AND n.created_at < :to";

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
              .actorCount(rs.getInt("actor_count"))
              .subjectId(rs.getLong("subject_id"))
              .commentId(rs.getObject("comment_id", Long.class))
              .plainText(rs.getString("plain_text"))
              .occurredAt(InboxRows.instant(rs, "occurred_at"))
              .createdAt(InboxRows.instant(rs, "created_at"))
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

  /// 给定的提醒里仍存在且未读的。
  ///
  /// @param recipientId 收件人
  /// @param notificationIds 提醒 ID
  /// @return 提醒，按排序时间先后
  @Override
  public List<InboxEntry> findUnread(long recipientId, Collection<Long> notificationIds) {
    if (notificationIds.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            SELECT
                + " WHERE n.recipient_id = :recipientId AND n.notification_id IN (:ids)"
                + " AND n.read_at IS NULL ORDER BY n.occurred_at, n.notification_id")
        .param("recipientId", recipientId)
        .param("ids", notificationIds)
        .query(ENTRY)
        .list();
  }

  /// 一个类别在写入时刻窗口里的未读总数与最新几条。
  ///
  /// @param recipientId 收件人
  /// @param category 类别
  /// @param from 左端（含）
  /// @param to 右端（不含）
  /// @param limit 最多取几条
  /// @return 窗口
  @Override
  public UnreadWindow unreadBetween(
      long recipientId, NotificationCategory category, Instant from, Instant to, int limit) {
    int total =
        jdbc.sql("SELECT count(*)" + UNREAD_IN_WINDOW)
            .param("recipientId", recipientId)
            .param("category", category.name())
            .param("from", InboxRows.utc(from))
            .param("to", InboxRows.utc(to))
            .query(Integer.class)
            .single();
    if (total == 0) {
      return UnreadWindow.of(0, List.of());
    }
    List<InboxEntry> newest =
        jdbc.sql(
                SELECT
                    + " WHERE n.recipient_id = :recipientId AND n.category = :category"
                    + " AND n.read_at IS NULL AND n.created_at >= :from AND n.created_at < :to"
                    + ORDER)
            .param("recipientId", recipientId)
            .param("category", category.name())
            .param("from", InboxRows.utc(from))
            .param("to", InboxRows.utc(to))
            .param("limit", limit)
            .query(ENTRY)
            .list();
    return UnreadWindow.of(total, newest);
  }
}

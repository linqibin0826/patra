package dev.patra.notification.infra.mail.persistence;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.mail.model.vo.QueuedReminder;
import dev.patra.notification.domain.mail.port.repository.ReminderQueueRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// 排队表的原生 SQL 实现。
@Repository
@RequiredArgsConstructor
public class ReminderQueueRepositoryAdapter implements ReminderQueueRepository {

  /// 一行映射成排队行。
  private static final RowMapper<QueuedReminder> ROW =
      (rs, rowNum) ->
          QueuedReminder.builder()
              .notificationId(rs.getLong("notification_id"))
              .recipientId(rs.getLong("recipient_id"))
              .category(NotificationCategory.valueOf(rs.getString("category")))
              .queuedAt(rs.getObject("queued_at", OffsetDateTime.class).toInstant())
              .dueAt(rs.getObject("due_at", OffsetDateTime.class).toInstant())
              .build();

  private final JdbcClient jdbc;

  /// 入队，已在队里的跳过；只在调用方（信箱写入）的事务里做。
  ///
  /// @param rows 排队行
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(List<QueuedReminder> rows) {
    for (QueuedReminder row : rows) {
      jdbc.sql(
              """
              INSERT INTO mail.reminder_queue (notification_id, recipient_id, category, queued_at, due_at)
              VALUES (:notificationId, :recipientId, :category, :queuedAt, :dueAt)
              ON CONFLICT (notification_id) DO NOTHING
              """)
          .param("notificationId", row.notificationId())
          .param("recipientId", row.recipientId())
          .param("category", row.category().name())
          .param("queuedAt", utc(row.queuedAt()))
          .param("dueAt", utc(row.dueAt()))
          .update();
    }
  }

  /// 至少有一行已到点的收件人。
  ///
  /// @param now 此刻
  /// @param afterRecipientId 游标
  /// @param limit 最多几个
  /// @return 收件人 ID
  @Override
  public List<Long> recipientsDue(Instant now, long afterRecipientId, int limit) {
    return jdbc.sql(
            """
            SELECT DISTINCT recipient_id FROM mail.reminder_queue
            WHERE due_at <= :now AND recipient_id > :after
            ORDER BY recipient_id LIMIT :limit
            """)
        .param("now", utc(now))
        .param("after", afterRecipientId)
        .param("limit", limit)
        .query(Long.class)
        .list();
  }

  /// 一个收件人的全部排队行。
  ///
  /// @param recipientId 收件人
  /// @return 排队行
  @Override
  public List<QueuedReminder> findByRecipient(long recipientId) {
    return jdbc.sql("SELECT * FROM mail.reminder_queue WHERE recipient_id = :recipientId")
        .param("recipientId", recipientId)
        .query(ROW)
        .list();
  }

  /// 按提醒 ID 删除。
  ///
  /// @param notificationIds 提醒 ID
  /// @return 删掉的行数
  @Override
  public int delete(Collection<Long> notificationIds) {
    if (notificationIds.isEmpty()) {
      return 0;
    }
    return jdbc.sql("DELETE FROM mail.reminder_queue WHERE notification_id IN (:ids)")
        .param("ids", notificationIds)
        .update();
  }

  /// 时刻按 UTC 绑定。
  ///
  /// @param instant 时刻
  /// @return 带时区偏移的时间
  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}

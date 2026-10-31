package dev.patra.notification.domain.mail.port.repository;

import dev.patra.notification.domain.mail.model.vo.QueuedReminder;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/// 离站逐条的排队表（spec 6.1）。
public interface ReminderQueueRepository {

  /// 入队；同一条提醒已在队里就跳过。必须在信箱写入的事务里调。
  ///
  /// @param rows 排队行
  void enqueue(List<QueuedReminder> rows);

  /// 至少有一行已到点的收件人。
  ///
  /// @param now 此刻
  /// @param afterRecipientId 游标：只取比它大的
  /// @param limit 最多几个
  /// @return 收件人 ID，升序、去重
  List<Long> recipientsDue(Instant now, long afterRecipientId, int limit);

  /// 一个收件人的全部排队行（两类、到没到点都在内）。
  ///
  /// @param recipientId 收件人
  /// @return 排队行
  List<QueuedReminder> findByRecipient(long recipientId);

  /// 按提醒 ID 删除。
  ///
  /// @param notificationIds 提醒 ID
  /// @return 删掉的行数
  int delete(Collection<Long> notificationIds);
}

package dev.patra.notification.domain.inbox.port.repository;

import dev.patra.notification.domain.inbox.model.enums.MarkReadOutcome;
import dev.patra.notification.domain.inbox.model.read.CreatedNotification;
import dev.patra.notification.domain.inbox.model.vo.NotificationDraft;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/// 提醒的写端。表按收件人分区，除按评论、按对象删除与保留期清理外，每个操作都带收件人。
public interface NotificationRepository {

  /// 写入一批提醒。同一收件人对同一条评论已有回复或 @ 提醒时跳过。
  ///
  /// @param drafts 待写入的提醒
  /// @return 真写入的提醒
  List<CreatedNotification> insertAll(List<NotificationDraft> drafts);

  /// 删掉一条评论的全部提醒（所有收件人、所有类别）。
  ///
  /// @param commentId 评论
  /// @return 被删了提醒的收件人
  Set<Long> deleteByComment(long commentId);

  /// 删掉一个对象下的全部提醒（经验删除）。
  ///
  /// @param subjectId 对象
  /// @return 被删了提醒的收件人
  Set<Long> deleteBySubject(long subjectId);

  /// 标一条为已读；已读的保持原来的已读时刻。
  ///
  /// @param recipientId 收件人
  /// @param notificationId 提醒
  /// @param readAt 已读时刻
  /// @return 结果
  MarkReadOutcome markRead(long recipientId, long notificationId, Instant readAt);

  /// 收件人的未读全部标为已读（三个标签一起）。
  ///
  /// @param recipientId 收件人
  /// @param readAt 已读时刻
  /// @return 标记的条数
  int markAllRead(long recipientId, Instant readAt);

  /// 删除排序时间早于截止时刻的提醒（已读未读都删）。
  ///
  /// @param cutoff 截止时刻（不含）
  /// @return 删除的条数
  int deleteOccurredBefore(Instant cutoff);

  /// 删除收件人的全部提醒（注销清理）。
  ///
  /// @param recipientId 收件人
  /// @return 删除的条数
  int deleteAllOf(long recipientId);

  /// 有提醒的收件人，按用户 ID 升序的一批（注销清理枚举用）。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID
  List<Long> findRecipientsAfter(long afterUserId, int limit);
}

package dev.patra.interaction.domain.notification.port.repository;

import dev.patra.interaction.domain.notification.model.vo.ExcerptText;
import dev.patra.interaction.domain.notification.model.vo.NotificationDraft;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.util.List;

/// 提醒的写端。表按收件人分区，除按评论更新的两个方法外，每个操作都带收件人。
public interface NotificationRepository {

  /// 写入一批提醒。同一收件人对同一条评论已有提醒时跳过（spec 4.3：编辑新增的 @ 不重复提醒被回复的人、经验作者）。
  ///
  /// @param drafts 待写入的提醒
  /// @return 新写入的条数
  int insertAll(List<NotificationDraft> drafts);

  /// 评论编辑后刷新它的全部提醒的摘录。
  ///
  /// @param commentId 评论
  /// @param excerpt 新摘录
  /// @return 更新的条数
  int refreshExcerpt(long commentId, ExcerptText excerpt);

  /// 评论删除后删掉它的全部提醒（所有收件人）。
  ///
  /// @param commentId 评论
  /// @return 删除的条数
  int deleteByComment(long commentId);

  /// 投稿经验删除后删掉这个对象下的全部提醒（所有收件人）。
  ///
  /// @param subjectId 经验的登记对象
  /// @return 删除的条数
  int deleteBySubject(SubjectId subjectId);

  /// 标一条为已读；已读的保持原来的已读时刻。
  ///
  /// @param recipientId 收件人
  /// @param notificationId 提醒
  /// @param readAt 已读时刻
  /// @return 这条提醒存在且属于收件人时为 `true`
  boolean markRead(long recipientId, long notificationId, Instant readAt);

  /// 收件人的未读全部标为已读（两个标签一起）。
  ///
  /// @param recipientId 收件人
  /// @param readAt 已读时刻
  /// @return 标记的条数
  int markAllRead(long recipientId, Instant readAt);

  /// 删除生成时刻早于截止时刻的提醒（已读未读都删）。
  ///
  /// @param cutoff 截止时刻（不含）
  /// @return 删除的条数
  int deleteCreatedBefore(Instant cutoff);

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

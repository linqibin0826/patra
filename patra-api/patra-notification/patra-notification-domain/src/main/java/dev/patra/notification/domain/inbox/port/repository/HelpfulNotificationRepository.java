package dev.patra.notification.domain.inbox.port.repository;

import dev.patra.notification.domain.inbox.model.aggregate.HelpfulNotification;
import dev.patra.notification.domain.inbox.model.vo.HelpfulMark;
import dev.patra.notification.domain.inbox.model.vo.HelpfulTarget;
import dev.patra.notification.domain.inbox.model.vo.SubjectOwner;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/// 有帮助的合并条目：存在 `inbox.notification`（`category = HELPFUL`）与附表 `inbox.helpful_actor`。都必须在调用方的事务里调。
public interface HelpfulNotificationRepository {

  /// 按合并键找条目并锁住信箱行。
  ///
  /// @param recipientId 收件人
  /// @param target 被点的内容
  /// @param day 北京时间的那一天
  /// @param owner 所在对象的所有者（库里不存，从这次事件带进聚合）
  /// @return 条目；这一天还没有时为空
  Optional<HelpfulNotification> findForUpdate(
      long recipientId, HelpfulTarget target, LocalDate day, SubjectOwner owner);

  /// 写入一条新的合并条目（信箱行与它的第一个点的人）。
  ///
  /// @param notification 新条目
  /// @param createdAt 写入信箱的时刻（处理时的时钟）
  /// @return 新条目的 ID
  long insert(HelpfulNotification notification, Instant createdAt);

  /// 写回并入一次点击后的条目：附表里这个人取较早的时刻，信箱行更新最近一位、时间、已读与经验正文。
  ///
  /// @param notification 条目
  /// @param mark 刚并入的点击
  void update(HelpfulNotification notification, HelpfulMark mark);
}

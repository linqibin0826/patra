package dev.patra.notification.domain.mail.port.repository;

import dev.patra.notification.domain.mail.model.aggregate.ReminderDelivery;
import java.time.Instant;
import java.util.List;

/// 提醒邮件的投递记录（spec 6.2）。
public interface ReminderDeliveryRepository {

  /// 新建一条。
  ///
  /// @param delivery 投递记录
  void insert(ReminderDelivery delivery);

  /// 新建一条汇总投递；这个人这个窗口已有一条就跳过。
  ///
  /// @param delivery 汇总投递
  /// @return 真插入了为 `true`
  boolean insertDigestIfAbsent(ReminderDelivery delivery);

  /// 待发且已到下一次尝试时刻的投递，按时刻先后。
  ///
  /// @param now 此刻
  /// @param limit 最多几条
  /// @return 投递记录
  List<ReminderDelivery> findDue(Instant now, int limit);

  /// 写回状态、跳过原因、失败次数、下一次尝试、第一次发送与结束时刻。
  ///
  /// @param delivery 投递记录
  void update(ReminderDelivery delivery);

  /// 删除结束得早于截止时刻的记录。
  ///
  /// @param cutoff 截止时刻
  /// @return 删掉的行数
  int deleteFinishedBefore(Instant cutoff);
}

package dev.patra.notification.domain.mail.port.repository;

import dev.patra.notification.domain.mail.model.aggregate.MailDelivery;
import java.util.UUID;

/// 邮件发送记录的仓储。
///
/// 发信前按投递 ID 查有没有处理过（重复投递直接跳过）；处理完在短事务里追加一条。
public interface MailDeliveryRepository {

  /// 这个投递 ID 是否已有记录（发过，或判过链接过期）。
  ///
  /// @param deliveryId 投递 ID
  /// @return 有记录为 `true`
  boolean exists(UUID deliveryId);

  /// 追加一条记录；必须在调用方的事务里调用。
  ///
  /// @param delivery 记录
  void add(MailDelivery delivery);
}

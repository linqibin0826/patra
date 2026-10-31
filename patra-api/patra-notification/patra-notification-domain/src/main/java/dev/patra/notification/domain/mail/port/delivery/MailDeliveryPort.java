package dev.patra.notification.domain.mail.port.delivery;

import dev.patra.notification.domain.mail.exception.MailDeliveryFailedException;
import dev.patra.notification.domain.mail.model.vo.OutgoingMail;

/// 发信端口：把一封信交给邮件服务商。安全邮件与提醒邮件共用。
///
/// **契约**：同步发出，返回即服务商已收下；失败抛 {@link MailDeliveryFailedException}。投递 ID 是服务商的
/// 幂等键，同一个 ID 必须对应同一份内容——调用方重试时用同一个 ID，服务商 24 小时内只送达一次。
/// 不要在数据库事务里调它（远程调用，见 `transaction.md` 第 2 节）。
public interface MailDeliveryPort {

  /// 发一封信。
  ///
  /// @param mail 要发的信
  /// @throws MailDeliveryFailedException 服务商不通、拒收，或信组不出来
  void deliver(OutgoingMail mail);
}

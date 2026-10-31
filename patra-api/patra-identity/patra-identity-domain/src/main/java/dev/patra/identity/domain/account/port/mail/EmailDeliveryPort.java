package dev.patra.identity.domain.account.port.mail;

import dev.patra.identity.domain.account.model.vo.mail.OutgoingMail;

/// 安全邮件的投递端口。
///
/// 必须在业务事务里调用：实现把这封信登记进同一个事务，事务提交后才外送（交给通知服务发出），回滚则一起作废，
/// 所以「业务改动生效 ⇔ 这封信一定会发」。投递是至少一次：补投时同一封信的事件 ID 不变，消费端据此去重。
/// 登记失败抛异常，调用方的事务随之回滚。
public interface EmailDeliveryPort {

  /// 在调用方的事务里登记一封邮件。
  ///
  /// @param mail 要发的邮件
  void send(OutgoingMail mail);
}

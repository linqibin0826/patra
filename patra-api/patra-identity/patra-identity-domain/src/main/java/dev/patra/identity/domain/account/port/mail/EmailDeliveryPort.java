package dev.patra.identity.domain.account.port.mail;

import dev.patra.identity.domain.account.model.vo.mail.OutgoingMail;

/// 邮件发送端口。
///
/// `send` 立即返回：渲染和发送在实现自己的执行器上异步进行，任何失败只记日志，不抛给调用方。
/// 返回值只说明这封有没有被接下：执行器拒绝（队列满、已停机）时为 `false`，接下之后发送失败仍为 `true`。
/// 调用方要在数据库事务提交之后再调它，否则用户点到链接时令牌可能还没落库。
public interface EmailDeliveryPort {

  /// 异步发一封邮件。
  ///
  /// @param mail 要发的邮件
  /// @return 接下了为 `true`；被拒绝、这封丢掉时为 `false`
  boolean send(OutgoingMail mail);
}

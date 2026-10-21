package dev.patra.identity.domain.port.mail;

/// 邮件发送端口。
///
/// `send` 立即返回：渲染和发送在实现自己的执行器上异步进行，任何失败只记日志，不抛给调用方。
/// 调用方要在数据库事务提交之后再调它，否则用户点到链接时令牌可能还没落库。
public interface EmailDeliveryPort {

  /// 异步发一封邮件。
  ///
  /// @param mail 要发的邮件
  void send(OutgoingMail mail);
}

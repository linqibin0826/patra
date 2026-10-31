package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import java.util.UUID;

/// 一封要发出去的安全邮件：只有事实（收件人、昵称、链接、时间、操作上下文），主题与正文归通知服务（PAP-104）。
///
/// 它同时是登记进事件发布登记表的领域事件：业务事务里交给发信端口，与业务改动同一事务登记，提交后映射成
/// `patra-identity-api` 的集成事件外送。事件 ID 在构造时生成、随登记行保存，补投时原样还原，消费端靠它去重。
public interface OutgoingMail {

  /// 事件 ID：构造时生成，补投时不变。
  ///
  /// @return 事件 ID
  UUID eventId();

  /// 收件人。
  ///
  /// @return 收件人邮箱
  EmailAddress to();
}

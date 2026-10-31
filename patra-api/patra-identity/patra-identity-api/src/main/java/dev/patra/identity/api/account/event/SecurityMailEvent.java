package dev.patra.identity.api.account.event;

import dev.patra.tool.core.event.IntegrationEvent;

/// identity 的安全邮件集成事件（PAP-104）：每个事件是「请给这个地址发这一封安全邮件」，由通知服务的邮件渠道照着发。
///
/// 发到 topic 交换机 {@link #EXCHANGE}，路由键是各记录的 `ROUTING_KEY`（`security-mail.` 加邮件种类），一条
/// {@link #ROUTING_KEY_PATTERN} 绑定收全部。事件只带事实（收件地址、昵称、链接与过期时间、操作上下文），主题、正文、
/// 「不是我怎么办」与有效期文案归通知服务。投递是至少一次：消费端按 `eventId` 去重，发信时它也是 Resend 的幂等键。
///
/// sealed：消费端 `switch` 能穷举全部种类，新增一种时漏处理编译不过。
public sealed interface SecurityMailEvent extends IntegrationEvent
    permits EmailVerificationMailEvent,
        PasswordResetMailEvent,
        PasswordResetDoneMailEvent,
        PasswordChangedMailEvent,
        EmailChangeConfirmMailEvent,
        EmailChangedNoticeMailEvent,
        EmailChangedConfirmationMailEvent,
        EmailChangeRevertedMailEvent,
        NewDeviceLoginMailEvent,
        AccountDeletionRequestedMailEvent,
        AccountDeletionCancelledMailEvent,
        AccountDeletionReminderMailEvent,
        AccountDeletionCompletedMailEvent {

  /// identity 发布集成事件的交换机。
  String EXCHANGE = "identity.events";

  /// 收全部安全邮件事件的绑定模式。
  String ROUTING_KEY_PATTERN = "security-mail.*";

  /// 收件地址。改邮箱时各封的收件地址不同（确认信发新邮箱、变更通知发旧邮箱），以事件为准。
  ///
  /// @return 收件地址
  String to();

  /// 收件人的昵称，没设时已回退成用户名。
  ///
  /// @return 昵称
  String displayName();
}

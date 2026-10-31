package dev.patra.notification.app.mail.usecase.security;

import dev.patra.notification.domain.mail.model.vo.SecurityMail;
import dev.patra.tool.cqrs.Command;
import java.util.Objects;

/// 发一封 identity 请求的安全邮件。
///
/// @param consumer 消费方名，填收到这个事件的队列名；去重表按（消费方, 事件 ID）记
/// @param mail 这封信的事实
public record SendSecurityMailCommand(String consumer, SecurityMail mail)
    implements Command<SendSecurityMailResult> {

  /// 两项都不能为 `null`。
  public SendSecurityMailCommand {
    Objects.requireNonNull(consumer, "consumer 不能为 null");
    Objects.requireNonNull(mail, "mail 不能为 null");
  }

  /// 创建。
  ///
  /// @param consumer 消费方名（队列名）
  /// @param mail 这封信的事实
  /// @return 命令
  public static SendSecurityMailCommand of(String consumer, SecurityMail mail) {
    return new SendSecurityMailCommand(consumer, mail);
  }
}

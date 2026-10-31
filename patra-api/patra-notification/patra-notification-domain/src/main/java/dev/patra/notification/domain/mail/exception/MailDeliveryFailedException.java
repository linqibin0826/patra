package dev.patra.notification.domain.mail.exception;

import dev.patra.notification.domain.mail.model.enums.MailDeliveryFailure;
import dev.patra.notification.domain.mail.model.vo.OutgoingMail;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.util.Objects;

/// 一封信没发出去：服务商不通、拒收，或者信本身组不出来（收件地址不合法、模板渲染失败）。
///
/// {@link #failure()} 说明属于哪一类，调用方据此记已发、跳过或重试。消息里有模板名、投递 ID、类别与
/// 发信适配器写好的脱敏原因；**不挂原始异常**：服务商的回复与 JavaMail 的异常会带上收件地址，
/// 重试耗尽时 Spring AMQP 会把整条 cause 链打进日志（spec 6.1）。
public class MailDeliveryFailedException extends DomainException {

  private final MailDeliveryFailure failure;

  /// 创建。
  ///
  /// @param mail 没发出去的信
  /// @param failure 属于哪一类
  /// @param reason 脱敏后的原因：各层异常的类名与消息，邮箱地址已遮住
  public MailDeliveryFailedException(
      OutgoingMail mail, MailDeliveryFailure failure, String reason) {
    super(
        "邮件发送失败: template="
            + mail.template()
            + ", deliveryId="
            + mail.deliveryId()
            + ", failure="
            + failure
            + ", reason="
            + reason,
        StandardErrorTrait.DEP_UNAVAILABLE);
    this.failure = Objects.requireNonNull(failure, "failure 不能为 null");
  }

  /// 属于哪一类失败。
  ///
  /// @return 类别
  public MailDeliveryFailure failure() {
    return failure;
  }
}

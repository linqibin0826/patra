package dev.patra.notification.domain.mail.model.aggregate;

import dev.patra.notification.domain.mail.model.enums.MailDeliveryOutcome;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/// 一封信的发送记录：投递 ID、哪一类信、结果、记下的时刻。只追加、不修改。
///
/// 不存正文、链接、收件地址与用户 ID（决策 F：通知服务不存邮箱副本）。要查某人有没有收到某封信，
/// 先在发起方的日志里按用户找到投递 ID（安全邮件就是 identity 的事件 ID），再按它查这里。
///
/// @param deliveryId 投递 ID，也是服务商的幂等键
/// @param template 模板名
/// @param outcome 结果
/// @param recordedAt 记下的时刻
public record MailDelivery(
    UUID deliveryId, String template, MailDeliveryOutcome outcome, Instant recordedAt) {

  /// 四项都不能为 `null`。
  public MailDelivery {
    Objects.requireNonNull(deliveryId, "deliveryId 不能为 null");
    Objects.requireNonNull(template, "template 不能为 null");
    Objects.requireNonNull(outcome, "outcome 不能为 null");
    Objects.requireNonNull(recordedAt, "recordedAt 不能为 null");
  }

  /// 发出去了。
  ///
  /// @param deliveryId 投递 ID
  /// @param template 模板名
  /// @param sentAt 发出的时刻
  /// @return 记录
  public static MailDelivery sent(UUID deliveryId, String template, Instant sentAt) {
    return new MailDelivery(deliveryId, template, MailDeliveryOutcome.SENT, sentAt);
  }

  /// 链接已过期，没发。
  ///
  /// @param deliveryId 投递 ID
  /// @param template 模板名
  /// @param decidedAt 判定的时刻
  /// @return 记录
  public static MailDelivery linkExpired(UUID deliveryId, String template, Instant decidedAt) {
    return new MailDelivery(deliveryId, template, MailDeliveryOutcome.LINK_EXPIRED, decidedAt);
  }

  /// 服务商永久拒收（或信组不出来），不再重试。
  ///
  /// @param deliveryId 投递 ID
  /// @param template 模板名
  /// @param rejectedAt 被拒的时刻
  /// @return 记录
  public static MailDelivery rejected(UUID deliveryId, String template, Instant rejectedAt) {
    return new MailDelivery(deliveryId, template, MailDeliveryOutcome.REJECTED, rejectedAt);
  }
}

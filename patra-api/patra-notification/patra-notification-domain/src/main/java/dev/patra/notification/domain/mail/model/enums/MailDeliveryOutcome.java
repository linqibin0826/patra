package dev.patra.notification.domain.mail.model.enums;

/// 一封信处理完的结果，进发送记录。发送失败不进记录（那次处理回滚了，看日志与死信队列）。
public enum MailDeliveryOutcome {
  /// 发出去了。
  SENT,
  /// 只为链接而发的信，处理时链接已过期，没发。
  LINK_EXPIRED
}

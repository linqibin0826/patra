package dev.patra.notification.domain.mail.model.enums;

/// 一封信处理完的结果，进发送记录。临时失败不进记录（那次处理回滚了，看日志与死信队列）。
public enum MailDeliveryOutcome {
  /// 发出去了（含服务商说这个幂等键已经接受过）。
  SENT,
  /// 只为链接而发的信，处理时链接已过期，没发。
  LINK_EXPIRED,
  /// 服务商永久拒收，或信本身组不出来，不再重试。
  REJECTED
}

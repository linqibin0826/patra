package dev.patra.notification.domain.mail.model.enums;

/// 一封信没发出去时属于哪一类，调用方据此决定记已发、跳过，还是稍后重试（spec 6.6）。
public enum MailDeliveryFailure {
  /// 这个幂等键服务商已经接受过（同键不同内容被拒）：当作已发。
  ALREADY_ACCEPTED,
  /// 这封信对这个收件人永远发不成（收件人被拒、信本身组不出来）：不再重试。
  REJECTED,
  /// 稍后可能就好了（连不上、超时、限流、鉴权失败、认不出的）：稍后重试。
  TRANSIENT
}

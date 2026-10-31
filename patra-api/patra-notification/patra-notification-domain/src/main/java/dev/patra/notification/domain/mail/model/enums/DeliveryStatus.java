package dev.patra.notification.domain.mail.model.enums;

/// 提醒邮件投递记录的状态（spec 6.2）。
public enum DeliveryStatus {

  /// 待发：到 `next_attempt_at` 时装配并调用发信端口。
  PENDING,

  /// 发出去了（发信端口正常返回，或报同一个幂等键已被接受）。
  SENT,

  /// 确定不该发，原因见 `SkipReason`。
  SKIPPED,

  /// 调用过发信端口之后 24 小时仍没成功：幂等键已失效，不再重发。
  EXPIRED;

  /// 是否已结束。
  ///
  /// @return 不是 `PENDING` 就是结束了
  public boolean finished() {
    return this != PENDING;
  }
}

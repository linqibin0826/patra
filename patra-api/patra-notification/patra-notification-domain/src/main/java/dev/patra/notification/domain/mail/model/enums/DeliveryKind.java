package dev.patra.notification.domain.mail.model.enums;

/// 提醒邮件的投递种类。
public enum DeliveryKind {

  /// 离站时逐条：同一类里一起等着的几条合成一封。
  INSTANT,

  /// 每日汇总：北京时间 8:00 列出过去 24 小时里仍未读的。
  DIGEST
}

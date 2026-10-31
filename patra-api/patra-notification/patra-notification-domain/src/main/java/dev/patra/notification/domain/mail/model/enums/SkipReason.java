package dev.patra.notification.domain.mail.model.enums;

/// 一封提醒邮件为什么没发（spec 5.3、5.4）。
public enum SkipReason {

  /// 这一类的档位不是逐条，或汇总里已经没有设为每日汇总的类别。
  MODE,

  /// 要列的提醒都已读、已删除、出现之后上过站，或者都没有落点。
  NOTHING_UNREAD,

  /// 收件人不该收：identity 不认识、账号不是正常状态、邮箱未验证或为空。
  RECIPIENT,

  /// 发信端口报这封信对这个收件人永久失败。
  REJECTED
}

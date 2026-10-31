package dev.patra.notification.app.mail.usecase.security;

/// 一次处理的结果。
public enum SendSecurityMailResult {
  /// 发出去了。
  SENT,
  /// 这封已经发过（或已判过期），这次重复投递直接跳过。
  DUPLICATE,
  /// 只为链接而发的信，链接已过期，不发。
  LINK_EXPIRED,
  /// 服务商永久拒收（或信组不出来），不再重试。
  REJECTED
}

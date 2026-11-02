package dev.patra.analytics.simulator.model;

/// 会话的真实身份（模拟器知道，管线要自己判断）。
public enum SessionKind {
  /// 真人
  HUMAN,
  /// 声明身份的爬虫
  DECLARED_CRAWLER,
  /// 伪装成浏览器的爬虫
  STEALTH_CRAWLER,
  /// 不跑脚本的访客
  NOSCRIPT
}

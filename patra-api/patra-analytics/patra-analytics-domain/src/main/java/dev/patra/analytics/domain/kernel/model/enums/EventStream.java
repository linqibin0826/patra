package dev.patra.analytics.domain.kernel.model.enums;

/// 采集结果写往哪里。
public enum EventStream {
  /// 合格的富化事件（Kafka `analytics.events.enriched`）
  ENRICHED,
  /// 不合格的事件与原因（Kafka `analytics.events.rejected`）
  REJECTED
}

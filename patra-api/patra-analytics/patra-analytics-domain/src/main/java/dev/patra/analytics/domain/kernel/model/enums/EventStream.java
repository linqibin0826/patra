package dev.patra.analytics.domain.kernel.model.enums;

/// 采集结果写往哪里。
public enum EventStream {
  /// 合格的富化事件（Kafka `events.raw`）
  RAW,
  /// 不合格的事件与原因（Kafka `events.dlq`）
  DLQ
}

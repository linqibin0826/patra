package dev.patra.analytics.domain.insight.model.enums;

/// 「看过这篇的人还看了」一篇的来源。
public enum AlsoReadSource {

  /// 常被同一批人同一天一起读（共现快照）。
  CO_READ,

  /// 补位：主要主题词相同。
  SAME_MESH,

  /// 补位：同一本期刊。
  SAME_VENUE
}

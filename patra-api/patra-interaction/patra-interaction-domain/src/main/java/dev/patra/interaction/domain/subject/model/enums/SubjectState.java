package dev.patra.interaction.domain.subject.model.enums;

/// 登记对象在来源里的状态。
public enum SubjectState {
  /// 来源里存在且正常。
  ACTIVE,
  /// 来源里软删或整行消失；可以复活。
  UNAVAILABLE
}

package dev.patra.notification.domain.inbox.model.enums;

/// 单条标已读的结果。
public enum MarkReadOutcome {

  /// 不存在，或不是这个收件人的。
  NOT_FOUND,

  /// 本来就已读（已读时刻不变）。
  ALREADY_READ,

  /// 这次从未读变成已读。
  MARKED
}

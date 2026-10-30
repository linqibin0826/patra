package dev.patra.notification.domain.inbox.exception;

import dev.patra.tool.core.error.trait.ErrorTrait;

/// inbox 子域的原因码（放进 ProblemDetail 的 `traits[]`）。
public enum InboxErrorTrait implements ErrorTrait {

  /// 提醒不存在，或不是自己的。
  NOTIFICATION_NOT_FOUND
}

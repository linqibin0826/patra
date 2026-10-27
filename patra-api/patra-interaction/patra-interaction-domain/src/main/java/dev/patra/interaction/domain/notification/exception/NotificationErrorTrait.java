package dev.patra.interaction.domain.notification.exception;

import dev.patra.tool.core.error.trait.ErrorTrait;

/// notification 子域的原因码（放进 ProblemDetail 的 `traits[]`）。
public enum NotificationErrorTrait implements ErrorTrait {

  /// 提醒不存在，或不是自己的。
  NOTIFICATION_NOT_FOUND
}

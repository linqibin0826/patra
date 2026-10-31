package dev.patra.notification.domain.inbox.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 提醒不存在或不是自己的（404）：别人的提醒与已被清理的提醒同样对待，不区分。
public final class NotificationNotFoundException extends DomainException {

  /// 创建异常。
  public NotificationNotFoundException() {
    super("提醒不存在", StandardErrorTrait.NOT_FOUND, InboxErrorTrait.NOTIFICATION_NOT_FOUND);
  }
}

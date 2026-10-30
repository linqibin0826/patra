package dev.patra.notification.domain.inbox.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 请求参数不合法（422）：标签、分页游标。
public final class InvalidInboxRequestException extends DomainException {

  /// 创建异常。
  ///
  /// @param message 说明
  public InvalidInboxRequestException(String message) {
    super(message, StandardErrorTrait.RULE_VIOLATION);
  }
}

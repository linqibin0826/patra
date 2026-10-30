package dev.patra.notification.domain.preference.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 改偏好的请求不合法（422）：类别不是「回复我的」「@ 我的」、档位不认识。文案固定，不拼请求参数。
public final class InvalidPreferenceRequestException extends DomainException {

  /// 创建异常。
  ///
  /// @param message 说明
  public InvalidPreferenceRequestException(String message) {
    super(message, StandardErrorTrait.RULE_VIOLATION);
  }
}

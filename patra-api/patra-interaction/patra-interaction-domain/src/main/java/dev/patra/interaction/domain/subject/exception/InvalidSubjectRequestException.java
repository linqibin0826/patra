package dev.patra.interaction.domain.subject.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 底座接口的请求参数不合法（422）：批量对象统计的类型不支持、key 个数越界等。
public final class InvalidSubjectRequestException extends DomainException {

  /// 创建异常。
  ///
  /// @param message 说明
  public InvalidSubjectRequestException(String message) {
    super(message, StandardErrorTrait.RULE_VIOLATION);
  }
}

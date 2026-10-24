package dev.patra.interaction.domain.collection.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 请求参数不合法（422）：未知的视图、排序、可见性、对象类型，批量个数越界等。
public final class InvalidCollectionRequestException extends DomainException {

  /// 创建异常。
  ///
  /// @param message 说明
  public InvalidCollectionRequestException(String message) {
    super(message, StandardErrorTrait.RULE_VIOLATION);
  }
}

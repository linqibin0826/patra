package dev.patra.analytics.domain.collect.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 请求体超过上限：整批拒收，返回 413。
public final class BatchTooLargeException extends DomainException {

  /// 创建异常。
  ///
  /// @param message 说明
  public BatchTooLargeException(String message) {
    super(message, StandardErrorTrait.RULE_VIOLATION);
  }
}

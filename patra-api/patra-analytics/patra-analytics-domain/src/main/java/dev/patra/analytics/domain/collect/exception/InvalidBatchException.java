package dev.patra.analytics.domain.collect.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 请求体不是合法的采集批次（不是 JSON、不符合 `collect-batch`）：整批拒收，返回 400。
public final class InvalidBatchException extends DomainException {

  /// 创建异常。
  ///
  /// @param message 说明
  public InvalidBatchException(String message) {
    super(message, StandardErrorTrait.RULE_VIOLATION);
  }
}

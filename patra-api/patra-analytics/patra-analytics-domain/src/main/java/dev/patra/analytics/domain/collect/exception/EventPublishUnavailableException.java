package dev.patra.analytics.domain.collect.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// Kafka 写不进去（连不上、超时、被拒）：整批返回 503，门户丢弃、模拟器重试。
public final class EventPublishUnavailableException extends DomainException {

  /// 创建异常。
  ///
  /// @param message 说明
  public EventPublishUnavailableException(String message) {
    super(message, StandardErrorTrait.DEP_UNAVAILABLE);
  }

  /// 创建异常并保留原因。
  ///
  /// @param message 说明
  /// @param cause 原始异常
  public EventPublishUnavailableException(String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}

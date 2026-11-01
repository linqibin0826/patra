package dev.patra.analytics.domain.ingest.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// ClickHouse 写不进去但重试可能会好（连不上、超时、5xx、磁盘不够）：不提交偏移量，同一批按退避重试。
public final class IngestUnavailableException extends DomainException {

  /// 创建异常并保留原因。
  ///
  /// @param message 说明
  /// @param cause 原始异常
  public IngestUnavailableException(String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}

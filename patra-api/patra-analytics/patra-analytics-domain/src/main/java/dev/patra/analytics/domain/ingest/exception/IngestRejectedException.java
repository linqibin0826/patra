package dev.patra.analytics.domain.ingest.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// ClickHouse 拒绝这批数据（解析失败、类型不对等，重试也不会好）：二分定位坏行，坏行转 dlq。
public final class IngestRejectedException extends DomainException {

  /// 创建异常并保留原因。
  ///
  /// @param message 说明（ClickHouse 的报错原文，转 dlq 时写进 reason_detail）
  /// @param cause 原始异常
  public IngestRejectedException(String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.RULE_VIOLATION);
  }
}

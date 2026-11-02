package dev.patra.analytics.domain.insight.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 算数字要用的依赖不可用（ClickHouse、PG 快照、catalog）：接口回 503，任务失败、旧快照保留。
public final class InsightUnavailableException extends DomainException {

  /// 创建异常并保留原因。
  ///
  /// @param message 说明
  /// @param cause 原始异常
  public InsightUnavailableException(String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}

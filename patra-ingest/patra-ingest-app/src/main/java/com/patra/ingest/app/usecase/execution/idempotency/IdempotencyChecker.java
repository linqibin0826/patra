package com.patra.ingest.app.usecase.execution.idempotency;

/// 幂等性检查器。
///
/// 检查任务是否已经成功(通过 idempotentKey + status)。
///
/// @author linqibin
/// @since 0.1.0
public interface IdempotencyChecker {

  /// 检查任务是否已成功执行。
  ///
  /// @param taskId 任务 ID
  /// @param idempotentKey 幂等键
  /// @return true 如果已成功,无需再次执行
  boolean isAlreadySucceeded(Long taskId, String idempotentKey);
}

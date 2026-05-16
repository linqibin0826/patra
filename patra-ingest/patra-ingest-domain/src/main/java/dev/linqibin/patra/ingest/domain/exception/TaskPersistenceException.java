package dev.linqibin.patra.ingest.domain.exception;

import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 任务持久化异常。
///
/// 用于 Task、TaskRunBatch 等数据批量插入或更新失败的场景。
/// 携带 `DEP_UNAVAILABLE` 特征表示依赖（数据库）不可用。
///
/// @author linqibin
/// @since 0.1.0
public class TaskPersistenceException extends IngestException {

  /// 使用消息构造任务持久化异常。
  ///
  /// @param message 详细消息
  public TaskPersistenceException(String message) {
    super(message, StandardErrorTrait.DEP_UNAVAILABLE);
  }

  /// 使用消息和根本原因构造任务持久化异常。
  ///
  /// @param message 详细消息
  /// @param cause 根本原因
  public TaskPersistenceException(String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}

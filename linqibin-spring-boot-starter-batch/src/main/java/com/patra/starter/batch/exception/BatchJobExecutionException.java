package com.patra.starter.batch.exception;

import com.patra.common.error.ApplicationException;

/// 批处理任务执行异常。
///
/// 当 Spring Batch Job 执行失败时抛出此异常。继承自 `ApplicationException`，集成 patra-common-core 的统一错误处理框架。
///
/// @author Patra Team
/// @since 1.0.0
public class BatchJobExecutionException extends ApplicationException {

  /// 使用 Job 名称和根本原因创建异常。
  ///
  /// @param jobName Job 名称
  /// @param cause 根本原因
  public BatchJobExecutionException(String jobName, Throwable cause) {
    super(BatchErrorCode.JOB_EXECUTION_FAILED, String.format("批处理任务执行失败: %s", jobName), cause);
  }
}

package com.patra.starter.batch.exception;

import com.patra.common.error.codes.ErrorCodeLike;
import lombok.RequiredArgsConstructor;

/// 批处理错误码枚举。
///
/// 集成 patra-common-core 的统一错误处理框架。
///
/// @author Patra Team
/// @since 1.0.0
@RequiredArgsConstructor
public enum BatchErrorCode implements ErrorCodeLike {

  /// 批处理任务执行失败。
  JOB_EXECUTION_FAILED("BATCH_001", 500),

  /// 批处理任务已在运行中。
  JOB_ALREADY_RUNNING("BATCH_002", 409),

  /// 批处理任务不存在。
  JOB_NOT_FOUND("BATCH_003", 404);

  /// 错误码。
  private final String codeValue;

  /// HTTP 状态码。
  private final int httpStatusValue;

  /// 返回错误码值。
  ///
  /// @return 错误码
  @Override
  public String code() {
    return codeValue;
  }

  /// 返回 HTTP 状态码。
  ///
  /// @return HTTP 状态码
  @Override
  public int httpStatus() {
    return httpStatusValue;
  }
}

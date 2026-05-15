package com.patra.starter.redisson.exception;

import com.patra.common.error.codes.ErrorCodeLike;
import lombok.RequiredArgsConstructor;

/// 分布式锁错误码枚举。
///
/// 错误码格式: `LOCK-{NNNN}`，遵循项目统一错误码规范：
///
/// - 0xxx: 通用 HTTP 错误（状态码映射）
/// - 1xxx: 业务特定错误
///
/// @author Patra Team
/// @since 1.0.0
@RequiredArgsConstructor
public enum LockErrorCode implements ErrorCodeLike {

  /// 锁获取失败（资源冲突）
  ACQUISITION_FAILED("LOCK-0409", 409),

  /// 锁操作超时
  TIMEOUT("LOCK-1001", 500),

  /// Redis 基础设施错误
  INFRASTRUCTURE_ERROR("LOCK-0503", 503),

  /// SpEL 表达式解析错误
  EXPRESSION_ERROR("LOCK-1002", 500);

  /// 错误码
  private final String codeValue;

  /// HTTP 状态码
  private final int httpStatusValue;

  /// 获取错误码。
  ///
  /// @return 错误码字符串
  @Override
  public String code() {
    return codeValue;
  }

  /// 获取 HTTP 状态码。
  ///
  /// @return HTTP 状态码
  @Override
  public int httpStatus() {
    return httpStatusValue;
  }
}

package com.patra.starter.redisson.exception;

import com.patra.common.error.codes.ErrorCodeLike;
import lombok.RequiredArgsConstructor;

/// 分布式锁错误码枚举。
///
/// 集成 patra-common-core 的统一错误处理框架
///
/// @author Patra Team
/// @since 1.0.0
@RequiredArgsConstructor
public enum LockErrorCode implements ErrorCodeLike {

    /// 锁获取失败
    ACQUISITION_FAILED("LOCK_001", 409),

    /// 锁操作超时
    TIMEOUT("LOCK_002", 500),

    /// Redis 基础设施错误
    INFRASTRUCTURE_ERROR("LOCK_003", 503),

    /// SpEL 表达式解析错误
    EXPRESSION_ERROR("LOCK_004", 500);

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

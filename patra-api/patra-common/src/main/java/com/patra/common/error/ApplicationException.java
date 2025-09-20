package com.patra.common.error;

import com.patra.common.error.codes.ErrorCodeLike;

/**
 * 应用层异常：承载业务错误码。
 *
 * <p>用于在应用层封装领域异常，或表示带结构化错误码的应用错误。
 * 内嵌的 ErrorCodeLike 将用于错误解析算法判定 HTTP 状态与错误响应。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class ApplicationException extends RuntimeException {
    
    /** 本异常关联的业务错误码 */
    private final ErrorCodeLike errorCode;
    
    /** 构造函数（错误码 + 消息）。 */
    public ApplicationException(ErrorCodeLike errorCode, String message) {
        super(message);
        if (errorCode == null) {
            throw new IllegalArgumentException("ErrorCode cannot be null");
        }
        this.errorCode = errorCode;
    }
    
    /** 构造函数（错误码 + 消息 + 原因）。 */
    public ApplicationException(ErrorCodeLike errorCode, String message, Throwable cause) {
        super(message, cause);
        if (errorCode == null) {
            throw new IllegalArgumentException("ErrorCode cannot be null");
        }
        this.errorCode = errorCode;
    }
    
    /** 返回关联的业务错误码。 */
    public ErrorCodeLike getErrorCode() {
        return errorCode;
    }
}

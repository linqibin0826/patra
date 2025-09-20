package com.patra.starter.core.error.model;

import com.patra.common.error.codes.ErrorCodeLike;

/**
 * 异常解析结果：包含业务错误码与 HTTP 状态码。
 *
 * <p>由错误解析算法返回，用于携带错误码与 HTTP 状态的组合结果。</p>
 *
 * @param errorCode 解析得到的业务错误码，不能为空
 * @param httpStatus 解析得到的 HTTP 状态码（int）
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ErrorResolution(
    ErrorCodeLike errorCode,
    int httpStatus
) {
    
    /**
     * 构造函数，校验参数合法性。
     *
     * @param errorCode 错误码，不能为空
     * @param httpStatus HTTP 状态码，范围 100-599
     * @throws IllegalArgumentException 当参数非法
     */
    public ErrorResolution {
        if (errorCode == null) {
            throw new IllegalArgumentException("Error code must not be null");
        }
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("HTTP status must be between 100 and 599, got: " + httpStatus);
        }
    }
}

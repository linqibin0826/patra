package com.patra.starter.web.resp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
/**
 * 通用 API 响应包装器。
 *
 * <p>提供统一的成功/失败响应结构，包含时间戳与可选数据体。</p>
 */
public class ApiResponse<T> {

    private final boolean success;

    private final int code;

    private final String message;

    private final T data;

    private final Instant timestamp = Instant.now();


    private ApiResponse(boolean success, int code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 创建成功响应。
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, ResultCode.OK.getCode(), ResultCode.OK.getMessage(), data);
    }

    /**
     * 创建失败响应（业务错误）。
     */
    public static <T> ApiResponse<T> failure(ResultCode code, String message) {
        return new ApiResponse<>(false, code.getCode(), message == null ? code.getMessage() : message, null);
    }

    /**
     * 创建错误响应（临时兼容方法）
     *
     * @param code    HTTP状态码
     * @param message 错误消息
     * @return 错误响应
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }

}

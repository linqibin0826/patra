package com.patra.starter.feign.error.util;

import com.patra.starter.feign.error.exception.RemoteCallException;

/**
 * {@link com.patra.starter.feign.error.exception.RemoteCallException} 辅助工具类。
 *
 * <p>提供对常见 HTTP 状态与错误模式的语义判断，便于适配器层进行简洁的错误分支处理。
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.starter.feign.error.exception.RemoteCallException
 */
public final class RemoteErrorHelper {
    
    private RemoteErrorHelper() { }
    
    /**
     * 是否为“未找到”语义（HTTP 404 或错误码以 -0404 结尾）。
     *
     * @param ex 远端调用异常
     * @return 是则 true，否则 false
     */
    public static boolean isNotFound(RemoteCallException ex) {
        return ex.getHttpStatus() == 404 || 
               (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0404"));
    }
    
    /**
     * 是否为“冲突”语义（HTTP 409 或错误码以 -0409 结尾）。
     *
     * @param ex 远端调用异常
     * @return 是则 true，否则 false
     */
    public static boolean isConflict(RemoteCallException ex) {
        return ex.getHttpStatus() == 409 || 
               (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0409"));
    }
    
    /**
     * 是否为“未授权”语义（HTTP 401 或错误码以 -0401 结尾）。
     *
     * @param ex 远端调用异常
     * @return 是则 true，否则 false
     */
    public static boolean isUnauthorized(RemoteCallException ex) {
        return ex.getHttpStatus() == 401 || 
               (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0401"));
    }
    
    /**
     * 是否为“禁止访问”语义（HTTP 403 或错误码以 -0403 结尾）。
     *
     * @param ex 远端调用异常
     * @return 是则 true，否则 false
     */
    public static boolean isForbidden(RemoteCallException ex) {
        return ex.getHttpStatus() == 403 || 
               (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0403"));
    }
    
    /**
     * 是否为“不可处理实体”语义（HTTP 422 或错误码以 -0422 结尾）。
     *
     * @param ex 远端调用异常
     * @return 是则 true，否则 false
     */
    public static boolean isUnprocessableEntity(RemoteCallException ex) {
        return ex.getHttpStatus() == 422 || 
               (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0422"));
    }
    
    /**
     * 是否为“请求过多”语义（HTTP 429 或错误码以 -0429 结尾）。
     *
     * @param ex 远端调用异常
     * @return 是则 true，否则 false
     */
    public static boolean isTooManyRequests(RemoteCallException ex) {
        return ex.getHttpStatus() == 429 || 
               (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0429"));
    }
    
    /**
     * 是否为客户端错误（4xx）。
     *
     * @param ex 远端调用异常
     * @return 是则 true，否则 false
     */
    public static boolean isClientError(RemoteCallException ex) {
        return ex.getHttpStatus() >= 400 && ex.getHttpStatus() < 500;
    }
    
    /**
     * 是否为服务端错误（5xx）。
     *
     * @param ex 远端调用异常
     * @return 是则 true，否则 false
     */
    public static boolean isServerError(RemoteCallException ex) {
        return ex.getHttpStatus() >= 500 && ex.getHttpStatus() < 600;
    }
    
    /**
     * 错误码是否等于指定值（精确匹配）。
     *
     * @param ex 远端调用异常
     * @param errorCode 期望错误码
     * @return 匹配则 true，否则 false
     */
    public static boolean is(RemoteCallException ex, String errorCode) {
        return errorCode != null && errorCode.equals(ex.getErrorCode());
    }
    
    /**
     * 是否包含任何业务错误码。
     *
     * @param ex 远端调用异常
     * @return 含有非空错误码则 true，否则 false
     */
    public static boolean hasErrorCode(RemoteCallException ex) {
        return ex.hasErrorCode();
    }
    
    /**
     * 是否包含 TraceId。
     *
     * @param ex 远端调用异常
     * @return 含有非空 TraceId 则 true，否则 false
     */
    public static boolean hasTraceId(RemoteCallException ex) {
        return ex.hasTraceId();
    }
    
    /**
     * 错误码是否属于给定集合之一。
     *
     * @param ex 远端调用异常
     * @param errorCodes 候选错误码集合
     * @return 匹配任一则 true，否则 false
     */
    public static boolean isAnyOf(RemoteCallException ex, String... errorCodes) {
        if (errorCodes == null || errorCodes.length == 0) {
            return false;
        }
        
        String actualCode = ex.getErrorCode();
        if (actualCode == null) {
            return false;
        }
        
        for (String code : errorCodes) {
            if (actualCode.equals(code)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 是否属于可重试的临时性失败（如 5xx、429/408/503/504 等）。
     *
     * @param ex 远端调用异常
     * @return 可能可重试则 true，否则 false
     */
    public static boolean isRetryable(RemoteCallException ex) {
        int status = ex.getHttpStatus();
        return isServerError(ex) || 
               status == 429 || // Too Many Requests
               status == 408 || // Request Timeout
               status == 503 || // Service Unavailable
               status == 504;   // Gateway Timeout
    }
}

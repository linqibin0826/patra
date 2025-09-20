package com.patra.starter.feign.error.metrics;

/**
 * Feign 错误处理指标采集接口。
 *
 * <p>用于跟踪 Feign 错误解码的性能、成功率、TraceId 提取、响应体读取与内容类型识别等。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface FeignErrorMetrics {
    
    /**
     * 记录 ProblemDetail 解析的成功与失败。
     *
     * @param methodKey Feign 方法键
     * @param httpStatus HTTP 状态码
     * @param success 是否解析成功
     * @param parseTimeMs 解析耗时（毫秒）
     */
    void recordProblemDetailParsing(String methodKey, int httpStatus, boolean success, long parseTimeMs);
    
    /**
     * 记录整体错误解码的成功情况。
     *
     * @param methodKey Feign 方法键
     * @param httpStatus HTTP 状态码
     * @param decodingSuccess 解码是否成功
     * @param tolerantMode 是否使用了宽容模式
     */
    void recordErrorDecodingSuccess(String methodKey, int httpStatus, boolean decodingSuccess, boolean tolerantMode);
    
    /**
     * 记录从响应头提取 TraceId 的情况。
     *
     * @param methodKey Feign 方法键
     * @param traceIdFound 是否找到 TraceId
     * @param headerUsed 若找到则为对应的响应头名
     */
    void recordTraceIdExtraction(String methodKey, boolean traceIdFound, String headerUsed);
    
    /**
     * 记录响应体读取的性能与问题。
     *
     * @param methodKey Feign 方法键
     * @param bodySize 响应体大小（字节）
     * @param readTimeMs 读取耗时（毫秒）
     * @param truncated 是否因大小限制被截断
     */
    void recordResponseBodyReading(String methodKey, int bodySize, long readTimeMs, boolean truncated);
    
    /**
     * 记录 ProblemDetail 内容类型识别。
     *
     * @param methodKey Feign 方法键
     * @param contentType 检测到的 Content-Type
     * @param isProblemDetail 是否被识别为 ProblemDetail
     */
    void recordContentTypeDetection(String methodKey, String contentType, boolean isProblemDetail);
}

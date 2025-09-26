package com.patra.starter.feign.error.exception;

import com.patra.common.error.problem.ErrorKeys;
import lombok.Getter;
import org.springframework.http.ProblemDetail;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 通过 Feign 调用远端服务时产生的错误所对应的异常类型。
 *
 * <p>定位：仅用于适配器层（adapter），不应穿透至应用/领域层。
 *
 * <p>能力：可结构化地访问远端错误信息，包括业务错误码、HTTP 状态码、TraceId，
 * 以及 {@link org.springframework.http.ProblemDetail} 的扩展属性集合。
 *
 * <p>相关：
 * - 由 {@link com.patra.starter.feign.error.decoder.ProblemDetailErrorDecoder} 负责构造
 * - 可搭配 {@link com.patra.starter.feign.error.util.RemoteErrorHelper} 进行语义判断
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
public class RemoteCallException extends RuntimeException {
    
    /** 远端返回的业务错误码（可能为空） */
    private final String errorCode;
    
    /** 远端响应的 HTTP 状态码 */
    private final int httpStatus;
    
    /** 触发本次调用的 Feign 方法键 */
    private final String methodKey;
    
    /** 用于跨服务关联的 TraceId（可能为空） */
    private final String traceId;
    
    /** 从 ProblemDetail 扩展出来的附加属性 */
    private final Map<String, Object> extensions;
    
    /**
     * 基于 {@link ProblemDetail} 构造异常，提取错误码、TraceId 与扩展属性。
     *
     * @param problemDetail 下游服务返回的 ProblemDetail
     * @param methodKey 本次调用的 Feign 方法键
     */
    public RemoteCallException(ProblemDetail problemDetail, String methodKey) {
        super(problemDetail.getDetail());
        this.httpStatus = problemDetail.getStatus();
        this.methodKey = methodKey;
        
        // 从 ProblemDetail 扩展属性中提取错误码与 TraceId
        Map<String, Object> properties = problemDetail.getProperties();
        if (properties == null) {
            properties = Collections.emptyMap();
        }
        this.errorCode = (String) properties.get(ErrorKeys.CODE);
        this.traceId = (String) properties.get(ErrorKeys.TRACE_ID);

        // 复制所有扩展字段，保留以备后续使用
        this.extensions = new HashMap<>(properties);
    }
    
    /**
     * 针对非 ProblemDetail 的错误响应构造异常（例如严格模式回退、宽容模式兜底场景）。
     *
     * @param httpStatus 响应的 HTTP 状态码
     * @param message 错误消息（可能来自响应原因短语）
     * @param methodKey Feign 方法键
     * @param traceId 如可从响应头获取则填充，否则为空
     */
    public RemoteCallException(int httpStatus, String message, String methodKey, String traceId) {
        super(message);
        this.httpStatus = httpStatus;
        this.methodKey = methodKey;
        this.traceId = traceId;
        this.errorCode = null;
        this.extensions = Collections.emptyMap();
    }
    
    /**
     * 完整参数构造函数，适用于需要显式设置全部字段的高级用法。
     *
     * @param errorCode 业务错误码（可空）
     * @param httpStatus HTTP 状态码
     * @param message 错误消息
     * @param methodKey Feign 方法键
     * @param traceId TraceId（可空）
     * @param extensions ProblemDetail 扩展属性（可为空/空集）
     */
    public RemoteCallException(String errorCode, int httpStatus, String message, 
                             String methodKey, String traceId, Map<String, Object> extensions) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.methodKey = methodKey;
        this.traceId = traceId;
        this.extensions = extensions != null ? new HashMap<>(extensions) : Collections.emptyMap();
    }
    
    /**
     * 是否包含业务错误码。
     *
     * @return 含有非空错误码返回 true，否则返回 false
     */
    public boolean hasErrorCode() {
        return errorCode != null && !errorCode.trim().isEmpty();
    }
    
    /**
     * 是否包含 TraceId。
     *
     * @return 含有非空 TraceId 返回 true，否则返回 false
     */
    public boolean hasTraceId() {
        return traceId != null && !traceId.trim().isEmpty();
    }
    
    /**
     * 按 key 读取扩展属性值。
     *
     * @param key 扩展属性键
     * @return 对应的扩展值；不存在则为 null
     */
    public Object getExtension(String key) {
        return extensions.get(key);
    }
    
    /**
     * 带类型转换地按 key 读取扩展属性值。
     *
     * @param key 扩展属性键
     * @param type 期望返回值类型
     * @param <T> 类型参数
     * @return 转换后的值；不存在或类型不匹配时为 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtension(String key, Class<T> type) {
        Object value = extensions.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * 返回扩展属性的不可变拷贝。
     *
     * @return 不可变映射视图
     */
    public Map<String, Object> getAllExtensions() {
        return Collections.unmodifiableMap(extensions);
    }
}

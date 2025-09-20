package com.patra.starter.core.error.spi;

import java.util.Optional;

/**
 * 从当前执行上下文提取 TraceId 的提供者接口。
 *
 * <p>可支持多种追踪系统与上下文（MDC、请求头等）。
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.starter.core.error.trace.HeaderBasedTraceProvider
 */
public interface TraceProvider {
    
    /**
     * 从执行上下文中获取当前 TraceId。
     *
     * @return 若可用则返回 TraceId，否则为空
     */
    Optional<String> getCurrentTraceId();
}

package com.patra.starter.core.error.pipeline.interceptor;

import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.pipeline.ResolutionInterceptor;
import com.patra.starter.core.error.pipeline.ResolutionInvocation;
import com.patra.starter.core.error.spi.TraceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * 追踪拦截器：记录当前 TraceId，便于日志关联。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TracingInterceptor implements ResolutionInterceptor {

    private final TraceProvider traceProvider;

    public TracingInterceptor(TraceProvider traceProvider) {
        this.traceProvider = traceProvider;
    }

    @Override
    public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
        traceProvider.getCurrentTraceId().ifPresent(traceId ->
                log.debug("解析异常时附带 TraceId: {}", traceId));
        return invocation.proceed(exception);
    }
}

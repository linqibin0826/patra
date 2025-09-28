package com.patra.starter.core.error.pipeline;

import com.patra.starter.core.error.engine.ErrorResolutionEngine;
import com.patra.starter.core.error.model.ErrorResolution;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 错误解析管线，按照拦截器顺序执行最终得到统一错误表示。
 */
public class ErrorResolutionPipeline {

    private final ErrorResolutionEngine engine;
    private final List<ResolutionInterceptor> interceptors;

    public ErrorResolutionPipeline(ErrorResolutionEngine engine,
                                   List<ResolutionInterceptor> interceptors) {
        this.engine = engine;
        if (interceptors == null || interceptors.isEmpty()) {
            this.interceptors = Collections.emptyList();
        } else {
            List<ResolutionInterceptor> ordered = new ArrayList<>(interceptors);
            AnnotationAwareOrderComparator.sort(ordered);
            this.interceptors = Collections.unmodifiableList(ordered);
        }
    }

    /**
     * 执行解析管线。
     */
    public ErrorResolution resolve(Throwable exception) {
        ResolutionInvocation invocation = buildInvocationChain();
        return invocation.proceed(exception);
    }

    private ResolutionInvocation buildInvocationChain() {
        ResolutionInvocation tail = engine::resolve;
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            ResolutionInterceptor interceptor = interceptors.get(i);
            ResolutionInvocation next = tail;
            tail = ex -> interceptor.intercept(ex, next);
        }
        return tail;
    }

    public List<ResolutionInterceptor> getInterceptors() {
        return interceptors;
    }
}

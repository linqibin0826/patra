package com.patra.starter.core.error.pipeline;

import com.patra.starter.core.error.model.ErrorResolution;

/**
 * 解析拦截器，通过组合实现熔断、指标等横切能力。
 */
public interface ResolutionInterceptor {

    /**
     * 执行拦截逻辑。
     *
     * @param exception 待解析的异常
     * @param invocation 调用链
     * @return 解析结果
     */
    ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation);
}

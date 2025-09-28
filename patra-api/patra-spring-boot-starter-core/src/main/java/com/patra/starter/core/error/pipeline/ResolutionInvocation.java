package com.patra.starter.core.error.pipeline;

import com.patra.starter.core.error.model.ErrorResolution;

/**
 * 解析调用链，用于在拦截器之间传递执行控制权。
 */
@FunctionalInterface
public interface ResolutionInvocation {

    /**
     * 执行链路中的下一个步骤。
     *
     * @param exception 当前处理的异常
     * @return 解析结果
     */
    ErrorResolution proceed(Throwable exception);
}

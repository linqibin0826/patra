package com.patra.starter.core.error.engine;

import com.patra.starter.core.error.model.ErrorResolution;

/**
 * 错误解析引擎接口，负责将任意异常转换为平台统一的错误码与 HTTP 状态。
 *
 * @author linqibin
 * @since 0.2.0
 */
public interface ErrorResolutionEngine {

    /**
     * 解析异常并返回平台统一的错误表示。
     *
     * @param exception 待解析的异常，不能为空
     * @return 解析结果
     */
    ErrorResolution resolve(Throwable exception);
}

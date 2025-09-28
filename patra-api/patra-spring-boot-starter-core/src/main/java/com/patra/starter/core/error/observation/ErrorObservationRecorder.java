package com.patra.starter.core.error.observation;

import com.patra.starter.core.error.model.ErrorResolution;

/**
 * 错误解析观测记录接口，用于输出指标或遥测数据。
 */
public interface ErrorObservationRecorder {

    /**
     * 记录一次解析流程的耗时与结果。
     *
     * @param exception 原始异常
     * @param resolution 解析结果
     * @param durationMs 耗时（毫秒）
     * @param slow 是否超过慢调用阈值
     */
    void recordResolution(Throwable exception, ErrorResolution resolution, long durationMs, boolean slow);

    /**
     * 记录熔断导致的兜底解析。
     */
    void recordCircuitBreakerFallback(Throwable exception);

    /**
     * 空实现，便于在未启用观测时注入。
     */
    ErrorObservationRecorder NO_OP = new ErrorObservationRecorder() {
        @Override
        public void recordResolution(Throwable exception, ErrorResolution resolution, long durationMs, boolean slow) {
            // no-op
        }

        @Override
        public void recordCircuitBreakerFallback(Throwable exception) {
            // no-op
        }
    };
}

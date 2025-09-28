package com.patra.starter.feign.error.observation;

/**
 * Feign 错误解码观测记录接口。
 */
public interface FeignErrorObservationRecorder {

    /** 空实现，便于在未启用观测时降级。 */
    FeignErrorObservationRecorder NO_OP = new FeignErrorObservationRecorder() {
        @Override
        public void recordProblemDetailParsing(String methodKey, int status, long durationMs, boolean success) {
            // no-op
        }

        @Override
        public void recordDecodingOutcome(String methodKey, int status, boolean success, boolean tolerantMode) {
            // no-op
        }

        @Override
        public void recordResponseBodyRead(String methodKey, int bodySize, long durationMs, boolean truncated) {
            // no-op
        }

        @Override
        public void recordTraceIdExtraction(String methodKey, boolean found, String headerName) {
            // no-op
        }
    };

    /**
     * 记录 ProblemDetail 解析情况。
     */
    void recordProblemDetailParsing(String methodKey, int status, long durationMs, boolean success);

    /**
     * 记录整体解码结果。
     */
    void recordDecodingOutcome(String methodKey, int status, boolean success, boolean tolerantMode);

    /**
     * 记录响应体读取情况。
     */
    void recordResponseBodyRead(String methodKey, int bodySize, long durationMs, boolean truncated);

    /**
     * 记录 TraceId 提取情况。
     */
    void recordTraceIdExtraction(String methodKey, boolean found, String headerName);
}

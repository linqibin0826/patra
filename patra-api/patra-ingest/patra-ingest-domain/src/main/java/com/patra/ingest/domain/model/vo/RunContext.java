package com.patra.ingest.domain.model.vo;

/**
 * Task Run 上下文信息（调度运行 ID、链路追踪 ID）。
 */
public record RunContext(String schedulerRunId, String correlationId) {

    public static RunContext empty() {
        return new RunContext(null, null);
    }

    public RunContext withSchedulerRun(String runId) {
        return new RunContext(runId, correlationId);
    }

    public RunContext withCorrelation(String corrId) {
        return new RunContext(schedulerRunId, corrId);
    }
}

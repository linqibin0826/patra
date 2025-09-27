package com.patra.ingest.domain.model.vo;

/**
 * 任务调度上下文，保留调度器运行标识与链路追踪信息。
 */
public record TaskSchedulerContext(String schedulerRunId, String correlationId) {

    public static TaskSchedulerContext empty() {
        return new TaskSchedulerContext(null, null);
    }

    public TaskSchedulerContext withSchedulerRun(String runId) {
        return new TaskSchedulerContext(runId, correlationId);
    }

    public TaskSchedulerContext withCorrelation(String corrId) {
        return new TaskSchedulerContext(schedulerRunId, corrId);
    }

    public boolean hasSchedulerRun() {
        return schedulerRunId != null && !schedulerRunId.isBlank();
    }
}

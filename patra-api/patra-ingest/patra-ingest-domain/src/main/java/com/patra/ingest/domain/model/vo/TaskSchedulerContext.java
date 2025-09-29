package com.patra.ingest.domain.model.vo;

/**
 * 任务调度上下文（Scheduler Context）。
 * <p>封装调度器一次运行链路标识（runId）与跨组件关联 ID（correlationId）。</p>
 * <ul>
 *   <li>schedulerRunId：同一调度触发批次统一标识（用于聚合统计）</li>
 *   <li>correlationId：跨系统 Trace / 日志串联</li>
 * </ul>
 * 提供不可变便捷变换方法（with*）。
 */
public record TaskSchedulerContext(String schedulerRunId, String correlationId) {

    /**
     * 空上下文（两个标识皆为空）
     */
    public static TaskSchedulerContext empty() {
        return new TaskSchedulerContext(null, null);
    }

    /**
     * 派生一个变更 schedulerRunId 的新上下文。
     */
    public TaskSchedulerContext withSchedulerRun(String runId) {
        return new TaskSchedulerContext(runId, correlationId);
    }

    /**
     * 派生一个变更 correlationId 的新上下文。
     */
    public TaskSchedulerContext withCorrelation(String corrId) {
        return new TaskSchedulerContext(schedulerRunId, corrId);
    }

    /**
     * 是否包含有效的调度运行标识。
     */
    public boolean hasSchedulerRun() {
        return schedulerRunId != null && !schedulerRunId.isBlank();
    }
}

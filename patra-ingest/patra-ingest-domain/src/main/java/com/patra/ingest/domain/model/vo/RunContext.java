package com.patra.ingest.domain.model.vo;

/**
 * Task Run 上下文信息（运行期关联元数据）。
 * <p>与 {@link TaskSchedulerContext} 类似，但聚焦单次 TaskRun 维度。</p>
 * <ul>
 *   <li>schedulerRunId：调度批次标识</li>
 *   <li>correlationId：跨系统追踪 ID</li>
 * </ul>
 */
public record RunContext(String schedulerRunId, String correlationId) {

    /**
     * 空上下文
     */
    public static RunContext empty() {
        return new RunContext(null, null);
    }

    /**
     * 派生新的上下文（更新 schedulerRunId）。
     */
    public RunContext withSchedulerRun(String runId) {
        return new RunContext(runId, correlationId);
    }

    /**
     * 派生新的上下文（更新 correlationId）。
     */
    public RunContext withCorrelation(String corrId) {
        return new RunContext(schedulerRunId, corrId);
    }
}

package com.patra.ingest.domain.model.vo;

/**
 * 游标推进的链路上下文，串联调度/计划/切片/任务/运行/批次。
 */
public record CursorLineage(Long scheduleInstanceId,
                            Long planId,
                            Long sliceId,
                            Long taskId,
                            Long runId,
                            Long batchId) {

    public static CursorLineage empty() {
        return new CursorLineage(null, null, null, null, null, null);
    }
}

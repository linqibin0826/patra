package com.patra.ingest.domain.model.vo;

/**
 * 游标推进链路上下文（Cursor Lineage）。
 * <p>用于记录一次游标推进所关联的层级实体主键，便于溯源与审计。</p>
 * <ul>
 *   <li>scheduleInstanceId → 调度实例</li>
 *   <li>planId → 计划</li>
 *   <li>sliceId → 切片</li>
 *   <li>taskId → 任务</li>
 *   <li>runId → 单次运行（TaskRun）</li>
 *   <li>batchId → 单批次明细（TaskRunBatch）</li>
 * </ul>
 */
public record CursorLineage(Long scheduleInstanceId,
                            Long planId,
                            Long sliceId,
                            Long taskId,
                            Long runId,
                            Long batchId) {

    /**
     * 空上下文（所有层级均为空）。
     */
    public static CursorLineage empty() {
        return new CursorLineage(null, null, null, null, null, null);
    }
}

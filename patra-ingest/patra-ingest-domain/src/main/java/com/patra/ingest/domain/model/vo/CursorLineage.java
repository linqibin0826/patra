package com.patra.ingest.domain.model.vo;

/**
 * Cursor lineage context capturing related entity identifiers for a single advancement.
 * <p>Facilitates traceability and audit across scheduling layers.</p>
 * <ul>
 *   <li>{@code scheduleInstanceId}: scheduling instance id</li>
 *   <li>{@code planId}: plan id</li>
 *   <li>{@code sliceId}: slice id</li>
 *   <li>{@code taskId}: task id</li>
 *   <li>{@code runId}: task run id</li>
 *   <li>{@code batchId}: task run batch id</li>
 * </ul>
 */
public record CursorLineage(Long scheduleInstanceId,
                            Long planId,
                            Long sliceId,
                            Long taskId,
                            Long runId,
                            Long batchId) {

    /**
     * Create an empty lineage context (all levels {@code null}).
     */
    public static CursorLineage empty() {
        return new CursorLineage(null, null, null, null, null, null);
    }
}

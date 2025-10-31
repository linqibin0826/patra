package com.patra.ingest.domain.model.vo.cursor;

/**
 * Cursor lineage context capturing related entity identifiers for a single advancement.
 *
 * <p>Facilitates traceability and audit across scheduling layers.
 *
 * <ul>
 *   <li>{@code scheduleInstanceId}: scheduling instance id
 *   <li>{@code planId}: plan id
 *   <li>{@code sliceId}: slice id
 *   <li>{@code taskId}: task id
 *   <li>{@code runId}: task run id
 *   <li>{@code batchId}: task run batch id
 * </ul>
 */
public record CursorLineage(
    Long scheduleInstanceId, Long planId, Long sliceId, Long taskId, Long runId, Long batchId) {

  /** Create an empty lineage context (all levels {@code null}). */
  public static CursorLineage empty() {
    return new CursorLineage(null, null, null, null, null, null);
  }
}

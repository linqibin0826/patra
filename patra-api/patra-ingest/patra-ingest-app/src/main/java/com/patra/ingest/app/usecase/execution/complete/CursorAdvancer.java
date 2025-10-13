package com.patra.ingest.app.usecase.execution.complete;

import com.patra.ingest.domain.model.vo.ExecutionContext;

/**
 * Cursor advancer interface.
 *
 * <p>Responsibility: advance the cursor watermark based on batch results; uses optimistic locking
 * to avoid conflicts.
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li>Idempotency: use optimistic locking (version) to prevent duplicate advancement.
 *   <li>Atomicity: throw on failure; upstream decides retry policy.
 *   <li>Window-aware: compute new watermark based on WindowSpec strategy.
 *   <li>Namespaces: support GLOBAL/TASK/PLAN granularities.
 * </ul>
 *
 * <p>Error handling:
 *
 * <ul>
 *   <li>Optimistic conflict: throw Spring's OptimisticLockingFailureException.
 *   <li>Cursor missing: create on first advancement.
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface CursorAdvancer {

  /**
   * Advances the cursor watermark.
   *
   * @param context execution context (window/provenance)
   * @param taskId task id (for TASK-granularity cursors)
   * @param runId run id (for audit)
   * @return true when advanced; false when optimistic conflict (retry later)
   */
  boolean advance(ExecutionContext context, Long taskId, Long runId);
}

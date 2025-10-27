package com.patra.ingest.app.usecase.execution.session;

import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.vo.ExecutionContext;

/**
 * Execution context loader.
 *
 * <p>Restores config and expression snapshots (Task → Slice → Plan), validates hashes, and compiles
 * expressions.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ExecutionContextLoader {

  /**
   * Loads execution context (config restore + expression compile).
   *
   * @param taskId task id
   * @param runId run id
   * @return execution context
   */
  ExecutionContext loadContext(Long taskId, Long runId);

  /**
   * Loads execution context (config restore + expression compile) — optimized to avoid reloading
   * Task.
   *
   * @param task task aggregate (already loaded)
   * @param runId run id
   * @return execution context
   */
  ExecutionContext loadContext(TaskAggregate task, Long runId);
}

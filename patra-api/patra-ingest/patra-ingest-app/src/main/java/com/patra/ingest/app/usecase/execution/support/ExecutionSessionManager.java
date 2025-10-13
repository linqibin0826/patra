package com.patra.ingest.app.usecase.execution.support;

import com.patra.ingest.domain.model.aggregate.TaskAggregate;

/**
 * Execution session manager.
 *
 * <p>Creates TaskRun, starts heartbeat renewal, and encapsulates cleanup (stop heartbeat + release
 * lease).
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ExecutionSessionManager {

  /**
   * Creates an execution session (creates TaskRun and starts heartbeat).
   *
   * @param taskId task id
   * @param leaseOwner lease owner
   * @param schedulerRunId scheduler run id
   * @param correlationId correlation id
   * @return execution session
   */
  ExecutionSession createSession(
      Long taskId, String leaseOwner, String schedulerRunId, String correlationId);

  /**
   * Creates an execution session (TaskRun + heartbeat) — optimized to avoid reloading Task.
   *
   * @param task task aggregate (already loaded)
   * @param leaseOwner lease owner
   * @param schedulerRunId scheduler run id
   * @param correlationId correlation id
   * @return execution session
   */
  ExecutionSession createSession(
      TaskAggregate task, String leaseOwner, String schedulerRunId, String correlationId);
}

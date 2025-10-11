package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.TaskRun;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository port for task execution attempts.
 * <p>Persists task run attempts and exposes history per task, allowing the application layer to implement retry
 * compensation, monitoring, and traceability.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TaskRunRepository {

    /**
     * Persist or update a task run attempt.
     *
     * @param run task run entity containing status, metrics, and heartbeat info
     * @return persisted entity, usually with a generated identifier
     */
    TaskRun save(TaskRun run);

    /**
     * Locate the latest attempt for the given task.
     *
     * @param taskId task identifier
     * @return latest run ordered by attempt number descending, or {@link Optional#empty()}
     */
    Optional<TaskRun> findLatest(Long taskId);

    /**
     * Retrieve the complete run history for a task.
     *
     * @param taskId task identifier
     * @return run attempts ordered per implementation (typically ascending attempt number)
     */
    List<TaskRun> findAll(Long taskId);

    /**
     * Return the highest attempt number for the task (to derive the next attempt id).
     *
     * @param taskId task identifier
     * @return highest attempt number, or {@code 0} when no runs exist
     */
    int getLatestAttemptNo(Long taskId);

    /**
     * Retrieve a run attempt by identifier.
     *
     * @param runId run identifier
     * @return run attempt if present
     */
    Optional<TaskRun> findById(Long runId);

    /**
     * Overwrite the checkpoint and refresh the heartbeat.
     *
     * @param runId          run identifier
     * @param checkpointJson checkpoint payload (null/empty clears it)
     * @param now            current timestamp
     * @return whether the update succeeded
     */
    boolean updateCheckpointAndHeartbeat(Long runId, String checkpointJson, Instant now);

    /**
     * Refresh the heartbeat without mutating the checkpoint.
     *
     * @param runId run identifier
     * @param now   current timestamp
     * @return whether the update succeeded
     */
    boolean touchHeartbeat(Long runId, Instant now);

    /**
     * Mark the run as failed and persist error context.
     *
     * @param runId        run identifier
     * @param errorMessage error description
     * @param now          current timestamp
     * @return whether the update succeeded
     */
    boolean markFailed(Long runId, String errorMessage, Instant now);

    /**
     * Check whether the task already has a successful run (idempotency helper).
     *
     * @param taskId task identifier
     * @return {@code true} if a {@code SUCCEEDED} run exists
     */
    boolean hasSucceededRun(Long taskId);
}

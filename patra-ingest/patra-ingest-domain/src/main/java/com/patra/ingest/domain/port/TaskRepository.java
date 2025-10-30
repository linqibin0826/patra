package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository port for task aggregates.
 *
 * <p>Persists tasks together with their plan/slice configuration and exposes plan-scoped queries
 * plus queue statistics to support scheduling and capacity management.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TaskRepository {

  /**
   * Persist or update a single task aggregate.
   *
   * @param task task aggregate containing metadata, execution strategy, and initial state
   * @return persisted aggregate with identifiers populated
   */
  TaskAggregate save(TaskAggregate task);

  /**
   * Persist multiple task aggregates, typically after slicing.
   *
   * @param tasks task aggregates
   * @return persisted aggregates retaining input order
   */
  List<TaskAggregate> saveAll(List<TaskAggregate> tasks);

  /**
   * Retrieve all tasks belonging to a plan.
   *
   * @param planId plan identifier
   * @return tasks for the plan
   */
  List<TaskAggregate> findByPlanId(Long planId);

  /**
   * Retrieve the task associated with a specific slice (enforces 1:1 relationship).
   *
   * <p><b>Note:</b> After refactoring, Slice:Task is now a 1:1 relationship protected by database
   * unique constraint {@code uk_task_slice}. This method returns at most one task.
   *
   * @param sliceId the slice identifier
   * @return the task aggregate if exists, or {@link Optional#empty()}
   */
  Optional<TaskAggregate> findBySliceId(Long sliceId);

  /**
   * Retrieve a task aggregate by identifier.
   *
   * @param taskId task identifier
   * @return aggregate or {@link Optional#empty()}
   */
  Optional<TaskAggregate> findById(Long taskId);

  /**
   * Count tasks in the {@code QUEUED} state.
   *
   * @param provenanceCode provenance filter (nullable)
   * @param operationCode operation filter (nullable)
   * @return queued task count
   */
  long countQueuedTasks(String provenanceCode, String operationCode);

  /**
   * Attempt to acquire a lease via compare-and-set (step 0).
   *
   * <p>Applies to {@code QUEUED} tasks that satisfy schedule and lease takeover conditions:
   *
   * <ul>
   *   <li>{@code status_code='QUEUED' AND idempotent_key=#{idem}}
   *   <li>{@code scheduled_at IS NULL OR scheduled_at <= #{now}}
   *   <li>{@code leased_until IS NULL OR leased_until <= #{now} OR lease_owner=#{owner}}
   * </ul>
   *
   * Updated fields: {@code lease_owner}, {@code leased_until}, {@code lease_count}+1.
   *
   * @param taskId task identifier
   * @param owner lease owner (workerId:execId or execId)
   * @param now current timestamp (UTC)
   * @param ttlSeconds lease time-to-live in seconds
   * @param idempotentKey defensive idempotency key check
   * @return {@code true} if acquired; {@code false} otherwise
   */
  boolean tryAcquireLease(
      Long taskId, String owner, Instant now, int ttlSeconds, String idempotentKey);

  /**
   * Move the task to {@code RUNNING} and refresh the lease (step 1).
   *
   * <p>Precondition: {@code WHERE lease_owner=#{owner}}. Updates {@code status_code='RUNNING'},
   * {@code started_at}, {@code last_heartbeat_at}, {@code leased_until}.
   *
   * @param taskId task identifier
   * @param owner lease owner
   * @param now current timestamp
   * @param ttlSeconds lease time-to-live in seconds
   * @return {@code true} if updated; {@code false} if the lease was lost
   */
  boolean markRunningWithLease(Long taskId, String owner, Instant now, int ttlSeconds);

  /**
   * Renew the lease via heartbeat.
   *
   * <p>Precondition: {@code WHERE lease_owner=#{owner}}. Updates {@code leased_until}, {@code
   * last_heartbeat_at}, {@code lease_count}+1.
   *
   * @param taskId task identifier
   * @param owner lease owner
   * @param now current timestamp
   * @param ttlSeconds lease time-to-live in seconds
   * @return {@code true} if renewed; {@code false} if the lease was lost
   */
  boolean renewLease(Long taskId, String owner, Instant now, int ttlSeconds);

  /**
   * Batch heartbeat renewal for performance.
   *
   * <p>Precondition: {@code WHERE id IN (taskIds) AND lease_owner=#{owner}}. Updates {@code
   * leased_until}, {@code last_heartbeat_at}, {@code lease_count}+1.
   *
   * @param taskIds task identifiers
   * @param owner lease owner
   * @param now current timestamp
   * @param ttlSeconds lease time-to-live in seconds
   * @return count of tasks successfully renewed
   */
  int batchRenewLeases(List<Long> taskIds, String owner, Instant now, int ttlSeconds);
}

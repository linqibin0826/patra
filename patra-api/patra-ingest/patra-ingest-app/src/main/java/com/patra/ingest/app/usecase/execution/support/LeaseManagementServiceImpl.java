package com.patra.ingest.app.usecase.execution.support;

import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.port.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Lease management service implementation.
 * <p>
 * Responsibility: wrap repository operations related to task leases and provide a unified API.
 * </p>
 * <p>
 * Design notes:
 * <ul>
 *   <li>tryAcquireLease: delegate to TaskRepository.tryAcquireLease() for CAS acquisition.</li>
 *   <li>renewLease: delegate to TaskRepository.renewLease().</li>
 *   <li>releaseLease: load aggregate, call releaseLease(), then save.</li>
 *   <li>validateLease: load aggregate and check leaseInfo.owner.</li>
 * </ul>
 * </p>
 * <p>
 * Logging: INFO for key lease operations (acquire, release, validation failures).
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaseManagementServiceImpl implements LeaseManagementService {

    private final TaskRepository taskRepository;
    private final Clock clock;

    /**
     * Attempts to acquire a lease.
     */
    @Override
    public boolean tryAcquireLease(Long taskId, String owner, Duration leaseDuration) {
        // Load task to obtain idempotent key (required by tryAcquireLease)
        TaskAggregate task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found taskId=" + taskId));

        Instant now = clock.instant();
        int ttlSeconds = (int) leaseDuration.toSeconds();
        boolean acquired = taskRepository.tryAcquireLease(
            taskId,
            owner,
            now,
            ttlSeconds,
            task.getIdempotentKey()
        );

        if (acquired) {
            log.info("[INGEST][APP] lease acquired taskId={} owner={}", taskId, owner);
        }
        return acquired;
    }

    /** Renews a lease. */
    @Override
    public boolean renewLease(Long taskId, String owner, Duration leaseDuration) {
        Instant now = clock.instant();
        int ttlSeconds = (int) leaseDuration.toSeconds();
        return taskRepository.renewLease(taskId, owner, now, ttlSeconds);
    }

    /** Releases a lease. */
    @Override
    public void releaseLease(Long taskId) {
        TaskAggregate task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found taskId=" + taskId));

        // Call domain object's release method and then save
        task.releaseLease();
        taskRepository.save(task);
        log.info("[INGEST][APP] lease released taskId={}", taskId);
    }

    /** Validates a lease (owner still current node). */
    @Override
    public boolean validateLease(Long taskId, String owner) {
        TaskAggregate task = taskRepository.findById(taskId)
            .orElse(null);

        if (task == null) {
            log.warn("[INGEST][APP] lease validation failed: task not found taskId={}", taskId);
            return false;
        }

        boolean valid = task.getLeaseInfo().isHeld()
            && owner.equals(task.getLeaseInfo().owner());

        if (!valid) {
            log.warn("[INGEST][APP] lease validation failed taskId={} expectedOwner={} actualOwner={}",
                     taskId, owner, task.getLeaseInfo().owner());
        }
        return valid;
    }
}

package com.patra.ingest.app.usecase.execution.support;

import java.time.Duration;

/**
 * Lease management service.
 * <p>Encapsulates lease acquire/renew/release logic (backed by TaskRepository).</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface LeaseManagementService {

    /**
     * Attempts to acquire a lease.
     *
     * @param taskId task id
     * @param owner lease owner
     * @param leaseDuration lease duration
     * @return true if acquired
     */
    boolean tryAcquireLease(Long taskId, String owner, Duration leaseDuration);

    /**
     * Renews a lease.
     *
     * @param taskId task id
     * @param owner lease owner
     * @param leaseDuration lease duration
     * @return true if renewed
     */
    boolean renewLease(Long taskId, String owner, Duration leaseDuration);

    /**
     * Releases a lease.
     *
     * @param taskId task id
     */
    void releaseLease(Long taskId);

    /**
     * Validates a lease (owner still current node).
     *
     * @param taskId task id
     * @param owner lease owner
     * @return true if lease is still valid
     */
    boolean validateLease(Long taskId, String owner);
}

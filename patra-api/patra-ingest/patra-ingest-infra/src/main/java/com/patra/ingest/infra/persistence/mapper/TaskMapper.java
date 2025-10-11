package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

/**
 * Mapper interface for the task table.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Extends MyBatis-Plus {@link BaseMapper} to provide common CRUD for the task table.</li>
 *   <li>Provides lease-related SQL operations (CAS acquire, renew, mark RUNNING).</li>
 *   <li>No business semantics here; domain/application layers enforce business rules.</li>
 *   <li>If complex queries are needed, prefer composing repository operations first; when custom SQL is required,
 *       keep method names clear, add complete Javadocs, and document the corresponding XML (if any). Avoid cross-aggregate joins.</li>
 * </ul>
 * </p>
 * <p>Thread-safety: stateless interface; MyBatis-generated proxy is a singleton and safe for concurrent reuse.</p>
 * <p>Logging: avoid logging in Mapper layer; let repositories log key paths to reduce I/O noise.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface TaskMapper extends BaseMapper<TaskDO> {

    /**
     * CAS lease acquisition (step 0).
     * <p>Only update tasks in QUEUED status that meet scheduling and lease-takeover conditions.
     * Implemented in: TaskMapper.xml#tryAcquireLease</p>
     *
     * @param taskId task id
     * @param owner lease owner id
     * @param now current time (UTC)
     * @param ttlSec lease TTL in seconds
     * @param idem idempotent key (defensive check)
     * @return affected rows (1=success, 0=failed)
     */
    int tryAcquireLease(@Param("taskId") Long taskId,
                        @Param("owner") String owner,
                        @Param("now") Instant now,
                        @Param("ttlSec") int ttlSec,
                        @Param("idem") String idem);

    /**
     * Mark task RUNNING and update lease (step 1).
     * <p>Preconditions: WHERE lease_owner=#{owner}; implemented in TaskMapper.xml#markRunningWithLease</p>
     *
     * @param taskId task id
     * @param owner lease owner
     * @param now current time
     * @param ttlSec lease TTL in seconds
     * @return affected rows (1=success, 0=lease lost)
     */
    int markRunningWithLease(@Param("taskId") Long taskId,
                             @Param("owner") String owner,
                             @Param("now") Instant now,
                             @Param("ttlSec") int ttlSec);

    /**
     * Heartbeat lease renewal.
     * <p>Preconditions: WHERE lease_owner=#{owner}; implemented in TaskMapper.xml#renewLease</p>
     *
     * @param taskId task id
     * @param owner lease owner
     * @param now current time
     * @param ttlSec lease TTL in seconds
     * @return affected rows (1=success, 0=lease lost)
     */
    int renewLease(@Param("taskId") Long taskId,
                   @Param("owner") String owner,
                   @Param("now") Instant now,
                   @Param("ttlSec") int ttlSec);

    /**
     * Batch heartbeat lease renewal (performance optimization).
     * <p>Preconditions: WHERE id IN (taskIds) AND lease_owner=#{owner}; implemented in TaskMapper.xml#batchRenewLeases</p>
     *
     * @param taskIds list of task ids
     * @param owner lease owner
     * @param now current time
     * @param ttlSec lease TTL in seconds
     * @return affected rows (number of tasks renewed)
     */
    int batchRenewLeases(@Param("taskIds") java.util.List<Long> taskIds,
                         @Param("owner") String owner,
                         @Param("now") Instant now,
                         @Param("ttlSec") int ttlSec);
}

package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.TaskRunDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

/**
 * Mapper for task execution records (TaskRun).
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Persist a single task execution attempt (start/end/state/metrics).</li>
 *   <li>Query the latest attemptNo (to derive the next attempt number).</li>
 *   <li>Future: queries by task/batch/state can be added as needed.</li>
 * </ul>
 * Keep this mapper focused; avoid cross-table aggregation logic here.
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface TaskRunMapper extends BaseMapper<TaskRunDO> {

    /**
     * Get the latest attempt number for a task.
     * <p>
     * Implemented in: TaskRunMapper.xml#selectLatestAttemptNo
     * </p>
     *
     * @param taskId task id
     * @return max attemptNo, or 0 when there is no record
     */
    int selectLatestAttemptNo(@Param("taskId") Long taskId);

    /**
     * Overwrite the checkpoint and refresh the heartbeat timestamp.
     */
    int updateCheckpointAndHeartbeat(@Param("runId") Long runId,
                                     @Param("checkpointJson") String checkpointJson,
                                     @Param("now") Instant now);

    /**
    * Refresh the heartbeat timestamp.
    */
    int touchHeartbeat(@Param("runId") Long runId,
                   @Param("now") Instant now);

    /**
     * Mark a run record as failed.
     */
    int markFailed(@Param("runId") Long runId,
                   @Param("errorMsg") String errorMsg,
                   @Param("now") Instant now);
}

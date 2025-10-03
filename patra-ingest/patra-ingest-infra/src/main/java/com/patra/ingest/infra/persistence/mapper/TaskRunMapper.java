package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.TaskRunDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

/**
 * 任务执行（TaskRun）表 Mapper。
 * <p>
 * 主要用于：
 * <ul>
 *   <li>记录单次任务运行（含开始 / 结束 / 状态 / 统计指标）。</li>
 *   <li>查询最新 attemptNo（用于生成下一次尝试编号）。</li>
 *   <li>后续可扩展根据任务 / 批次 / 状态的查询。</li>
 * </ul>
 * 不在此处加入跨表统计逻辑，保持 Mapper 纯粹。
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface TaskRunMapper extends BaseMapper<TaskRunDO> {

    /**
     * 获取任务的最新 attemptNo。
     * <p>
     * 实现位于：TaskRunMapper.xml#selectLatestAttemptNo
     * </p>
     *
     * @param taskId 任务 ID
     * @return 最大 attemptNo，若无记录则返回 0
     */
    int selectLatestAttemptNo(@Param("taskId") Long taskId);

    /**
     * 覆盖检查点并刷新心跳时间。
     */
    int updateCheckpointAndHeartbeat(@Param("runId") Long runId,
                                     @Param("checkpointJson") String checkpointJson,
                                     @Param("now") Instant now);

    /**
    * 刷新心跳时间。
    */
    int touchHeartbeat(@Param("runId") Long runId,
                   @Param("now") Instant now);

    /**
     * 将运行记录标记为失败。
     */
    int markFailed(@Param("runId") Long runId,
                   @Param("errorMsg") String errorMsg,
                   @Param("now") Instant now);
}

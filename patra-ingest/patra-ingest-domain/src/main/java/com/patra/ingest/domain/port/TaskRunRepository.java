package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.TaskRun;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 任务运行记录仓储端口。
 * <p>负责持久化任务的运行尝试（Attempt），并支持以任务维度回溯运行历史，
 * 便于应用层实现重试补偿、运行监控与追踪。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TaskRunRepository {

    /**
     * 保存或更新一次任务运行尝试。
     *
     * @param run 任务运行实体，包含运行状态、统计与心跳信息
     * @return 持久化后的运行实体，通常携带数据库生成的主键
     */
    TaskRun save(TaskRun run);

    /**
     * 查询指定任务最近一次运行尝试记录。
     *
     * @param taskId 任务 ID
     * @return 若存在则返回最新的运行记录（按 attemptNo 倒序），不存在返回 empty
     */
    Optional<TaskRun> findLatest(Long taskId);

    /**
     * 按任务 ID 查询全部运行历史。
     *
     * @param taskId 任务 ID
     * @return 运行记录集合，按实现约定输出顺序（通常 attemptNo 升序）
     */
    List<TaskRun> findAll(Long taskId);

    /**
     * 获取任务的最新 attemptNo（用于生成下一次尝试编号）。
     *
     * @param taskId 任务 ID
     * @return 最大的 attemptNo，若无运行记录则返回 0
     */
    int getLatestAttemptNo(Long taskId);

    /**
     * 按运行 ID 查询运行记录。
     *
     * @param runId 运行 ID
     * @return 若存在则返回运行记录，否则 empty
     */
    Optional<TaskRun> findById(Long runId);

    /**
     * 覆盖写入检查点并刷新心跳时间。
     *
     * @param runId 运行 ID
     * @param checkpointJson 检查点 JSON 字符串（null/空串表示清空）
     * @param now 当前时间
     * @return 是否更新成功
     */
    boolean updateCheckpointAndHeartbeat(Long runId, String checkpointJson, Instant now);

    /**
    * 仅刷新心跳时间（不触碰检查点）。
    *
    * @param runId 运行 ID
    * @param now 当前时间
    * @return 是否更新成功
    */
    boolean touchHeartbeat(Long runId, Instant now);

    /**
     * 将运行记录标记为失败并记录错误信息。
     *
     * @param runId 运行 ID
     * @param errorMessage 错误信息
     * @param now 当前时间
     * @return 是否更新成功
     */
    boolean markFailed(Long runId, String errorMessage, Instant now);

    /**
     * 检查任务是否存在成功的运行记录（用于幂等判断）。
     *
     * @param taskId 任务 ID
     * @return true 表示已有 SUCCEEDED 状态的运行记录
     */
    boolean hasSucceededRun(Long taskId);
}

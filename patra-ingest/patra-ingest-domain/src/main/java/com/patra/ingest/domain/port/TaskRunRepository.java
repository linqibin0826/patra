package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.TaskRun;
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
}

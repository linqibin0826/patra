package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.TaskRunBatch;
import java.util.List;

/**
 * 任务运行批次仓储端口。
 * <p>调度运行时一个任务可能被拆分为多个批次（分页/令牌），该端口负责批次层面的持久化与查询。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TaskRunBatchRepository {

    /**
     * 批量写入任务运行批次记录。
     *
     * @param batches 批次实体集合，包含批次状态与统计信息
     */
    void saveAll(List<TaskRunBatch> batches);

    /**
     * 按运行 ID 查询关联的批次记录。
     *
     * @param runId 运行 ID
     * @return 该运行下的批次列表
     */
    List<TaskRunBatch> findByRunId(Long runId);
}

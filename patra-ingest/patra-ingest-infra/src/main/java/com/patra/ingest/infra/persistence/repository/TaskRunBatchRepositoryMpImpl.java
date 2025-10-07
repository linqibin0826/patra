package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.port.TaskRunBatchRepository;
import com.patra.ingest.infra.persistence.converter.TaskRunBatchConverter;
import com.patra.ingest.infra.persistence.entity.TaskRunBatchDO;
import com.patra.ingest.infra.persistence.mapper.TaskRunBatchMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务运行批次（TaskRunBatch）仓储实现。
 * <p>职责：批次统计与分页（页号 pageNo / pageSize）持久化，用于增量采集阶段分块处理。</p>
 * <p>说明：当前批量保存采取逐条写入；若未来批次数量显著增大，可引入批处理 SQL 或异步写。</p>
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class TaskRunBatchRepositoryMpImpl implements TaskRunBatchRepository {

    private final TaskRunBatchMapper mapper;
    private final TaskRunBatchConverter converter;

    /**
     * 保存单个运行批次（insert or update）。
     * @param batch 批次实体
     */
    @Override
    public void save(TaskRunBatch batch) {
        TaskRunBatchDO dto = converter.toDO(batch);
        if (dto.getId() == null) {
            mapper.insert(dto);
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][INFRA] task run batch insert runId={} batchNo={} status={}",
                          dto.getRunId(), dto.getBatchNo(), dto.getStatusCode());
            }
        } else {
            mapper.updateById(dto);
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][INFRA] task run batch update id={} runId={} batchNo={} status={}",
                          dto.getId(), dto.getRunId(), dto.getBatchNo(), dto.getStatusCode());
            }
        }
    }

    /**
     * 批量保存运行批次（insert or update）。
     * @param batches 批次集合
     */
    @Override
    public void saveAll(List<TaskRunBatch> batches) {
        for (TaskRunBatch batch : batches) {
            TaskRunBatchDO dto = converter.toDO(batch);
            if (dto.getId() == null) {
                mapper.insert(dto);
                if (log.isDebugEnabled()) {
                    log.debug("[INGEST][INFRA] task run batch insert runId={} size={} status={}", dto.getRunId(), dto.getRecordCount(), dto.getStatusCode());
                }
            } else {
                mapper.updateById(dto);
                if (log.isDebugEnabled()) {
                    log.debug("[INGEST][INFRA] task run batch update id={} runId={} size={} status={}", dto.getId(), dto.getRunId(), dto.getRecordCount(), dto.getStatusCode());
                }
            }
        }
    }

    /**
     * 查询指定运行尝试的全部批次。
     * @param runId 运行尝试 ID
     * @return 批次列表（可能为空）
     */
    @Override
    public List<TaskRunBatch> findByRunId(Long runId) {
        return mapper.selectList(new QueryWrapper<TaskRunBatchDO>().eq("run_id", runId))
            .stream().map(converter::toDomain).collect(Collectors.toList());
    }
}

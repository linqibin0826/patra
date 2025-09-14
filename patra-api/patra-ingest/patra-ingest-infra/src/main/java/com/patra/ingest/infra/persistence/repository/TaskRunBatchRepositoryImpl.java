package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.port.TaskRunBatchRepository;
import com.patra.ingest.infra.mapstruct.RunBatchConverter;
import com.patra.ingest.infra.persistence.entity.TaskRunBatchDO;
import com.patra.ingest.infra.persistence.mapper.IngTaskRunBatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 运行批次仓储实现。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Repository
@RequiredArgsConstructor
public class TaskRunBatchRepositoryImpl implements TaskRunBatchRepository {

    private final IngTaskRunBatchMapper mapper;
    private final RunBatchConverter converter;

    @Override
    public Optional<TaskRunBatch> findById(Long id) {
        var obj = mapper.selectById(id);
        return Optional.ofNullable(obj).map(converter::toEntity);
    }

    @Override
    public List<TaskRunBatch> findByRunId(Long runId) {
        var q = new LambdaQueryWrapper<TaskRunBatchDO>()
                .eq(TaskRunBatchDO::getRunId, runId)
                .orderByAsc(TaskRunBatchDO::getBatchNo);
        var list = mapper.selectList(q);
        return list.stream().map(converter::toEntity).toList();
    }

    @Override
    public Optional<TaskRunBatch> findByRunIdAndBatchNo(Long runId, Integer batchNo) {
        var q = new LambdaQueryWrapper<TaskRunBatchDO>()
                .eq(TaskRunBatchDO::getRunId, runId)
                .eq(TaskRunBatchDO::getBatchNo, batchNo)
                .last("limit 1");
        var obj = mapper.selectOne(q);
        return Optional.ofNullable(obj).map(converter::toEntity);
    }

    @Override
    public Optional<TaskRunBatch> findByRunIdAndBeforeToken(Long runId, String beforeToken) {
        var q = new LambdaQueryWrapper<TaskRunBatchDO>()
                .eq(TaskRunBatchDO::getRunId, runId)
                .eq(TaskRunBatchDO::getBeforeToken, beforeToken)
                .last("limit 1");
        var obj = mapper.selectOne(q);
        return Optional.ofNullable(obj).map(converter::toEntity);
    }

    @Override
    public Optional<TaskRunBatch> findByIdempotentKey(String idempotentKey) {
        var q = new LambdaQueryWrapper<TaskRunBatchDO>()
                .eq(TaskRunBatchDO::getIdempotentKey, idempotentKey)
                .last("limit 1");
        var obj = mapper.selectOne(q);
        return Optional.ofNullable(obj).map(converter::toEntity);
    }

    @Override
    public TaskRunBatch save(TaskRunBatch batch) {
        var toSave = converter.toDO(batch);
        if (toSave.getId() == null) mapper.insert(toSave); else mapper.updateById(toSave);
        return converter.toEntity(toSave);
    }
}


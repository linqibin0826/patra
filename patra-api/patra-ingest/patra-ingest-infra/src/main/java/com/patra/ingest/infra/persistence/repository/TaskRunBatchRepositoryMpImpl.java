package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.port.TaskRunBatchRepository;
import com.patra.ingest.infra.persistence.converter.TaskRunBatchConverter;
import com.patra.ingest.infra.persistence.entity.TaskRunBatchDO;
import com.patra.ingest.infra.persistence.mapper.TaskRunBatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class TaskRunBatchRepositoryMpImpl implements TaskRunBatchRepository {

    private final TaskRunBatchMapper mapper;
    private final TaskRunBatchConverter converter;

    @Override
    public void saveAll(List<TaskRunBatch> batches) {
        for (TaskRunBatch batch : batches) {
            TaskRunBatchDO dto = converter.toDO(batch);
            if (dto.getId() == null) mapper.insert(dto); else mapper.updateById(dto);
        }
    }

    @Override
    public List<TaskRunBatch> findByRunId(Long runId) {
        return mapper.selectList(new QueryWrapper<TaskRunBatchDO>().eq("run_id", runId))
            .stream().map(converter::toDomain).collect(Collectors.toList());
    }
}

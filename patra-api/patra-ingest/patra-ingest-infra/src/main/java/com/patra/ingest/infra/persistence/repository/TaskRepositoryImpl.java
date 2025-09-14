package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.ingest.domain.model.aggregate.Task;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.infra.mapstruct.JobConverter;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import com.patra.ingest.infra.persistence.mapper.IngTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 任务仓储实现。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Repository
@RequiredArgsConstructor
public class TaskRepositoryImpl implements TaskRepository {

    private final IngTaskMapper mapper;
    private final JobConverter converter;

    @Override
    public Optional<Task> findById(Long id) {
        var obj = mapper.selectById(id);
        return Optional.ofNullable(obj).map(converter::toAggregate);
    }

    @Override
    public Optional<Task> findByIdempotentKey(String idempotentKey) {
        var q = new LambdaQueryWrapper<TaskDO>()
                .eq(TaskDO::getIdempotentKey, idempotentKey)
                .last("limit 1");
        var obj = mapper.selectOne(q);
        return Optional.ofNullable(obj).map(converter::toAggregate);
    }

    @Override
    public List<Task> findBySliceId(Long sliceId) {
        var q = new LambdaQueryWrapper<TaskDO>()
                .eq(TaskDO::getSliceId, sliceId)
                .orderByAsc(TaskDO::getId);
        var list = mapper.selectList(q);
        return list.stream().map(converter::toAggregate).toList();
    }

    @Override
    public Task save(Task task) {
        var toSave = converter.toDO(task);
        if (toSave.getId() == null) mapper.insert(toSave); else mapper.updateById(toSave);
        return converter.toAggregate(toSave);
    }
}


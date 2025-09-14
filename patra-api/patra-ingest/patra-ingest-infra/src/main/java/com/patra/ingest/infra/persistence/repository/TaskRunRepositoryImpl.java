package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.port.TaskRunRepository;
import com.patra.ingest.infra.mapstruct.RunConverter;
import com.patra.ingest.infra.persistence.entity.TaskRunDO;
import com.patra.ingest.infra.persistence.mapper.IngTaskRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 任务运行（attempt）仓储实现。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Repository
@RequiredArgsConstructor
public class TaskRunRepositoryImpl implements TaskRunRepository {

    private final IngTaskRunMapper mapper;
    private final RunConverter converter;

    @Override
    public Optional<TaskRun> findById(Long id) {
        var obj = mapper.selectById(id);
        return Optional.ofNullable(obj).map(converter::toEntity);
    }

    @Override
    public List<TaskRun> findByTaskId(Long taskId) {
        var q = new LambdaQueryWrapper<TaskRunDO>()
                .eq(TaskRunDO::getTaskId, taskId)
                .orderByAsc(TaskRunDO::getAttemptNo);
        var list = mapper.selectList(q);
        return list.stream().map(converter::toEntity).toList();
    }

    @Override
    public Optional<TaskRun> findByTaskIdAndAttemptNo(Long taskId, Integer attemptNo) {
        var q = new LambdaQueryWrapper<TaskRunDO>()
                .eq(TaskRunDO::getTaskId, taskId)
                .eq(TaskRunDO::getAttemptNo, attemptNo)
                .last("limit 1");
        var obj = mapper.selectOne(q);
        return Optional.ofNullable(obj).map(converter::toEntity);
    }

    @Override
    public TaskRun save(TaskRun run) {
        var toSave = converter.toDO(run);
        if (toSave.getId() == null) mapper.insert(toSave); else mapper.updateById(toSave);
        return converter.toEntity(toSave);
    }
}


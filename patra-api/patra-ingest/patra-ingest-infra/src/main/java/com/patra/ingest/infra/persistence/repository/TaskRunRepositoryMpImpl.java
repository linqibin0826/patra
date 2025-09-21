package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.port.TaskRunRepository;
import com.patra.ingest.infra.persistence.converter.TaskRunConverter;
import com.patra.ingest.infra.persistence.entity.TaskRunDO;
import com.patra.ingest.infra.persistence.mapper.TaskRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class TaskRunRepositoryMpImpl implements TaskRunRepository {

    private final TaskRunMapper mapper;
    private final TaskRunConverter converter;

    @Override
    public TaskRun save(TaskRun run) {
        TaskRunDO dto = converter.toDO(run);
        if (dto.getId() == null) mapper.insert(dto); else mapper.updateById(dto);
        // 返回数据库最新状态重新映射（包含生成的 ID）
        TaskRunDO persisted = mapper.selectById(dto.getId());
        return converter.toDomain(persisted);
    }

    @Override
    public Optional<TaskRun> findLatest(Long taskId) {
        TaskRunDO one = mapper.selectOne(new QueryWrapper<TaskRunDO>()
            .eq("task_id", taskId)
            .orderByDesc("attempt_no")
            .last("limit 1"));
        return Optional.ofNullable(one).map(converter::toDomain);
    }

    @Override
    public List<TaskRun> findAll(Long taskId) {
    return mapper.selectList(new QueryWrapper<TaskRunDO>()
        .eq("task_id", taskId)
        .orderByAsc("attempt_no"))
            .stream().map(converter::toDomain).collect(Collectors.toList());
    }
}

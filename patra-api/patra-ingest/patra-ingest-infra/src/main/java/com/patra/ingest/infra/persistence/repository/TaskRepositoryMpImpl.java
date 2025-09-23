package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.infra.persistence.converter.TaskConverter;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import com.patra.ingest.infra.persistence.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TaskRepositoryMpImpl implements TaskRepository {

    private final TaskMapper mapper;
    private final TaskConverter converter;

    @Override
    public TaskAggregate save(TaskAggregate task) {
        TaskDO entity = converter.toEntity(task);
        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return converter.toAggregate(entity);
    }

    @Override
    public List<TaskAggregate> saveAll(List<TaskAggregate> tasks) {
        List<TaskAggregate> persisted = new ArrayList<>(tasks.size());
        for (TaskAggregate task : tasks) {
            persisted.add(save(task));
        }
        return persisted;
    }
}

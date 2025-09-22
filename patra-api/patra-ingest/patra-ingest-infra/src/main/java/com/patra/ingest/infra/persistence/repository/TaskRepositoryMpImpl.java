package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.model.aggregate.Task;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.infra.persistence.converter.TaskConverter;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import com.patra.ingest.infra.persistence.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class TaskRepositoryMpImpl implements TaskRepository {
    private final TaskMapper mapper;
    private final TaskConverter converter;

    @Override
    public Task save(Task task) {
        TaskDO dto = converter.toDO(task);
        if (dto.getId() == null) {
            mapper.insert(dto);
        } else {
            mapper.updateById(dto);
        }
        return converter.toDomain(dto);
    }

    @Override
    public void saveAll(List<Task> tasks) {
        for (Task t : tasks) {
            save(t);
        }
    }
}

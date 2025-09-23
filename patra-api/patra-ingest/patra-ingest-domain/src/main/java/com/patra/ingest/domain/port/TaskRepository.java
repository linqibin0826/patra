package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.TaskAggregate;

import java.util.List;

public interface TaskRepository {
    TaskAggregate save(TaskAggregate task);

    List<TaskAggregate> saveAll(List<TaskAggregate> tasks);
}

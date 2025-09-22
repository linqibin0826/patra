package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.Task;
import java.util.List;

public interface TaskRepository {
    Task save(Task task);
    void saveAll(List<Task> tasks);
}

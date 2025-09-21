package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.Task;
import java.util.List;

public interface TaskRepository {
    void saveAll(List<Task> tasks);
}

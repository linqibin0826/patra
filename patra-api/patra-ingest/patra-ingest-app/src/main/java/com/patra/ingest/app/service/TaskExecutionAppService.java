package com.patra.ingest.app.service;

import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.port.TaskRunRepository;
import com.patra.ingest.domain.port.TaskRunBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskExecutionAppService {

    private final TaskRunRepository taskRunRepository;
    private final TaskRunBatchRepository taskRunBatchRepository;

    @Transactional
    public TaskRun startRun(TaskRun run) {
        return taskRunRepository.save(run);
    }
}

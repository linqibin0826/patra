package com.patra.ingest.app.service;

import com.patra.ingest.domain.model.aggregate.Plan;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.ScheduleInstanceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IngestOrchestratorAppService {

    private final ScheduleInstanceRepository scheduleInstanceRepository;
    private final PlanRepository planRepository;
    private final PlanSliceRepository planSliceRepository;
    private final TaskRepository taskRepository;

    @Transactional
    public Plan createPlan(Plan plan) {
        return planRepository.save(plan);
    }
}

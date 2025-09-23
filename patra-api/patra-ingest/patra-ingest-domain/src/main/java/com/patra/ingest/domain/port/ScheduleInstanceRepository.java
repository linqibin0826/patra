package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;

public interface ScheduleInstanceRepository {
    ScheduleInstanceAggregate save(ScheduleInstanceAggregate instance);
}

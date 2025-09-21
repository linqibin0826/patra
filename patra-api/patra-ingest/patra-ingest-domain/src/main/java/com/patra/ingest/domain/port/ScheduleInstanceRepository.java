package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.ScheduleInstance;

public interface ScheduleInstanceRepository {
    ScheduleInstance save(ScheduleInstance instance);
}

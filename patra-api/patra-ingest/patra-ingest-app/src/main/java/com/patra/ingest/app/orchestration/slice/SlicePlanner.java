package com.patra.ingest.app.orchestration.slice;

import com.patra.ingest.app.orchestration.slice.model.SlicePlanningContext;
import com.patra.ingest.app.orchestration.slice.model.SlicePlan;

import java.util.List;

public interface SlicePlanner {
    String code();

    List<SlicePlan> slice(SlicePlanningContext context);
}

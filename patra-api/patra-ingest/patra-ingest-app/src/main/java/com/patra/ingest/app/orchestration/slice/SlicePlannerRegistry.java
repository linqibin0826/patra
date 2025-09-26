package com.patra.ingest.app.orchestration.slice;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SlicePlannerRegistry {
    private final Map<String, SlicePlanner> planners = new HashMap<>();

    public SlicePlannerRegistry(List<SlicePlanner> plannerList) {
        for (SlicePlanner planner : plannerList) {
            planners.putIfAbsent(planner.code(), planner);
        }
    }

    public SlicePlanner get(String code) {
        return planners.get(code);
    }
}

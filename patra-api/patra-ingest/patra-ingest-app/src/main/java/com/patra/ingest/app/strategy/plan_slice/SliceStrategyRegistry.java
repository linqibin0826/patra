package com.patra.ingest.app.strategy.plan_slice;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SliceStrategyRegistry {
    private final Map<String, SliceStrategy> strategies = new HashMap<>();
    public SliceStrategyRegistry(List<SliceStrategy> strategyList) {
        for (SliceStrategy s : strategyList) {
            strategies.putIfAbsent(s.code(), s);
        }
    }
    public SliceStrategy get(String code) { return strategies.get(code); }
}

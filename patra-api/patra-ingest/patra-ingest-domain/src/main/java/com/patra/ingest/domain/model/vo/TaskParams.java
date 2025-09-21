package com.patra.ingest.domain.model.vo;

import java.util.Map;

/** 任务参数（规范化后的键值）。 */
public record TaskParams(Map<String, Object> values) {
    public TaskParams {
        values = values == null ? Map.of() : Map.copyOf(values);
    }
    public boolean isEmpty() { return values.isEmpty(); }
}

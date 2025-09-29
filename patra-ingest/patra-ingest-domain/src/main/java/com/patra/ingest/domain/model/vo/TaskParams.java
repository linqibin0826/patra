package com.patra.ingest.domain.model.vo;

import java.util.Map;

/**
 * 任务参数键值对（不可变 Map）。
 * <p>语义：任务运行所需的上下文/配置/提示参数。</p>
 * 不变式：内部 Map 永远不可变且非 null。
 */
public record TaskParams(Map<String, Object> values) {
    public TaskParams {
        values = values == null ? Map.of() : Map.copyOf(values);
    }
    /** 是否为空参数集 */
    public boolean isEmpty() { return values.isEmpty(); }
}

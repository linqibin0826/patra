package com.patra.ingest.domain.model.vo;

/**
 * Task Run 的检查点快照（JSON 字符串）。
 */
public record TaskRunCheckpoint(String raw) {

    public TaskRunCheckpoint {
        if (raw != null && raw.isBlank()) {
            raw = null;
        }
    }

    public static TaskRunCheckpoint empty() {
        return new TaskRunCheckpoint(null);
    }

    public boolean isPresent() {
        return raw != null && !raw.isBlank();
    }
}

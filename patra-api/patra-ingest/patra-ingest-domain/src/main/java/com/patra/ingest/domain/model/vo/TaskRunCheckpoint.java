package com.patra.ingest.domain.model.vo;

/**
 * Task Run 检查点快照值对象。
 * <p>用途：存储运行过程中的增量恢复信息（JSON 串），用于失败重跑或断点续传。</p>
 * <ul>
 *   <li>raw：原始 JSON 字符串（去除空白时可为空）</li>
 * </ul>
 * 不变式：空串标准化为 null；不解析内部结构，延迟到使用端处理。
 */
public record TaskRunCheckpoint(String raw) {

    public TaskRunCheckpoint {
        if (raw != null && raw.isBlank()) {
            raw = null;
        }
    }

    /**
     * 空检查点（无状态）。
     */
    public static TaskRunCheckpoint empty() {
        return new TaskRunCheckpoint(null);
    }

    /**
     * 是否存在有效快照。
     */
    public boolean isPresent() {
        return raw != null && !raw.isBlank();
    }
}

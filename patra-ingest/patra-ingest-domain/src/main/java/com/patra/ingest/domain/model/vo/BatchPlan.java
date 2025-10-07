package com.patra.ingest.domain.model.vo;

import java.util.List;

/**
 * 批次规划结果值对象。
 * <p>
 * 职责：封装批次规划器返回的批次列表，包含总批次数、是否超限等信息。
 * </p>
 * <p>
 * 不变式：
 * <ul>
 *   <li>batches 不能为 null（但可以为空列表）。</li>
 *   <li>totalBatches >= 0。</li>
 * </ul>
 * </p>
 *
 * @param batches 批次列表
 * @param totalBatches 总批次数
 * @param exceedsLimit 是否超过批次数限制
 * @author linqibin
 * @since 0.1.0
 */
public record BatchPlan(
    List<Batch> batches,
    int totalBatches,
    boolean exceedsLimit
) {
    public BatchPlan {
        if (batches == null) {
            throw new IllegalArgumentException("batches 不能为 null");
        }
        if (totalBatches < 0) {
            throw new IllegalArgumentException("totalBatches 不能为负数");
        }
    }

    /**
     * 创建空的批次规划（无批次）。
     */
    public static BatchPlan empty() {
        return new BatchPlan(List.of(), 0, false);
    }

    /**
     * 创建单批次规划。
     */
    public static BatchPlan single(Batch batch) {
        return new BatchPlan(List.of(batch), 1, false);
    }

    /**
     * 是否有批次。
     */
    public boolean hasBatches() {
        return !batches.isEmpty();
    }
}

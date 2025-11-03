package com.patra.ingest.domain.model.vo.batch;

import java.util.List;

/**
 * 批次规划结果值对象。
 *
 * <p>封装规划器生成的批次列表、总数和限制标志。
 *
 * <p>不变式:
 *
 * <ul>
 *   <li>{@code batches} 不能为 {@code null} (但可以为空列表)。
 *   <li>{@code totalBatches} 必须大于或等于零。
 * </ul>
 *
 * @param batches 批次列表
 * @param totalBatches 批次总数
 * @param exceedsLimit 是否超出批次限制
 * @author linqibin
 * @since 0.1.0
 */
public record BatchPlan(List<Batch> batches, int totalBatches, boolean exceedsLimit) {
  public BatchPlan {
    if (batches == null) {
      throw new IllegalArgumentException("batches must not be null");
    }
    if (totalBatches < 0) {
      throw new IllegalArgumentException("totalBatches must not be negative");
    }
  }

  /** 创建空的批次计划。 */
  public static BatchPlan empty() {
    return new BatchPlan(List.of(), 0, false);
  }

  /** 创建包含单个批次的计划。 */
  public static BatchPlan single(Batch batch) {
    return new BatchPlan(List.of(batch), 1, false);
  }

  /** 当计划包含至少一个批次时返回 {@code true}。 */
  public boolean hasBatches() {
    return !batches.isEmpty();
  }
}

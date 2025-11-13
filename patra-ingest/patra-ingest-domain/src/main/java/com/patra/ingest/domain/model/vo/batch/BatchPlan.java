package com.patra.ingest.domain.model.vo.batch;

import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import java.util.List;

/**
 * 批次规划结果值对象。
 *
 * <p>封装规划器生成的批次列表和增强的执行上下文。
 *
 * <p>不变式:
 *
 * <ul>
 *   <li>{@code batches} 不能为 {@code null} (但可以为空列表)。
 *   <li>{@code enrichedContext} 不能为 {@code null}。
 * </ul>
 *
 * @param batches 批次列表
 * @param enrichedContext 包含 planMetadata 的增强执行上下文
 * @author linqibin
 * @since 0.2.0
 */
public record BatchPlan(List<Batch> batches, ExecutionContext enrichedContext) {
  public BatchPlan {
    if (batches == null) {
      throw new IllegalArgumentException("batches must not be null");
    }
    if (enrichedContext == null) {
      throw new IllegalArgumentException("enrichedContext must not be null");
    }
  }

  /** 创建空的批次计划。 */
  public static BatchPlan empty(ExecutionContext ctx) {
    return new BatchPlan(List.of(), ctx);
  }

  /** 创建包含单个批次的计划。 */
  public static BatchPlan single(Batch batch, ExecutionContext ctx) {
    return new BatchPlan(List.of(batch), ctx);
  }

  /** 批次总数。 */
  public int totalBatches() {
    return batches.size();
  }

  /** 当计划包含至少一个批次时返回 {@code true}。 */
  public boolean hasBatches() {
    return !batches.isEmpty();
  }

  /** 是否超出批次限制（根据 planMetadata 判断）。 */
  public boolean exceedsLimit() {
    // 可以从 enrichedContext.planMetadata() 中获取限制信息
    // 目前默认返回 false，后续可根据具体需求实现
    return false;
  }
}

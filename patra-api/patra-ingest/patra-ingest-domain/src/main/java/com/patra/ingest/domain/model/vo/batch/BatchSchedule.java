package com.patra.ingest.domain.model.vo.batch;

import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import java.util.List;

/**
 * 批次执行调度表。
 *
 * <p>封装批次调度构建器生成的批次列表和执行上下文。
 *
 * <p>不变式:
 *
 * <ul>
 *   <li>{@code batches} 不能为 {@code null} (但可以为空列表)。
 *   <li>{@code enrichedContext} 不能为 {@code null}。
 * </ul>
 *
 * @param batches 批次列表
 * @param enrichedContext 包含元数据的执行上下文
 * @author linqibin
 * @since 0.2.0
 */
public record BatchSchedule(List<Batch> batches, ExecutionContext enrichedContext) {
  public BatchSchedule {
    if (batches == null) {
      throw new IllegalArgumentException("batches must not be null");
    }
    if (enrichedContext == null) {
      throw new IllegalArgumentException("enrichedContext must not be null");
    }
  }

  /** 创建空的批次调度表。 */
  public static BatchSchedule empty(ExecutionContext ctx) {
    return new BatchSchedule(List.of(), ctx);
  }

  /** 创建包含单个批次的调度表。 */
  public static BatchSchedule single(Batch batch, ExecutionContext ctx) {
    return new BatchSchedule(List.of(batch), ctx);
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

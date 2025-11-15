package com.patra.ingest.domain.model.vo.batch;

import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.query.QuerySession;
import java.util.List;

/**
 * 批次执行调度表。
 *
 * <p>封装批次调度构建器生成的批次列表、执行上下文和查询会话。
 *
 * <p><strong>设计说明</strong>：
 *
 * <ul>
 *   <li>{@code batches}: 批次列表（纯领域模型，只包含offset/limit）
 *   <li>{@code context}: 执行上下文（包含查询、配置等）
 *   <li>{@code querySession}: 查询会话（包含总记录数、会话令牌等）
 * </ul>
 *
 * <p>{@code querySession} 用于后续的参数映射，例如：
 *
 * <ul>
 *   <li>PubMed: 从 stateToken 中提取 webEnv/queryKey
 *   <li>EPMC: 从 stateToken 中提取 cursorMark
 *   <li>其他数据源的特定会话信息
 * </ul>
 *
 * <p>不变式:
 *
 * <ul>
 *   <li>{@code batches} 不能为 {@code null} (但可以为空列表)
 *   <li>{@code context} 不能为 {@code null}
 *   <li>{@code querySession} 不能为 {@code null}
 * </ul>
 *
 * @param batches 批次列表
 * @param context 执行上下文
 * @param querySession 查询会话
 * @author linqibin
 * @since 0.3.0
 */
public record BatchSchedule(
    List<Batch> batches, ExecutionContext context, QuerySession querySession) {

  /** 紧凑构造器：验证不变式。 */
  public BatchSchedule {
    if (batches == null) {
      throw new IllegalArgumentException("batches must not be null");
    }
    if (context == null) {
      throw new IllegalArgumentException("context must not be null");
    }
    if (querySession == null) {
      throw new IllegalArgumentException("querySession must not be null");
    }
  }

  /**
   * 创建空的批次调度表。
   *
   * @param ctx 执行上下文
   * @param session 查询会话（totalRecords=0）
   * @return 空调度表
   */
  public static BatchSchedule empty(ExecutionContext ctx, QuerySession session) {
    return new BatchSchedule(List.of(), ctx, session);
  }

  /**
   * 创建包含单个批次的调度表。
   *
   * @param batch 单个批次
   * @param ctx 执行上下文
   * @param session 查询会话
   * @return 调度表
   */
  public static BatchSchedule single(Batch batch, ExecutionContext ctx, QuerySession session) {
    return new BatchSchedule(List.of(batch), ctx, session);
  }

  /**
   * 批次总数。
   *
   * @return 批次数量
   */
  public int totalBatches() {
    return batches.size();
  }

  /**
   * 当计划包含至少一个批次时返回 {@code true}。
   *
   * @return 是否有批次
   */
  public boolean hasBatches() {
    return !batches.isEmpty();
  }

  /**
   * 是否超出批次限制。
   *
   * <p>根据配置中的 maxPagesPerExecution 判断。
   *
   * @return 是否超出限制
   */
  public boolean exceedsLimit() {
    Integer maxPages = context.configSnapshot().pagination().maxPagesPerExecution();
    if (maxPages == null || maxPages <= 0) {
      return false; // 无限制
    }
    return batches.size() > maxPages;
  }
}

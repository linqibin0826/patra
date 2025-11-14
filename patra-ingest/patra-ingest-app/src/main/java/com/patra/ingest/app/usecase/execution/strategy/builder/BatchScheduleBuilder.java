package com.patra.ingest.app.usecase.execution.strategy.builder;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.vo.batch.BatchSchedule;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;

/**
 * 批次调度构建器接口
 *
 * <p>职责: 从执行上下文中构建批次调度表;可根据 Provenance 定制策略。
 *
 * <h3>设计考量</h3>
 *
 * <ul>
 *   <li><b>策略模式</b>: 不同 provenanceCode 可使用不同的构建策略
 *   <li><b>批次限制</b>: 强制执行最大批次数量;超限时抛异常或标记 exceedsLimit
 *   <li><b>游标支持</b>: 支持基于游标的分页(如 token-based)
 *   <li><b>窗口感知</b>: 根据 WindowSpec 策略调整查询范围 (TIME/DATE/ID_RANGE/CURSOR_LANDMARK 等)
 * </ul>
 *
 * <h3>注册机制</h3>
 *
 * <p>实现类应注册到 {@link BatchScheduleBuilderRegistry},通过 provenanceCode 路由。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface BatchScheduleBuilder {

  /**
   * 返回支持的 Provenance 代码
   *
   * @return Provenance 代码
   */
  ProvenanceCode getProvenanceCode();

  /**
   * 构建批次调度表
   *
   * @param context 执行上下文(查询/参数/窗口/配置快照)
   * @return 批次调度表
   */
  BatchSchedule build(ExecutionContext context);
}

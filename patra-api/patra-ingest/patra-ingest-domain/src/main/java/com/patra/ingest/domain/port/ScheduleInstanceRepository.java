package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;

/**
 * 调度实例(ScheduleInstance)聚合根仓储端口(六边形架构 - Domain → Infrastructure)。
 *
 * <p><b>职责</b>: 持久化每次调度触发的上下文快照,支持:
 *
 * <ul>
 *   <li>调度回放 - 记录每次触发的完整上下文
 *   <li>审计跟踪 - 追溯任务的调度来源
 *   <li>任务关联 - 建立任务与调度实例的血缘关系
 * </ul>
 *
 * <p><b>端口语义</b>: 此接口是六边形架构中的 <b>仓储端口(Repository Port)</b>,定义在 Domain
 * 层,由基础设施层(Infrastructure)实现,确保领域逻辑与持久化技术解耦。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ScheduleInstanceRepository {

  /**
   * 持久化或更新调度实例聚合根。
   *
   * <p><b>业务含义</b>: 保存调度触发的快照,包括调度器元数据、触发时间戳、参数等。
   *
   * @param instance 聚合根,包含调度器元数据、触发时间戳、参数
   * @return 已持久化的调度实例
   */
  ScheduleInstanceAggregate saveOrUpdateInstance(ScheduleInstanceAggregate instance);
}

package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import java.util.Optional;

/**
 * 采集计划(Plan)聚合根仓储端口(六边形架构 - Domain → Infrastructure)。
 *
 * <p><b>职责</b>: 持久化、去重和查询采集计划聚合根,保证:
 *
 * <ul>
 *   <li>计划创建的一致性 - 通过 planKey 唯一约束防止重复计划
 *   <li>计划回放能力 - 支持历史计划的精确检索
 *   <li>聚合根完整性 - 确保 Plan 及其元数据的事务一致性
 * </ul>
 *
 * <p><b>端口语义</b>: 此接口是六边形架构中的 <b>仓储端口(Repository Port)</b>,定义在 Domain
 * 层,由基础设施层(Infrastructure)实现,确保领域逻辑与持久化技术解耦。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlanRepository {

  /**
   * 持久化或更新单个计划聚合根。
   *
   * <p><b>业务含义</b>: 保存完整的计划聚合根,包括时间窗口、触发器、切片策略等。
   *
   * @param plan 计划聚合根,包含窗口、触发器、切片策略
   * @return 已持久化的聚合根
   */
  PlanAggregate save(PlanAggregate plan);

  /**
   * 根据 {@code planKey} 查询计划聚合根。
   *
   * <p><b>业务含义</b>: 通过业务主键(从数据源、操作、窗口等派生)定位唯一计划。
   *
   * @param planKey 唯一计划键(从 source、operation、window 等派生)
   * @return 匹配的计划,或 {@link Optional#empty()}
   */
  Optional<PlanAggregate> findByPlanKey(String planKey);

  /**
   * 检查指定 {@code planKey} 是否已存在。
   *
   * <p><b>业务含义</b>: 在创建计划前进行去重检查,避免重复计划。
   *
   * @param planKey 唯一计划键
   * @return {@code true} 表示计划已存在
   */
  boolean existsByPlanKey(String planKey);

  /**
   * 根据标识符查询计划聚合根。
   *
   * <p><b>业务含义</b>: 通过技术主键(ID)检索计划。
   *
   * @param planId 计划标识符
   * @return 计划聚合根,或 {@link Optional#empty()}
   */
  Optional<PlanAggregate> findById(Long planId);
}

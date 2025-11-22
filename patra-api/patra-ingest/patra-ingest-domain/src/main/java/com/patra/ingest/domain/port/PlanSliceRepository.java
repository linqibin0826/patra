package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import java.util.List;
import java.util.Optional;

/// 计划切片(PlanSlice)聚合根仓储端口(六边形架构 - Domain → Infrastructure)。
///
/// **职责**: 持久化计划切片聚合根,确保:
///
/// - 切片持久化 - 保存计划生成的时间窗口切片
///   - 任务组装支持 - 为任务创建提供精确的窗口查询
///   - 回放能力 - 支持历史切片的精确检索
///
/// **端口语义**: 此接口是六边形架构中的 **仓储端口(Repository Port)**,定义在 Domain
/// 层,由基础设施层(Infrastructure)实现,确保领域逻辑与持久化技术解耦。
///
/// @author linqibin
/// @since 0.1.0
public interface PlanSliceRepository {

  /// 持久化或更新单个计划切片聚合根。
  ///
  /// **业务含义**: 保存切片聚合根,包括时间窗口和过滤上下文。
  ///
  /// @param slice 计划切片聚合根,包含窗口和过滤上下文
  /// @return 已持久化的切片
  PlanSliceAggregate save(PlanSliceAggregate slice);

  /// 批量持久化多个计划切片。
  ///
  /// **业务含义**: 一次性保存多个切片,用于计划切片后的批量保存。
  ///
  /// @param slices 待持久化的切片列表
  /// @return 已持久化的切片列表
  List<PlanSliceAggregate> saveAll(List<PlanSliceAggregate> slices);

  /// 查询指定计划的所有切片。
  ///
  /// **业务含义**: 获取计划的完整切片列表,用于任务组装或回放。
  ///
  /// @param planId 计划标识符
  /// @return 切片列表
  List<PlanSliceAggregate> findByPlanId(Long planId);

  /// 根据标识符查询切片。
  ///
  /// **业务含义**: 通过技术主键(ID)检索切片。
  ///
  /// @param sliceId 切片标识符
  /// @return 切片,或 {@link Optional#empty()}
  Optional<PlanSliceAggregate> findById(Long sliceId);
}

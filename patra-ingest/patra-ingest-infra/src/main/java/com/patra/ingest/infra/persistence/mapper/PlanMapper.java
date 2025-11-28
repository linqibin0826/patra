package com.patra.ingest.infra.persistence.mapper;

import com.patra.ingest.infra.persistence.entity.PlanDO;
import com.patra.starter.mybatis.mapper.PatraBaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/// 采集计划 Mapper 接口 — 对计划表的数据访问操作。
///
/// @author linqibin
/// @since 0.1.0
public interface PlanMapper extends PatraBaseMapper<PlanDO> {

  /// 根据计划键查找计划
  PlanDO findByPlanKey(@Param("planKey") String planKey);

  /// 根据调度实例 ID 查找计划
  List<PlanDO> findByScheduleInstanceId(@Param("scheduleInstanceId") Long scheduleInstanceId);

  /// 查找给定数据源和操作类型的活跃计划
  List<PlanDO> findActiveByProvenanceAndOperation(
      @Param("provenanceCode") String provenanceCode,
      @Param("operationCode") String operationCode,
      @Param("statusCodes") List<String> statusCodes);

  /// 统计计划键是否存在
  int countByPlanKey(@Param("planKey") String planKey);

  /// 统计给定数据源和操作的特定状态计划数
  long countByProvenanceAndOperationAndStatus(
      @Param("provenanceCode") String provenanceCode,
      @Param("operationCode") String operationCode,
      @Param("statusCode") String statusCode);

  /// 批量更新计划状态
  int batchUpdateStatus(
      @Param("planIds") List<Long> planIds,
      @Param("statusCode") String statusCode,
      @Param("remarks") String remarks);

  /// 软删除计划
  int softDeleteById(@Param("planId") Long planId);
}

package com.patra.ingest.infra.adapter.persistence.dao;

import com.patra.ingest.infra.adapter.persistence.entity.PlanSliceEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// 计划切片 JPA Repository。
///
/// **职责**：
///
/// - 提供 PlanSliceEntity 的 CRUD 操作
/// - 支持按计划 ID 查询关联切片
///
/// @author linqibin
/// @since 0.1.0
public interface PlanSliceDao extends JpaRepository<PlanSliceEntity, Long> {

  /// 根据计划 ID 查找所有切片。
  ///
  /// @param planId 计划 ID
  /// @return 切片列表
  List<PlanSliceEntity> findByPlanId(Long planId);

  /// 根据计划 ID 列表批量查找切片。
  ///
  /// @param planIds 计划 ID 列表
  /// @return 切片列表
  List<PlanSliceEntity> findByPlanIdIn(List<Long> planIds);
}

package dev.linqibin.patra.ingest.infra.adapter.persistence.dao;

import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.ScheduleInstanceEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// 调度实例 JPA Repository。
///
/// **职责**：
///
/// - 提供 ScheduleInstanceEntity 的 CRUD 操作
/// - 每次调度触发（手动/定时/API）创建一个实例
///
/// @author linqibin
/// @since 0.1.0
public interface ScheduleInstanceDao extends JpaRepository<ScheduleInstanceEntity, Long> {

  /// 根据数据源代码查找调度实例。
  ///
  /// @param provenanceCode 数据源代码
  /// @return 调度实例列表
  List<ScheduleInstanceEntity> findByProvenanceCode(String provenanceCode);

  /// 根据调度器代码和作业 ID 查找调度实例。
  ///
  /// @param schedulerCode 调度器代码
  /// @param schedulerJobId 作业 ID
  /// @return 调度实例列表
  List<ScheduleInstanceEntity> findBySchedulerCodeAndSchedulerJobId(
      String schedulerCode, String schedulerJobId);

  /// 根据触发时间范围查找调度实例。
  ///
  /// @param startTime 开始时间（含）
  /// @param endTime 结束时间（不含）
  /// @return 调度实例列表
  List<ScheduleInstanceEntity> findByTriggeredAtBetween(Instant startTime, Instant endTime);
}

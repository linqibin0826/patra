package dev.linqibin.patra.ingest.infra.adapter.persistence.dao;

import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.PlanEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 采集计划 JPA Repository。
///
/// **职责**：
///
/// - 提供 PlanEntity 的 CRUD 操作
/// - 支持按计划键、调度实例、数据源+操作等多种查询
/// - 提供状态批量更新和软删除操作
///
/// @author linqibin
/// @since 0.1.0
public interface PlanDao extends JpaRepository<PlanEntity, Long> {

  /// 根据计划键查找计划。
  ///
  /// @param planKey 计划键
  /// @return 计划实体
  Optional<PlanEntity> findByPlanKey(String planKey);

  /// 根据调度实例 ID 查找所有关联计划。
  ///
  /// @param scheduleInstanceId 调度实例 ID
  /// @return 计划列表
  List<PlanEntity> findByScheduleInstanceId(Long scheduleInstanceId);

  /// 查找给定数据源和操作类型的活跃计划。
  ///
  /// @param provenanceCode 数据源代码
  /// @param operationCode 操作类型代码
  /// @param statusCodes 状态代码列表
  /// @return 满足条件的计划列表
  @Query(
      """
      SELECT p FROM PlanEntity p
      WHERE p.provenanceCode = :provenanceCode
        AND p.operationCode = :operationCode
        AND p.statusCode IN :statusCodes
      """)
  List<PlanEntity> findActiveByProvenanceAndOperation(
      @Param("provenanceCode") String provenanceCode,
      @Param("operationCode") String operationCode,
      @Param("statusCodes") Collection<String> statusCodes);

  /// 检查计划键是否存在。
  ///
  /// @param planKey 计划键
  /// @return 存在返回 true
  boolean existsByPlanKey(String planKey);

  /// 统计给定数据源和操作的特定状态计划数。
  ///
  /// @param provenanceCode 数据源代码
  /// @param operationCode 操作类型代码
  /// @param statusCode 状态代码
  /// @return 计划数量
  long countByProvenanceCodeAndOperationCodeAndStatusCode(
      String provenanceCode, String operationCode, String statusCode);

  /// 批量更新计划状态。
  ///
  /// @param planIds 计划 ID 列表
  /// @param statusCode 目标状态代码
  /// @return 受影响行数
  @Modifying
  @Query(
      """
      UPDATE PlanEntity p
      SET p.statusCode = :statusCode, p.version = p.version + 1
      WHERE p.id IN :planIds
      """)
  int batchUpdateStatus(
      @Param("planIds") Collection<Long> planIds, @Param("statusCode") String statusCode);
}

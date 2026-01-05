package com.patra.registry.infra.adapter.persistence.dao.provenance;

import com.patra.registry.infra.adapter.persistence.entity.provenance.ProvBatchingCfgEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 批处理配置 JPA Repository。
///
/// **职责**：
///
/// - 提供 ProvBatchingCfgEntity 的 CRUD 操作
/// - 支持时态切片和操作优先级查询
///
/// @author linqibin
/// @since 0.1.0
public interface ProvBatchingCfgDao extends JpaRepository<ProvBatchingCfgEntity, Long> {

  /// 查询指定数据源和操作作用域的有效批处理配置。
  ///
  /// @param provenanceId 数据源标识
  /// @param operationType 操作类型键
  /// @param now 查询时间戳
  /// @return 有效的批处理配置(可选)
  @Query(
      value =
          """
          SELECT * FROM reg_prov_batching_cfg
          WHERE lifecycle_status_code = 'ACTIVE'
            AND provenance_id = :provenanceId
            AND effective_from <= :now
            AND (effective_to IS NULL OR effective_to > :now)
            AND operation_type IN (:operationType, 'ALL')
          ORDER BY
            CASE WHEN operation_type = :operationType THEN 1 ELSE 2 END,
            effective_from DESC,
            id DESC
          LIMIT 1
          """,
      nativeQuery = true)
  Optional<ProvBatchingCfgEntity> findActiveMerged(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("now") Instant now);
}

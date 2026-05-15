package dev.linqibin.patra.registry.infra.adapter.persistence.dao.expr;

import dev.linqibin.patra.registry.infra.adapter.persistence.entity.expr.ProvApiParamMapEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// API 参数映射 JPA Repository。
///
/// **职责**：
///
/// - 提供 ProvApiParamMapEntity 的 CRUD 操作
/// - 支持时态切片、操作优先级和端点过滤查询
///
/// @author linqibin
/// @since 0.1.0
public interface ProvApiParamMapDao extends JpaRepository<ProvApiParamMapEntity, Long> {

  /// 查询指定数据源、操作和端点的所有激活 API 参数映射。
  ///
  /// @param provenanceId 数据源标识
  /// @param operationType 规范化的操作类型(支持 ALL 回退)
  /// @param endpointName 端点名称(可为 null，表示所有端点)
  /// @param now 评估时间戳
  /// @return API 参数映射列表
  @Query(
      value =
          """
          SELECT * FROM reg_prov_api_param_map
          WHERE lifecycle_status_code = 'ACTIVE'
            AND provenance_id = :provenanceId
            AND operation_type IN (:operationType, 'ALL')
            AND (endpoint_name IS NULL OR endpoint_name = :endpointName)
            AND effective_from <= :now
            AND (effective_to IS NULL OR effective_to > :now)
          ORDER BY std_key
          """,
      nativeQuery = true)
  List<ProvApiParamMapEntity> findActiveByTask(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("endpointName") String endpointName,
      @Param("now") Instant now);
}

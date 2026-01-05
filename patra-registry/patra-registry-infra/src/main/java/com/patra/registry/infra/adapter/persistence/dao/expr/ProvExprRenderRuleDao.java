package com.patra.registry.infra.adapter.persistence.dao.expr;

import com.patra.registry.infra.adapter.persistence.entity.expr.ProvExprRenderRuleEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 表达式渲染规则 JPA Repository。
///
/// **职责**：
///
/// - 提供 ProvExprRenderRuleEntity 的 CRUD 操作
/// - 支持时态切片和操作优先级查询
///
/// @author linqibin
/// @since 0.1.0
public interface ProvExprRenderRuleDao extends JpaRepository<ProvExprRenderRuleEntity, Long> {

  /// 查询指定数据源和操作的所有激活渲染规则。
  ///
  /// @param provenanceId 数据源标识
  /// @param operationType 规范化的操作类型(支持 ALL 回退)
  /// @param now 评估时间戳
  /// @return 渲染规则列表
  @Query(
      value =
          """
          SELECT * FROM reg_prov_expr_render_rule
          WHERE lifecycle_status_code = 'ACTIVE'
            AND provenance_id = :provenanceId
            AND operation_type IN (:operationType, 'ALL')
            AND effective_from <= :now
            AND (effective_to IS NULL OR effective_to > :now)
          ORDER BY field_key, op_code
          """,
      nativeQuery = true)
  List<ProvExprRenderRuleEntity> findActiveByTask(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("now") Instant now);
}

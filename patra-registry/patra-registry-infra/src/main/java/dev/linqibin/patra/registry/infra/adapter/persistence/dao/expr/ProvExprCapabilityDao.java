package dev.linqibin.patra.registry.infra.adapter.persistence.dao.expr;

import dev.linqibin.patra.registry.infra.adapter.persistence.entity.expr.ProvExprCapabilityEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 表达式能力 JPA Repository。
///
/// **职责**：
///
/// - 提供 ProvExprCapabilityEntity 的 CRUD 操作
/// - 支持时态切片和操作优先级查询
/// - 支持按字段分组的去重查询
///
/// @author linqibin
/// @since 0.1.0
public interface ProvExprCapabilityDao extends JpaRepository<ProvExprCapabilityEntity, Long> {

  /// 获取请求字段的最具体激活能力切片。
  ///
  /// @param provenanceId 数据源标识
  /// @param operationType 规范化的操作类型(支持 ALL 回退)
  /// @param fieldKey 规范字段键
  /// @param now 评估时间戳
  /// @return 在 `now` 时刻有效的能力(可选)
  @Query(
      value =
          """
          SELECT * FROM reg_prov_expr_capability
          WHERE lifecycle_status_code = 'ACTIVE'
            AND provenance_id = :provenanceId
            AND operation_type IN (:operationType, 'ALL')
            AND field_key = :fieldKey
            AND effective_from <= :now
            AND (effective_to IS NULL OR effective_to > :now)
          ORDER BY
            CASE WHEN operation_type = :operationType THEN 1 ELSE 2 END,
            effective_from DESC,
            id DESC
          LIMIT 1
          """,
      nativeQuery = true)
  Optional<ProvExprCapabilityEntity> findActive(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("fieldKey") String fieldKey,
      @Param("now") Instant now);

  /// 列出提供的操作作用域的所有激活能力切片，合并具有相同 `field_key` 的源级行。
  ///
  /// 使用 ROW_NUMBER() 窗口函数按 field_key 分组，每个字段只返回优先级最高的一条记录。
  ///
  /// @param provenanceId 数据源标识
  /// @param operationType 规范化的操作类型(支持 ALL 回退)
  /// @param now 评估时间戳
  /// @return 能力列表，每个字段一条
  @Query(
      value =
          """
          SELECT * FROM (
              SELECT c.*,
                     ROW_NUMBER() OVER (
                         PARTITION BY c.field_key
                         ORDER BY CASE WHEN c.operation_type = :operationType THEN 1 ELSE 2 END,
                                  c.effective_from DESC,
                                  c.id DESC
                     ) AS rn
              FROM reg_prov_expr_capability c
              WHERE c.lifecycle_status_code = 'ACTIVE'
                AND c.provenance_id = :provenanceId
                AND c.operation_type IN (:operationType, 'ALL')
                AND c.effective_from <= :now
                AND (c.effective_to IS NULL OR c.effective_to > :now)
          ) t
          WHERE t.rn = 1
          ORDER BY t.field_key
          """,
      nativeQuery = true)
  List<ProvExprCapabilityEntity> findActiveByTask(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("now") Instant now);
}

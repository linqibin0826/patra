package com.patra.registry.infra.adapter.persistence.dao.provenance;

import com.patra.registry.infra.adapter.persistence.entity.provenance.ProvenanceEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 数据源 JPA Repository。
///
/// **职责**：
///
/// - 提供 ProvenanceEntity 的 CRUD 操作
/// - 通过业务代码加载数据源元数据
///
/// @author linqibin
/// @since 0.1.0
public interface ProvenanceDao extends JpaRepository<ProvenanceEntity, Long> {

  /// 通过其稳定的业务代码获取数据源。
  ///
  /// @param code 数据源代码(例如，pubmed)
  /// @return 可选的数据源实体
  @Query(
      """
      SELECT p FROM ProvenanceEntity p
      WHERE p.provenanceCode = :code
        AND p.deletedAt IS NULL
      """)
  Optional<ProvenanceEntity> findByCode(@Param("code") String code);

  /// 列出按代码排序的所有激活数据源。
  ///
  /// @return 数据源列表
  @Query(
      """
      SELECT p FROM ProvenanceEntity p
      WHERE p.deletedAt IS NULL
      ORDER BY p.provenanceCode
      """)
  List<ProvenanceEntity> findAllActive();
}

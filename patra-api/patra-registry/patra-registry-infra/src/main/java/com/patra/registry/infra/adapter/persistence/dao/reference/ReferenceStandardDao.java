package com.patra.registry.infra.adapter.persistence.dao.reference;

import com.patra.registry.infra.adapter.persistence.entity.reference.ReferenceStandardEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 来源标准 JPA Repository。
///
/// **职责**：
///
/// - 提供 ReferenceStandardEntity 的只读查询能力
///
/// @author linqibin
/// @since 0.1.0
public interface ReferenceStandardDao extends JpaRepository<ReferenceStandardEntity, Long> {

  /// 通过字典类型代码和标准代码查询来源标准。
  ///
  /// @param dictTypeCode 字典类型代码
  /// @param standardCode 标准代码
  /// @return 可选的来源标准实体
  @Query(
      """
      SELECT s FROM ReferenceStandardEntity s
      WHERE s.dictTypeCode = :dictTypeCode
        AND s.standardCode = :standardCode
        AND s.deletedAt IS NULL
      """)
  Optional<ReferenceStandardEntity> findByDictTypeCodeAndStandardCode(
      @Param("dictTypeCode") String dictTypeCode, @Param("standardCode") String standardCode);

  /// 查询指定字典类型的规范标准。
  ///
  /// @param dictTypeCode 字典类型代码
  /// @return 可选的规范标准实体
  @Query(
      """
      SELECT s FROM ReferenceStandardEntity s
      WHERE s.dictTypeCode = :dictTypeCode
        AND s.canonical = true
        AND s.enabled = true
        AND s.deletedAt IS NULL
      """)
  Optional<ReferenceStandardEntity> findCanonicalByDictTypeCode(
      @Param("dictTypeCode") String dictTypeCode);
}

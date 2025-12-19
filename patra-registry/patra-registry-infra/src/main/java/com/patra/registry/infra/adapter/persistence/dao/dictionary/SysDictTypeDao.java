package com.patra.registry.infra.adapter.persistence.dao.dictionary;

import com.patra.registry.infra.adapter.persistence.entity.dictionary.SysDictTypeEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 系统字典类型 JPA Repository。
///
/// **职责**：
///
/// - 提供 SysDictTypeEntity 的 CRUD 操作
/// - 通过类型代码查询字典类型
///
/// @author linqibin
/// @since 0.1.0
public interface SysDictTypeDao extends JpaRepository<SysDictTypeEntity, Long> {

  /// 通过类型代码查询字典类型。
  ///
  /// @param typeCode 类型代码
  /// @return 可选的字典类型实体
  @Query(
      """
      SELECT t FROM SysDictTypeEntity t
      WHERE t.typeCode = :typeCode
        AND t.deletedAt IS NULL
      """)
  Optional<SysDictTypeEntity> findByTypeCode(@Param("typeCode") String typeCode);

  /// 查询所有激活的字典类型。
  ///
  /// @return 字典类型列表
  @Query(
      """
      SELECT t FROM SysDictTypeEntity t
      WHERE t.deletedAt IS NULL
      ORDER BY t.typeCode
      """)
  List<SysDictTypeEntity> findAllActive();
}

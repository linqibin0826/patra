package com.patra.registry.infra.adapter.persistence.dao.dictionary;

import com.patra.registry.infra.adapter.persistence.entity.dictionary.SysDictItemEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 系统字典项 JPA Repository。
///
/// **职责**：
///
/// - 提供 SysDictItemEntity 的 CRUD 操作
/// - 通过类型 ID 查询字典项
/// - 通过类型 ID 和项目代码查询单个字典项
///
/// @author linqibin
/// @since 0.1.0
public interface SysDictItemDao extends JpaRepository<SysDictItemEntity, Long> {

  /// 通过类型 ID 查询所有字典项。
  ///
  /// @param typeId 类型 ID
  /// @return 字典项列表
  @Query(
      """
      SELECT i FROM SysDictItemEntity i
      WHERE i.typeId = :typeId
        AND i.deletedAt IS NULL
      ORDER BY i.displayOrder, i.itemCode
      """)
  List<SysDictItemEntity> findByTypeId(@Param("typeId") Long typeId);

  /// 通过类型 ID 和项目代码查询字典项。
  ///
  /// @param typeId 类型 ID
  /// @param itemCode 项目代码
  /// @return 可选的字典项实体
  @Query(
      """
      SELECT i FROM SysDictItemEntity i
      WHERE i.typeId = :typeId
        AND i.itemCode = :itemCode
        AND i.deletedAt IS NULL
      """)
  Optional<SysDictItemEntity> findByTypeIdAndItemCode(
      @Param("typeId") Long typeId, @Param("itemCode") String itemCode);

  /// 查询类型的默认字典项。
  ///
  /// @param typeId 类型 ID
  /// @return 可选的默认字典项
  @Query(
      """
      SELECT i FROM SysDictItemEntity i
      WHERE i.typeId = :typeId
        AND i.isDefault = true
        AND i.deletedAt IS NULL
      """)
  Optional<SysDictItemEntity> findDefaultByTypeId(@Param("typeId") Long typeId);
}

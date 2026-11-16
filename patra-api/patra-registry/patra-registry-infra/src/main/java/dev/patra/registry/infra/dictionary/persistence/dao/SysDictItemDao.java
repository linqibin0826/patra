package dev.patra.registry.infra.dictionary.persistence.dao;

import dev.patra.registry.infra.dictionary.persistence.entity.SysDictItemEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/// 系统字典项 JPA Repository。
///
/// **职责**：
///
/// - 提供 SysDictItemEntity 的 CRUD 操作
/// - 通过类型 ID 查询字典项
/// - 通过类型 ID 和项目代码查询单个字典项
///
/// 由于实体继承自 SoftDeletableJpaEntity（使用 Hibernate @SoftDelete），
/// 框架会自动添加 `deleted_at IS NULL` 过滤条件，无需手动指定。
///
/// @author linqibin
/// @since 0.1.0
public interface SysDictItemDao extends JpaRepository<SysDictItemEntity, Long> {

  /// 通过类型 ID 查询所有字典项，按显示顺序和项目代码排序。
  ///
  /// @param dictTypeId 类型 ID
  /// @return 字典项列表
  List<SysDictItemEntity> findByDictTypeIdAndDeletedAtIsNullOrderByDisplayOrderAscItemCodeAsc(
      Long dictTypeId);

  /// 通过类型 ID 和项目代码查询字典项。
  ///
  /// @param dictTypeId 类型 ID
  /// @param itemCode 项目代码
  /// @return 可选的字典项实体
  Optional<SysDictItemEntity> findByDictTypeIdAndItemCodeAndDeletedAtIsNull(
      Long dictTypeId, String itemCode);

  /// 批量查询类型内的字典项。
  ///
  /// @param dictTypeId 类型 ID
  /// @param itemCodes 项目代码集合
  /// @return 匹配的字典项实体列表
  List<SysDictItemEntity> findByDictTypeIdAndItemCodeInAndDeletedAtIsNull(
      Long dictTypeId, Iterable<String> itemCodes);

  /// 批量查询字典项。
  ///
  /// @param itemIds 字典项 ID 集合
  /// @return 匹配的字典项实体列表
  List<SysDictItemEntity> findByIdInAndDeletedAtIsNull(Iterable<Long> itemIds);

  /// 查询类型的默认字典项。
  ///
  /// @param dictTypeId 类型 ID
  /// @return 可选的默认字典项
  Optional<SysDictItemEntity> findByDictTypeIdAndIsDefaultTrueAndDeletedAtIsNull(Long dictTypeId);

  /// 查询指定类型下所有启用的字典项，按显示顺序和项目代码排序。
  ///
  /// @param dictTypeId 类型 ID
  /// @return 已启用的字典项列表
  List<SysDictItemEntity>
      findByDictTypeIdAndEnabledTrueAndDeletedAtIsNullOrderByDisplayOrderAscItemCodeAsc(
          Long dictTypeId);
}

package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.infra.persistence.entity.MeshQualifierEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/// MeSH 限定词 JPA Repository。
///
/// **功能说明**：
///
/// 提供 MeSH 限定词的数据访问接口，基于 Spring Data JPA 自动实现。
/// 支持标准 CRUD 操作和自定义查询方法。
///
/// **自动实现的方法**（继承自 JpaRepository）：
///
/// - `save(entity)` - 保存或更新
/// - `saveAll(entities)` - 批量保存
/// - `findById(id)` - 按主键查询
/// - `findAll()` - 查询所有
/// - `deleteById(id)` - 按主键删除（软删除）
/// - `count()` - 统计总数
///
/// **软删除说明**：
///
/// 由于 `MeshQualifierEntity` 继承了 `BaseJpaEntity`，所有查询自动过滤已删除记录，
/// 删除操作会自动更新 `deleted_at` 字段而非物理删除。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshQualifierDao extends JpaRepository<MeshQualifierEntity, Long> {

  /// 按 UI 查询限定词。
  ///
  /// @param ui 限定词唯一标识符（格式：Q000001-Q999999）
  /// @return 限定词实体（如果存在）
  Optional<MeshQualifierEntity> findByUi(String ui);

  /// 检查是否存在任何数据。
  ///
  /// 用于批量导入前检查是否需要导入数据。
  ///
  /// @return 如果存在任何未删除的限定词数据返回 true
  @Query("SELECT COUNT(q) > 0 FROM MeshQualifierEntity q")
  boolean hasAnyData();

  /// 按 UI 检查是否存在。
  ///
  /// @param ui 限定词唯一标识符
  /// @return 如果存在返回 true
  boolean existsByUi(String ui);

  /// 按名称列表批量查询限定词。
  ///
  /// @param names 限定词名称列表
  /// @return 限定词实体列表
  List<MeshQualifierEntity> findAllByNameIn(Collection<String> names);

  /// 按 UI 列表批量查询限定词。
  ///
  /// @param uis 限定词 UI 列表
  /// @return 限定词实体列表
  List<MeshQualifierEntity> findAllByUiIn(Collection<String> uis);
}

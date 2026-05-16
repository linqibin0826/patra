package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.persistence.entity.MeshDescriptorEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/// MeSH 主题词 JPA Repository。
///
/// **职责**：管理 MeshDescriptorEntity 的 CRUD 操作。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshDescriptorDao extends JpaRepository<MeshDescriptorEntity, Long> {

  /// 判断是否存在任何数据。
  ///
  /// @return true 如果存在数据
  @Query("SELECT COUNT(d) > 0 FROM MeshDescriptorEntity d")
  boolean hasAnyData();

  /// 按 UI 查询主题词。
  ///
  /// @param ui MeSH UI
  /// @return 主题词实体（如果存在）
  Optional<MeshDescriptorEntity> findByUi(String ui);

  /// 按 UI 列表批量查询主题词。
  ///
  /// @param uis UI 列表
  /// @return 主题词实体列表
  List<MeshDescriptorEntity> findAllByUiIn(Collection<String> uis);

  /// 按名称列表批量查询主题词。
  ///
  /// @param names 主题词名称列表
  /// @return 主题词实体列表
  List<MeshDescriptorEntity> findAllByNameIn(Collection<String> names);
}

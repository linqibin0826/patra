package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.infra.persistence.entity.MeshScrHeadingMappedToEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// SCR 映射关系 JPA Repository。
///
/// **职责**：管理 MeshScrHeadingMappedToEntity 的 CRUD 操作。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshScrHeadingMappedToDao
    extends JpaRepository<MeshScrHeadingMappedToEntity, Long> {

  /// 按 SCR UI 查询所有映射关系。
  ///
  /// @param scrUi SCR UI
  /// @return 映射关系实体列表
  List<MeshScrHeadingMappedToEntity> findAllByScrUi(String scrUi);

  /// 按 SCR UI 列表批量查询映射关系。
  ///
  /// @param scrUis SCR UI 列表
  /// @return 映射关系实体列表
  List<MeshScrHeadingMappedToEntity> findAllByScrUiIn(Collection<String> scrUis);

  /// 按 Descriptor UI 查询所有映射关系（反向查询）。
  ///
  /// @param descriptorUi Descriptor UI
  /// @return 映射关系实体列表
  List<MeshScrHeadingMappedToEntity> findAllByDescriptorUi(String descriptorUi);

  /// 按 SCR UI 删除所有映射关系。
  ///
  /// @param scrUi SCR UI
  void deleteAllByScrUi(String scrUi);

  /// 按 SCR UI 列表批量删除映射关系。
  ///
  /// @param scrUis SCR UI 列表
  void deleteAllByScrUiIn(Collection<String> scrUis);
}

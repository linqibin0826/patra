package com.patra.catalog.infra.persistence.jpa;

import com.patra.catalog.infra.persistence.jpa.entity.MeshEntryTermEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// MeSH 入口术语 JPA Repository。
///
/// **职责**：管理 MeshEntryTermEntity 的 CRUD 操作。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshEntryTermJpaRepository extends JpaRepository<MeshEntryTermEntity, Long> {

  /// 按主题词 UI 查询所有入口术语。
  ///
  /// @param descriptorUi 主题词 UI
  /// @return 入口术语实体列表
  List<MeshEntryTermEntity> findAllByDescriptorUi(String descriptorUi);

  /// 按主题词 UI 列表批量查询入口术语。
  ///
  /// @param descriptorUis 主题词 UI 列表
  /// @return 入口术语实体列表
  List<MeshEntryTermEntity> findAllByDescriptorUiIn(Collection<String> descriptorUis);

  /// 按概念 UI 查询所有入口术语。
  ///
  /// @param conceptUi 概念 UI
  /// @return 入口术语实体列表
  List<MeshEntryTermEntity> findAllByConceptUi(String conceptUi);

  /// 按主题词 UI 删除所有入口术语。
  ///
  /// @param descriptorUi 主题词 UI
  void deleteAllByDescriptorUi(String descriptorUi);

  /// 按主题词 UI 列表批量删除入口术语。
  ///
  /// @param descriptorUis 主题词 UI 列表
  void deleteAllByDescriptorUiIn(Collection<String> descriptorUis);
}

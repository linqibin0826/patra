package com.patra.catalog.infra.persistence.jpa;

import com.patra.catalog.infra.persistence.jpa.entity.MeshConceptEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// MeSH 概念 JPA Repository。
///
/// **职责**：管理 MeshConceptEntity 的 CRUD 操作。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshConceptJpaRepository extends JpaRepository<MeshConceptEntity, Long> {

  /// 按主题词 UI 查询所有概念。
  ///
  /// @param descriptorUi 主题词 UI
  /// @return 概念实体列表
  List<MeshConceptEntity> findAllByDescriptorUi(String descriptorUi);

  /// 按主题词 UI 列表批量查询概念。
  ///
  /// @param descriptorUis 主题词 UI 列表
  /// @return 概念实体列表
  List<MeshConceptEntity> findAllByDescriptorUiIn(Collection<String> descriptorUis);

  /// 按主题词 UI 删除所有概念。
  ///
  /// @param descriptorUi 主题词 UI
  void deleteAllByDescriptorUi(String descriptorUi);

  /// 按主题词 UI 列表批量删除概念。
  ///
  /// @param descriptorUis 主题词 UI 列表
  void deleteAllByDescriptorUiIn(Collection<String> descriptorUis);
}

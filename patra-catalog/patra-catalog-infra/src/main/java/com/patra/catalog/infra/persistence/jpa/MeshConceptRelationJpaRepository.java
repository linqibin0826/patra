package com.patra.catalog.infra.persistence.jpa;

import com.patra.catalog.infra.persistence.jpa.entity.MeshConceptRelationEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// MeSH 概念关系 JPA Repository。
///
/// **职责**：管理 MeshConceptRelationEntity 的 CRUD 操作。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshConceptRelationJpaRepository
    extends JpaRepository<MeshConceptRelationEntity, Long> {

  /// 按概念 UI 查询所有关系。
  ///
  /// @param conceptUi 概念 UI
  /// @return 概念关系实体列表
  List<MeshConceptRelationEntity> findAllByConceptUi(String conceptUi);

  /// 按主题词 UI 查询所有概念关系。
  ///
  /// @param descriptorUi 主题词 UI
  /// @return 概念关系实体列表
  List<MeshConceptRelationEntity> findAllByDescriptorUi(String descriptorUi);

  /// 按主题词 UI 列表批量查询概念关系。
  ///
  /// @param descriptorUis 主题词 UI 列表
  /// @return 概念关系实体列表
  List<MeshConceptRelationEntity> findAllByDescriptorUiIn(Collection<String> descriptorUis);

  /// 按主题词 UI 删除所有概念关系。
  ///
  /// @param descriptorUi 主题词 UI
  void deleteAllByDescriptorUi(String descriptorUi);

  /// 按主题词 UI 列表批量删除概念关系。
  ///
  /// @param descriptorUis 主题词 UI 列表
  void deleteAllByDescriptorUiIn(Collection<String> descriptorUis);
}

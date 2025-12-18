package com.patra.catalog.infra.adapter.persistence.dao;

import com.patra.catalog.infra.adapter.persistence.entity.MeshTreeNumberEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// MeSH 树形编号 JPA Repository。
///
/// **职责**：管理 MeshTreeNumberEntity 的 CRUD 操作。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshTreeNumberDao extends JpaRepository<MeshTreeNumberEntity, Long> {

  /// 按主题词 UI 查询所有树形编号。
  ///
  /// @param descriptorUi 主题词 UI
  /// @return 树形编号实体列表
  List<MeshTreeNumberEntity> findAllByDescriptorUi(String descriptorUi);

  /// 按主题词 UI 列表批量查询树形编号。
  ///
  /// @param descriptorUis 主题词 UI 列表
  /// @return 树形编号实体列表
  List<MeshTreeNumberEntity> findAllByDescriptorUiIn(Collection<String> descriptorUis);

  /// 按主题词 UI 删除所有树形编号。
  ///
  /// @param descriptorUi 主题词 UI
  void deleteAllByDescriptorUi(String descriptorUi);

  /// 按主题词 UI 列表批量删除树形编号。
  ///
  /// @param descriptorUis 主题词 UI 列表
  void deleteAllByDescriptorUiIn(Collection<String> descriptorUis);
}

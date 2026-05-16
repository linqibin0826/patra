package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.persistence.entity.MeshEntryCombinationEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// MeSH 组合条目 JPA Repository。
///
/// **职责**：管理 MeshEntryCombinationEntity 的 CRUD 操作。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshEntryCombinationDao extends JpaRepository<MeshEntryCombinationEntity, Long> {

  /// 按主题词 UI 查询所有组合条目。
  ///
  /// @param descriptorUi 主题词 UI
  /// @return 组合条目实体列表
  List<MeshEntryCombinationEntity> findAllByDescriptorUi(String descriptorUi);

  /// 按主题词 UI 列表批量查询组合条目。
  ///
  /// @param descriptorUis 主题词 UI 列表
  /// @return 组合条目实体列表
  List<MeshEntryCombinationEntity> findAllByDescriptorUiIn(Collection<String> descriptorUis);

  /// 按主题词 UI 删除所有组合条目。
  ///
  /// @param descriptorUi 主题词 UI
  void deleteAllByDescriptorUi(String descriptorUi);

  /// 按主题词 UI 列表批量删除组合条目。
  ///
  /// @param descriptorUis 主题词 UI 列表
  void deleteAllByDescriptorUiIn(Collection<String> descriptorUis);
}
